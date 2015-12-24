/*******************************************************************************
 * Copyright 2012 Paweł Psztyć
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.rest.client.activity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rest.client.ClientFactory;
import org.rest.client.ExternalEventsFactory;
import org.rest.client.RestClient;
import org.rest.client.StatusNotification;
import org.rest.client.analytics.GoogleAnalytics;
import org.rest.client.analytics.GoogleAnalyticsApp;
import org.rest.client.event.AddEncodingEvent;
import org.rest.client.event.ClearFormEvent;
import org.rest.client.event.HttpEncodingChangeEvent;
import org.rest.client.event.HttpMethodChangeEvent;
import org.rest.client.event.OverwriteUrlEvent;
import org.rest.client.event.ProjectChangeEvent;
import org.rest.client.event.ProjectChangeRequestEvent;
import org.rest.client.event.ProjectDeleteEvent;
import org.rest.client.event.ProjectDeleteRequestEvent;
import org.rest.client.event.RequestChangeEvent;
import org.rest.client.event.RequestEndEvent;
import org.rest.client.event.RequestStartActionEvent;
import org.rest.client.event.URLFieldToggleEvent;
import org.rest.client.event.UrlValueChangeEvent;
import org.rest.client.gdrive.DriveApi;
import org.rest.client.gdrive.DriveAuth;
import org.rest.client.gdrive.DriveFileItem;
import org.rest.client.jso.ExternalDriveCreateData;
import org.rest.client.jso.ExternalDriveCreateResponse;
import org.rest.client.jso.ResponseStatusData;
import org.rest.client.place.RequestPlace;
import org.rest.client.request.RedirectData;
import org.rest.client.request.RequestHeadersParser;
import org.rest.client.request.URLParser;
import org.rest.client.storage.StoreResultCallback;
import org.rest.client.storage.store.ProjectStoreWebSql;
import org.rest.client.storage.store.RequestDataStoreWebSql;
import org.rest.client.storage.store.StoreKeys;
import org.rest.client.storage.store.objects.FormEncodingObject;
import org.rest.client.storage.store.objects.HistoryObject;
import org.rest.client.storage.store.objects.ProjectObject;
import org.rest.client.storage.store.objects.RequestObject;
import org.rest.client.tutorial.TutorialFactory;
import org.rest.client.ui.AddEncodingView;
import org.rest.client.ui.EditProjectView;
import org.rest.client.ui.RequestView;
import org.rest.client.ui.ResponseView;

import com.allen_sauer.gwt.log.client.Log;
import com.google.code.gwt.database.client.service.DataServiceException;
import com.google.code.gwt.database.client.service.ListCallback;
import com.google.code.gwt.database.client.service.VoidCallback;
import com.google.gwt.chrome.def.BackgroundJsCallback;
import com.google.gwt.chrome.storage.Storage;
import com.google.gwt.chrome.storage.StorageArea.StorageSimpleCallback;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptException;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.xhr2.client.Header;
import com.google.gwt.xhr2.client.RequestHeader;
import com.google.gwt.xhr2.client.Response;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

/**
 * Activities typically restore state ("wake up"), perform initialization (
 * "set up"), and load a corresponding UI ("show up")
 * 
 * @author Paweł Psztyć
 * 
 */
public class RequestActivity extends AppActivity implements RequestView.Presenter, ResponseView.ResponsePresenter {

	final private RequestPlace place;
	private EventBus eventBus;
	protected ResponseView responseView;
	protected RequestView requestView;
	FlowPanel viewFlowPanel;
	TutorialFactory tutorialFactory = null;
	private String currentRequestEtag = null;

	private final static String ANALYTICS_EVENT_CATEGORY = "Request view";

	public RequestActivity(RequestPlace place, ClientFactory clientFactory) {
		super(clientFactory);
		this.place = place;
	}

	@Override
	public void start(AcceptsOneWidget panel, com.google.gwt.event.shared.EventBus eventBus) {

		this.eventBus = eventBus;
		super.start(panel, eventBus);
		if (RestClient.currentlyOpenedProject > 0) {
			RestClient.previouslyOpenedProject = RestClient.currentlyOpenedProject;
			RestClient.currentlyOpenedProject = -1;
		}

		RestClient.RESTORED_REQUEST = null;
		RestClient.CURRENT_GOOGLE_DRIVE_ITEM = null;
		currentRequestEtag = null;

		requestView = this.clientFactory.getRequestView();
		requestView.reset();

		requestView.setPresenter(this);
		viewFlowPanel = new FlowPanel();
		viewFlowPanel.add(requestView);
		panel.setWidget(viewFlowPanel);

		String entryId = place.getEntryId();

		if (place.isHistory()) {
			try {
				int historyId = Integer.parseInt(entryId);
				restoreRequestFromHistory(historyId);
			} catch (Exception e) {
				if (RestClient.isDebug()) {
					Log.error("Unable read history ID", e);
				}
				StatusNotification.notify("Unable read history ID", StatusNotification.TIME_SHORT);
				restoreLatestRequest();
			}
		} else if (place.isProject()) {
			try {
				int projectId = Integer.parseInt(entryId);
				RestClient.currentlyOpenedProject = projectId;
				restoreRequestFromProject(projectId, -1);
			} catch (Exception e) {
				if (RestClient.isDebug()) {
					Log.error("Unable read project ID", e);
				}
				StatusNotification.notify("Unable read project ID", StatusNotification.TIME_SHORT);
				restoreLatestRequest();
			}

		} else if (place.isProjectsEndpoint()) {
			try {
				int endpointId = Integer.parseInt(entryId);
				restoreRequestFromProject(-1, endpointId);
			} catch (Exception e) {
				if (RestClient.isDebug()) {
					Log.error("Unable read project's endpoint ID", e);
				}
				StatusNotification.notify("Unable read project data", StatusNotification.TIME_SHORT);
				restoreLatestRequest();
			}
		} else if (place.isSaved()) {
			try {
				int savedId = Integer.parseInt(entryId);
				restoreFormSavedRequest(savedId);
			} catch (Exception e) {
				if (RestClient.isDebug()) {
					Log.error("Unable read saved item ID", e);
				}
				StatusNotification.notify("Unable read saved request data", StatusNotification.TIME_SHORT);
				restoreLatestRequest();
			}
		} else if (place.isExternal()) {
			createExternalRequest(entryId);
		} else if (place.isGdrive()) {
			if (place.isCreate()) {
				fromGoogleDriveAction(entryId);
			} else {
				fromGoogleDriveFile(entryId);
			}
		} else {
			restoreLatestRequest();
		}
		observeEvents();
		activateTutorial();
	}

	private void fromGoogleDriveAction(final String entryId) {
		requestView.reset();

		clientFactory.getChromeMessagePassing().postMessage(ExternalEventsFactory.EXT_GET_EXTERNAL_REQUEST_DATA,
				entryId, new BackgroundJsCallback() {

					@Override
					public void onSuccess(Object message) {
						ExternalDriveCreateResponse response;
						try {
							response = ((JavaScriptObject) message).cast();
						} catch (Exception e) {
							StatusNotification.notify("Unable to read response from background page",
									StatusNotification.TIME_MEDIUM);
							return;
						}
						if (response.isError()) {
							StatusNotification.notify(response.getErrorMessage(), StatusNotification.TIME_MEDIUM);
							return;
						}
						ExternalDriveCreateData data = response.getData();
						if (data == null) {
							StatusNotification.notify("No data passed to application.", StatusNotification.TIME_MEDIUM);
							return;
						}
						RestClient.GOOGLE_DRIVE_CREATE_FOLDER_ID = data.getFolderId();
						RestClient.GOOGLE_DRIVE_CREATE_USER_ID = data.getUserId();

						tutorialFactory = new TutorialFactory("gdriveCreate", true);
						requestView.setUpDriveTutorial(tutorialFactory);

					}

					@Override
					public void onError(String message) {

						Log.error("Error get gdrive data", message);
					}
				});

	}

	private void fromGoogleDriveFile(final String entryId) {

		final DialogBox loader = new DialogBox(false);
		loader.setAnimationEnabled(false);
		loader.setAutoHideEnabled(false);
		loader.setAutoHideOnHistoryEventsEnabled(true);
		loader.setGlassEnabled(true);

		HTML html = new HTML("<div class=\"dialogTitle\">Loading file from Google Drive ™</div>");
		loader.add(html);

		loader.show();
		loader.center();

		DriveApi.hasSession(new DriveApi.SessionHandler() {
			@Override
			public void onResult(DriveAuth result) {
				if (result == null) {
					// not logged in user
					DriveApi.auth(new DriveApi.SessionHandler() {
						@Override
						public void onResult(DriveAuth result) {
							if (result == null) {
								loader.hide();
								return;
							}
							getFileMetadataFromDrive(entryId, loader);
						}
					}, false);
					return;
				}
				getFileMetadataFromDrive(entryId, loader);
			}
		});
	}

	private void getFileMetadataFromDrive(final String fileId, final DialogBox loader) {

		DriveApi.getFileMetadata(fileId, new DriveApi.FileMetadataHandler() {

			@Override
			public void onLoad(DriveFileItem response) {
				if (response == null) {
					loader.hide();
					StatusNotification.notify("Unable read from gdrive. ", StatusNotification.TIME_SHORT);
					return;
				}

				downloadFileFromDrive(fileId, response, loader);
			}

			@Override
			public void onError(JavaScriptException exc) {
				loader.hide();
				if (RestClient.isDebug()) {
					Log.error("Unable read from gdrive.", exc);
				}
				StatusNotification.notify("Unable read from gdrive. " + exc.getMessage(),
						StatusNotification.TIME_SHORT);
			}
		});
	}

	private void downloadFileFromDrive(final String fileId, final DriveFileItem response, final DialogBox loader) {

		final String fileUrl = response.getDownloadUrl();
		final String fileTitle = response.getTitle();
		final String fileEtag = response.getEtag();

		if (currentRequestEtag != null && fileEtag != null && fileEtag.equals(currentRequestEtag)) {
			// nothing has changed.
			loader.hide();
			requestView.setGDriveConstrols();
			return;
		}

		currentRequestEtag = fileEtag;

		DriveApi.downloadFile(fileUrl, new DriveApi.FileDownloadHandler() {
			@Override
			public void onError(JavaScriptException exc) {
				loader.hide();
				if (RestClient.isDebug()) {
					Log.error("Unable download from gdrive.", exc);
				}
				StatusNotification.notify("Unable download from gdrive. " + exc.getMessage(),
						StatusNotification.TIME_SHORT);
			}

			@Override
			public void onDownload(String content) {
				if (content == null) {
					loader.hide();
					// request error
					StatusNotification.notify("Unable download from gdrive. ", StatusNotification.TIME_SHORT);
					return;
				}
				RequestObject values = null;
				try {
					values = RequestObject.fromString(content);
				} catch (Exception e) {
					loader.hide();
					if (RestClient.isDebug()) {
						Log.error("Invalid ARC file.", e);
					}
					StatusNotification.notify("Invalid ARC file.", StatusNotification.TIME_SHORT);
					return;
				}

				if (values == null) {
					StatusNotification.notify("Invalid ARC file.", StatusNotification.TIME_SHORT);
				} else {
					RestClient.CURRENT_GOOGLE_DRIVE_ITEM = fileId;
				}

				values.setGDriveId(fileId);
				values.setName(fileTitle);

				setViewParameters(values);
				loader.hide();
			}
		});
	}

	/**
	 * 
	 * @param projectId
	 *            -1 if project ID is unknown
	 * @param endpointId
	 *            -1 for default endpoint (first one)
	 * @param requestView
	 */
	private void restoreRequestFromProject(int projectId, int endpointId) {
		if (projectId == -1 && endpointId == -1) {
			if (RestClient.isDebug()) {
				Log.error("Project ID and endpoint ID can't be -1 at once.");
			}
			StatusNotification.notify("Unable read project data");
			restoreLatestRequest();
			return;
		}
		final ProjectStoreWebSql projectsStore = clientFactory.getProjectsStore();
		if (endpointId == -1) {
			projectsStore.getByKey(projectId, new StoreResultCallback<ProjectObject>() {

				@Override
				public void onSuccess(ProjectObject result) {
					restoreDefaultRequestFromProject(result, requestView);
				}

				@Override
				public void onError(Throwable e) {
					if (RestClient.isDebug()) {
						Log.error("Unable read project data.", e);
					}
					StatusNotification.notify("Unable read project data");
				}
			});
		} else {
			clientFactory.getRequestDataStore().getByKey(endpointId, new StoreResultCallback<RequestObject>() {
				@Override
				public void onSuccess(final RequestObject result) {
					if (result.getProject() > 0) {
						RestClient.currentlyOpenedProject = result.getProject();
						projectsStore.getByKey(result.getProject(), new StoreResultCallback<ProjectObject>() {

							@Override
							public void onSuccess(ProjectObject project) {
								restoreProjectEndpoint(project, result);
							}

							@Override
							public void onError(Throwable e) {
								if (RestClient.isDebug()) {
									Log.error("Unable read project data.", e);
								}
								StatusNotification.notify("Unable read project data");
							}
						});
					} else {
						if (RestClient.isDebug()) {
							Log.error("Project does not contain selected endpoint.");
						}
						StatusNotification.notify("Project does not contain selected endpoint.");
					}
				}

				@Override
				public void onError(Throwable e) {
					if (RestClient.isDebug()) {
						Log.error("Unable read project data.", e);
					}
					StatusNotification.notify("Unable read project's endpoint data");
				}
			});
		}
	}

	private void restoreDefaultRequestFromProject(final ProjectObject project, final RequestView requestView) {
		if (project == null || project.getId() <= 0) {
			if (RestClient.isDebug()) {
				Log.error("No such project.");
			}
			StatusNotification.notify("No such project.");
			return;
		}
		clientFactory.getRequestDataStore().getService().getProjectDefaultRequests(project.getId(),
				new ListCallback<RequestObject>() {

					@Override
					public void onFailure(DataServiceException error) {
						if (RestClient.isDebug()) {
							Log.error("Can't find default endpoint for this project. Database error.", error);
						}
						StatusNotification.notify("Can't find default endpoint for this project.");
					}

					@Override
					public void onSuccess(List<RequestObject> result) {
						if (result == null || result.size() == 0) {
							if (RestClient.isDebug()) {
								Log.error("Can't find default endpoint for this project.");
							}
							StatusNotification.notify("Can't find default endpoint for this project.");
							return;
						}
						restoreProjectEndpoint(project, result.get(0));
					}
				});
	}

	private void restoreProjectEndpoint(final ProjectObject project, final RequestObject request) {
		showProjectRelatedData(project.getId(), project);

		// if can overwrite current params first restore latest request
		// and then set parameters.
		if (RestClient.currentlyOpenedProject == RestClient.previouslyOpenedProject) {

			if (RestClient.isDebug()) {
				Log.debug("Restoring data for the same project as previous.");
			}

			RequestObject.restoreLatest(new Callback<RequestObject, Throwable>() {
				@Override
				public void onSuccess(RequestObject result) {
					restoreProjectEndpointWithLatestData(result, project, request, requestView);
				}

				@Override
				public void onFailure(Throwable reason) {
					StatusNotification.notify("Unable to complete :(");
					Log.error("Unable to restore project data :(", reason);
				}
			});

			return;
		}
		// treat it as a default projects request

		requestView.setHeaders(request.getHeaders());
		requestView.setMethod(request.getMethod());
		requestView.setPayload(request.getPayload());
		requestView.setEncoding(request.getEncoding());
		requestView.setUrl(request.getURL());

		setUserDefinedContentEncodingValues(request.getEncoding(), request.getHeaders());
		RestClient.fixChromeLayout();
		RestClient.RESTORED_REQUEST = request.getId();
	}

	private void restoreProjectEndpointWithLatestData(RequestObject lesteSavedRequest, final ProjectObject project,
			final RequestObject request, final RequestView requestView) {

		if (request.isSkipHeaders()) {
			requestView.setHeaders(lesteSavedRequest.getHeaders());
		} else {
			requestView.setHeaders(request.getHeaders());
		}
		if (request.isSkipMethod()) {
			requestView.setMethod(lesteSavedRequest.getMethod());
		} else {
			requestView.setMethod(request.getMethod());
		}
		if (request.isSkipPayload()) {
			requestView.setPayload(lesteSavedRequest.getPayload());
		} else {
			requestView.setPayload(request.getPayload());
		}
		requestView.setEncoding(request.getEncoding());
		if (RestClient.isDebug()) {
			Log.debug("Restoring encoding to ." + request.getEncoding());
		}

		String oldUrl = lesteSavedRequest.getURL();

		String newUrl = request.getURL();
		URLParser urlData = new URLParser().parse(newUrl);
		URLParser oldUrlData = new URLParser().parse(oldUrl);

		if (request.isSkipHistory()) {
			// remove hash from restored and get one from latest
			urlData.setAnchor(oldUrlData.getAnchor());
		}
		if (request.isSkipParams()) {
			// remove query string from restored and get one from latest
			urlData.setQuery(oldUrlData.getQuery());
		}
		if (request.isSkipPath()) {
			// remove path from restored and get one from latest
			urlData.setPath(oldUrlData.getPath());
		}
		if (request.isSkipProtocol()) {
			// remove hash from restored and get one from latest
			urlData.setProtocol(oldUrlData.getProtocol());
		}
		if (request.isSkipServer()) {
			// remove hash from restored and get one from latest
			urlData.setAuthority(oldUrlData.getAuthority());
		}

		requestView.setUrl(urlData.toString());

		setUserDefinedContentEncodingValues(request.getEncoding(), request.getHeaders());
		RestClient.fixChromeLayout();
		RestClient.RESTORED_REQUEST = request.getId();
	}

	private void showProjectRelatedData(final int projectId, final ProjectObject project) {
		clientFactory.getRequestDataStore().getService().getProjectRequests(projectId,
				new ListCallback<RequestObject>() {

					@Override
					public void onFailure(DataServiceException error) {
						if (RestClient.isDebug()) {
							Log.error("Unable to find related projects.", error);
						}
					}

					@Override
					public void onSuccess(final List<RequestObject> request) {
						if (request.size() == 0) {
							return;
						}
						int _endpointId = -1;
						if (place.isProjectsEndpoint()) {
							try {
								_endpointId = Integer.parseInt(place.getEntryId());
							} catch (Exception e) {
							}
						}
						final int endpointId = _endpointId;

						if (project == null) {
							ProjectStoreWebSql projectsStore = clientFactory.getProjectsStore();
							projectsStore.getByKey(projectId, new StoreResultCallback<ProjectObject>() {

								@Override
								public void onSuccess(ProjectObject project) {
									requestView.setProjectData(project, request, endpointId);
								}

								@Override
								public void onError(Throwable e) {
									if (RestClient.isDebug()) {
										Log.error("Unable read project data.", e);
									}
									StatusNotification.notify("Unable read project related data");
								}
							});
						} else {
							requestView.setProjectData(project, request, endpointId);
						}
					}
				});
	}

	/**
	 * Register event handlers from system's event bus to handle relevant data.
	 * Registered events: - Request start - Request end -
	 */
	private void observeEvents() {

		// When request starts disable UI controls
		RequestStartActionEvent.register(eventBus, new RequestStartActionEvent.Handler() {
			@Override
			public void onStart(Date time) {
				requestView.handleRequestStartActionEvent(time);
				GoogleAnalytics.sendEvent("Engagement", "Click", "Request start");
				GoogleAnalyticsApp.sendEvent("Engagement", "Click", "Request start");
			}
		});

		OverwriteUrlEvent.register(eventBus, new OverwriteUrlEvent.Handler() {
			@Override
			public void onUrlChange(String url) {
				requestView.setUrl(url);
			}
		});

		RequestChangeEvent.register(eventBus, new RequestChangeEvent.Handler() {
			@Override
			public void onChange(RequestChangeEvent event) {
				requestView.handleRequestChangeEvent(event);
			}
		});

		RequestEndEvent.register(eventBus, new RequestEndEvent.Handler() {
			@Override
			public void onResponse(boolean success, final Response response, long requestTime) {

				requestView.handleRequestEndEvent();

				if (responseView != null) {
					responseView.asWidget().removeFromParent();
					responseView = null;
				}

				responseView = clientFactory.getResponseView();
				viewFlowPanel.add(responseView);
				responseView.setPresenter(RequestActivity.this);
				responseView.setResponseData(success, response, requestTime);

				/**
				 * Get request and response headers data from Chrome Extensions
				 * API
				 */
				clientFactory.getChromeMessagePassing()
						.postMessage(ExternalEventsFactory.EXT_GET_COLLECTED_REQUEST_DATA, new BackgroundJsCallback() {
					@Override
					public void onSuccess(Object result) {
						if (result == null) {
							responseView.setBackgroundResponseData(null);
						} else {
							responseView.setBackgroundResponseData((ResponseStatusData) result);
						}
					}

					@Override
					public void onError(String message) {
						if (RestClient.isDebug()) {
							Log.error("Unknown error occured: " + message);
						}
					}
				});
			}
		});

		ProjectChangeRequestEvent.register(eventBus, new ProjectChangeRequestEvent.Handler() {
			@Override
			public void onProjectChange(final ProjectObject project) {
				if (project == null) {
					return;
				}
				ProjectStoreWebSql store = clientFactory.getProjectsStore();
				store.put(project, project.getId(), new StoreResultCallback<Integer>() {

					@Override
					public void onSuccess(Integer result) {
						ProjectChangeEvent ev = new ProjectChangeEvent(project);
						eventBus.fireEvent(ev);

						if (RestClient.currentlyOpenedProject == project.getId()) {
							requestView.updateProjectMetadata(project);
						}
					}

					@Override
					public void onError(Throwable e) {
						if (RestClient.isDebug()) {
							Log.error("Unable to update project data", e);
						}
						StatusNotification.notify("Unable to update project data", StatusNotification.TIME_SHORT);
					}
				});
			}
		});
		ProjectDeleteRequestEvent.register(eventBus, new ProjectDeleteRequestEvent.Handler() {

			@Override
			public void onProjectDelete(final int projectId) {
				ProjectStoreWebSql projectStore = clientFactory.getProjectsStore();
				projectStore.remove(projectId, new StoreResultCallback<Boolean>() {

					@Override
					public void onSuccess(Boolean result) {
						if (!result.booleanValue()) {
							if (RestClient.isDebug()) {
								Log.error("Unable to delete project data");
							}
							StatusNotification.notify("Unable to delete project data", StatusNotification.TIME_SHORT);
							return;
						}

						RequestDataStoreWebSql requestsStore = clientFactory.getRequestDataStore();
						requestsStore.getService().deleteFromProject(projectId, new VoidCallback() {

							@Override
							public void onFailure(DataServiceException error) {
								if (RestClient.isDebug()) {
									Log.error("Unable to delete project related  data", error);
								}
								ProjectDeleteEvent ev = new ProjectDeleteEvent(projectId);
								eventBus.fireEvent(ev);
								goTo(new RequestPlace(null));
							}

							@Override
							public void onSuccess() {
								ProjectDeleteEvent ev = new ProjectDeleteEvent(projectId);
								eventBus.fireEvent(ev);
								goTo(new RequestPlace(null));
							}
						});

					}

					@Override
					public void onError(Throwable e) {
						if (RestClient.isDebug()) {
							Log.error("Unable to delete project data", e);
						}
						StatusNotification.notify("Unable to delete project data", StatusNotification.TIME_SHORT);
					}
				});
			}
		});
	}

	private ArrayList<RedirectData> getRedirectData(JSONArray response) {
		ArrayList<RedirectData> result = new ArrayList<RedirectData>();
		if (response == null) {
			return result;
		}
		int size = response.size();
		for (int i = 0; i < size; i++) {
			JSONValue itemValue = response.get(i);
			JSONObject item = itemValue.isObject();
			if (item == null)
				continue;

			boolean fromCache = item.get("fromCache").isBoolean().booleanValue();
			String redirectUrl = item.get("redirectUrl").isString().stringValue();

			String statusLine = null;
			JSONValue statusLineValue = item.get("statusLine");
			if (statusLineValue != null) {
				statusLine = item.get("statusLine").isString().stringValue();
			}
			int statusCode = Integer.parseInt(item.get("statusCode").isNumber().toString());
			ArrayList<Header> headers = extractHeadersExternal(item, "responseHeaders");
			RedirectData redirect = new RedirectData();
			redirect.setRedirectUrl(redirectUrl);
			redirect.setFromCache(fromCache);
			redirect.setResponseHeaders(headers);
			redirect.setStatusCode(statusCode);
			if (statusLine != null)
				redirect.setStatusLine(statusLine);
			result.add(redirect);
		}
		return result;
	}

	private ArrayList<Header> extractHeadersExternal(JSONObject response, String key) {
		ArrayList<Header> headers = new ArrayList<Header>();
		JSONValue valuesValue = response.get(key);
		if (valuesValue == null) {
			return headers;
		}
		JSONArray arr = valuesValue.isArray();
		if (arr == null) {
			return headers;
		}
		int len = arr.size();
		for (int i = 0; i < len; i++) {
			JSONValue item = arr.get(i);
			final String name = item.isObject().get("name").isString().stringValue();
			final String value = item.isObject().get("value").isString().stringValue();
			Header header = new Header() {
				@Override
				public String getName() {
					return name;
				}

				@Override
				public String getValue() {
					return value;
				}

				@Override
				public String toString() {
					return name + " : " + value;
				}
			};
			headers.add(header);
		}
		return headers;
	}

	/**
	 * Restore request from history DB
	 * 
	 * @param historyId
	 * @param requestView
	 */
	private void restoreRequestFromHistory(int historyId) {
		RestClient.getClientFactory().getHistoryRequestStore().getByKey(historyId,
				new StoreResultCallback<HistoryObject>() {

					@Override
					public void onSuccess(HistoryObject result) {
						requestView.setUrl(result.getURL());
						requestView.setMethod(result.getMethod());
						requestView.setHeaders(result.getHeaders());
						requestView.setPayload(result.getPayload());
						setUserDefinedContentEncodingValues(result.getEncoding(), result.getHeaders());

						Date date = new Date((long) result.getTime());
						String lastUseDate = DateTimeFormat.getFormat(PredefinedFormat.DATE_TIME_FULL).format(date);
						requestView.setRequestName("Last used: " + lastUseDate);
						RestClient.fixChromeLayout();
					}

					@Override
					public void onError(Throwable e) {
						if (RestClient.isDebug()) {
							Log.error("Unable read history data", e);
						}
						StatusNotification.notify("Unable read history data");
					}
				});
	}

	private void createExternalRequest(final String requestUUID) {
		clientFactory.getChromeMessagePassing().postMessage(ExternalEventsFactory.EXT_GET_EXTERNAL_REQUEST_DATA,
				requestUUID, new BackgroundJsCallback() {
					@Override
					public void onSuccess(Object result) {
						if (result == null) {
							StatusNotification.notify("Data from external extension is no longer available :(",
									StatusNotification.TIME_MEDIUM);
							return;
						}
						JSONValue parsedValue = (JSONValue) result;
						JSONObject obj = parsedValue.isObject();
						if (obj.containsKey("error")) {
							if (obj.get("error").isBoolean().booleanValue()) {
								Log.error("Error get External Data. Message: "
										+ obj.get("message").isString().stringValue());
								StatusNotification.notify(obj.get("message").isString().stringValue(),
										StatusNotification.TIME_MEDIUM);
								return;
							}
						}
						if (obj.containsKey("data")) {
							JSONObject dataObj = obj.get("data").isObject();
							if (dataObj.containsKey("url")) {
								requestView.setUrl(dataObj.get("url").isString().stringValue());
							}
							if (dataObj.containsKey("method")) {
								requestView.setMethod(dataObj.get("method").isString().stringValue());
							}
							if (dataObj.containsKey("headers")) {
								requestView.setHeaders(dataObj.get("headers").isString().stringValue());
							}
							if (dataObj.containsKey("payload")) {
								requestView.setPayload(dataObj.get("payload").isString().stringValue());
							}
							if (dataObj.containsKey("encoding")) {
								requestView.setEncoding(dataObj.get("encoding").isString().stringValue());
							}
						}
						RestClient.fixChromeLayout();
					}

					@Override
					public void onError(String message) {
						if (RestClient.isDebug()) {
							Log.error("Unknown error occured: " + message);
						}
					}
				});

	}

	private void restoreFormSavedRequest(final int savedId) {
		clientFactory.getRequestDataStore().getByKey(savedId, new StoreResultCallback<RequestObject>() {

			@Override
			public void onSuccess(RequestObject result) {
				RestClient.RESTORED_REQUEST = savedId;
				setViewParameters(result);
			}

			@Override
			public void onError(Throwable e) {
				Log.error("Unable read stored data :(", e);
				StatusNotification.notify("Unable read stored data :(", StatusNotification.TIME_MEDIUM);
			}
		});
	}

	/**
	 * Restore latest, not saved request
	 * 
	 * @param view
	 */
	private void restoreLatestRequest() {
		RequestObject.restoreLatest(new Callback<RequestObject, Throwable>() {
			@Override
			public void onSuccess(RequestObject result) {
				setViewParameters(result);
			}

			@Override
			public void onFailure(Throwable caught) {
			}
		});
	}

	/**
	 * 
	 * @param selectCurrentEncoding
	 * @param headers
	 */
	private void setUserDefinedContentEncodingValues(String _selectCurrentEncoding, String headers) {

		if (headers != null) {
			ArrayList<RequestHeader> requerstHeadersList = RequestHeadersParser.stringToHeaders(headers);
			for (RequestHeader h : requerstHeadersList) {
				if (h.getName().toLowerCase().equals("content-type")) {
					_selectCurrentEncoding = h.getValue();
				}
			}
		}

		final String selectCurrentEncoding = _selectCurrentEncoding;
		clientFactory.getFormEncodingStore().all(new StoreResultCallback<Map<Integer, FormEncodingObject>>() {

			@Override
			public void onSuccess(Map<Integer, FormEncodingObject> result) {
				final RequestView view = clientFactory.getRequestView();
				String encodingToSelect = selectCurrentEncoding;

				if (selectCurrentEncoding == null) {
					encodingToSelect = view.getEncoding();
				}

				String[] values = new String[result.size()];
				Set<Integer> keys = result.keySet();
				int i = 0;
				for (Integer k : keys) {
					FormEncodingObject dbvalue = result.get(k);
					if (dbvalue != null)
						values[i] = dbvalue.getEncoding();
					i++;
				}
				view.appendEncodingValues(values);
				view.setEncoding(encodingToSelect);
			}

			@Override
			public void onError(Throwable e) {
				e.printStackTrace();
				Log.error("getFormEncodingsStore.all in RequestActivity", e);
			}
		});
	}

	@Override
	public String mayStop() {
		
		revokeDownloadData();
		if (tutorialFactory != null) {
			tutorialFactory.clear();
		}

		RequestObject ro = RequestObject.createRequest();
		ro.setEncoding(requestView.getEncoding());
		ro.setHeaders(requestView.getHeaders());
		ro.setMethod(requestView.getMethod());
		ro.setPayload(requestView.getPayload());
		ro.setURL(requestView.getUrl());
		ro.setName(requestView.getRequestName());
		ro.setProject(RestClient.currentlyOpenedProject);
		
		if (RestClient.RESTORED_REQUEST != null) {
			ro.setId(RestClient.RESTORED_REQUEST);
		}

		if (RestClient.CURRENT_GOOGLE_DRIVE_ITEM != null && !RestClient.CURRENT_GOOGLE_DRIVE_ITEM.isEmpty()) {
			ro.setGDriveId(RestClient.CURRENT_GOOGLE_DRIVE_ITEM);
		}
		ro.storeLastest(new Callback<Void, Throwable>() {
			@Override
			public void onSuccess(Void result) {
			}

			@Override
			public void onFailure(Throwable reason) {
				if (RestClient.isDebug()) {
					Log.error("Unable to store latest request data.", reason);
				}
			}
		});
		return null;
	}

	private static HandlerRegistration addDialogRegistrtion = null;

	@Override
	public void requestAddEncodingDialog(final String previousEncoding) {
		AddEncodingView dialog = clientFactory.getAddEncodingView(eventBus);
		dialog.show();

		final RequestView view = this.clientFactory.getRequestView();

		final AddEncodingEvent.Handler handler = new AddEncodingEvent.Handler() {
			@Override
			public void onAddEncoding(final String encoding) {
				addDialogRegistrtion.removeHandler();
				if (encoding == null || encoding.isEmpty()) {
					view.setEncoding(previousEncoding);
				} else {
					FormEncodingObject feo = FormEncodingObject.create();
					feo.setEncoding(encoding);
					clientFactory.getFormEncodingStore().put(feo, null, new StoreResultCallback<Integer>() {
						@Override
						public void onSuccess(Integer result) {
							setUserDefinedContentEncodingValues(encoding, null);
						}

						@Override
						public void onError(Throwable e) {
							e.printStackTrace();
							if (RestClient.isDebug()) {
								Log.error(
										"RequestActivity::requestAddEncodingDialog->AddEncodingEvent.Handler->store::put",
										e);
							}
							view.setEncoding(previousEncoding);
						}
					});
				}

				addDialogRegistrtion = null;
			}
		};

		addDialogRegistrtion = AddEncodingEvent.register(eventBus, handler);
	}

	@Override
	public void fireClearAllEvent() {

		RestClient.previouslyOpenedProject = RestClient.currentlyOpenedProject;
		RestClient.currentlyOpenedProject = -1;
		
		Storage store = GWT.create(Storage.class);
		store.getLocal().remove(StoreKeys.LATEST_REQUEST_KEY, new StorageSimpleCallback() {
			
			@Override
			public void onError(String message) {}
			
			@Override
			public void onDone() {}
		});
		RestClient.RESTORED_REQUEST = null;
		RestClient.CURRENT_GOOGLE_DRIVE_ITEM = null;
		
		eventBus.fireEvent(new ClearFormEvent());

		goTo(new RequestPlace("default"));
		GoogleAnalytics.sendEvent("Engagement", "Click", "Clear request form");
	}

	/*@Override
	public void getResponseHeadersInfo(ArrayList<String> names, final Callback<List<HeaderRow>, Throwable> callback) {

		clientFactory.getHeadersStore().getResponseHeadersByName(names, new StoreResultCallback<List<HeaderRow>>() {

			@Override
			public void onSuccess(List<HeaderRow> result) {
				callback.onSuccess(result);
			}

			@Override
			public void onError(Throwable e) {
				callback.onFailure(e);
			}
		});
	}

	@Override
	public void getRequestHeadersInfo(ArrayList<String> names, final Callback<List<HeaderRow>, Throwable> callback) {
		clientFactory.getHeadersStore().getRequestHeadersByName(names, new StoreResultCallback<List<HeaderRow>>() {

			@Override
			public void onSuccess(List<HeaderRow> result) {
				callback.onSuccess(result);
			}

			@Override
			public void onError(Throwable e) {
				callback.onFailure(e);
			}
		});
	}*/

	private String exportFileObjectUrl = null;

	@Override
	public String createDownloadData(String body, String endoding) {
		if (exportFileObjectUrl != null) {
			revokeDownloadData();
		}
		exportFileObjectUrl = createDownloadDataImpl(body, endoding);
		return exportFileObjectUrl;
	}

	private final native String createDownloadDataImpl(String data, String endoding) /*-{
		var blob = new $wnd.Blob([ data ], {
			type : endoding
		});
		return $wnd.URL.createObjectURL(blob);
	}-*/;

	@Override
	public void revokeDownloadData() {
		if (exportFileObjectUrl != null) {
			revokeDownloadDataImpl(exportFileObjectUrl);
			exportFileObjectUrl = null;
		}
	}

	private final native void revokeDownloadDataImpl(String url) /*-{
		$wnd.URL.revokeObjectURL(url);
	}-*/;

	private void activateTutorial() {
		tutorialFactory = new TutorialFactory("request");

		tutorialFactory.canStartTutorial(new Callback<Boolean, Throwable>() {
			
			@Override
			public void onSuccess(Boolean result) {
				if(result){
					requestView.setUpTutorial(tutorialFactory);
				}
			}
			
			@Override
			public void onFailure(Throwable reason) {}
		});
	}

	@Override
	public void fireEncodingChangeEvent(String newEncoding) {
		eventBus.fireEvent(new HttpEncodingChangeEvent(newEncoding));
	}

	@Override
	public void fireMethodChangeEvent(String newMethod) {
		eventBus.fireEvent(new HttpMethodChangeEvent(newMethod));
	}

	@Override
	public void fireUrlChangeEvent(String newUrl) {
		eventBus.fireEvent(new UrlValueChangeEvent(newUrl));
		requestView.handleUrlValueChangeEvent(newUrl);
	}

	@Override
	public void fireUrlToggleEvent(boolean isNowSimpleView) {
		eventBus.fireEvent(new URLFieldToggleEvent(isNowSimpleView));
		GoogleAnalytics.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget toggle",
				isNowSimpleView ? "Single line" : "Details form");
		GoogleAnalyticsApp.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget toggle",
				isNowSimpleView ? "Single line" : "Details form");
	}

	@Override
	public void fireRequestStartActionEvent(Date startTime) {
		eventBus.fireEvent(new RequestStartActionEvent(startTime));
	}

	@Override
	public void deleteCurrentEndpoint() {
		String _entryId = place.getEntryId();
		final int entryId = Integer.parseInt(_entryId);
		if (place.isProjectsEndpoint()) {
			try {
				clientFactory.getRequestDataStore().remove(entryId, new StoreResultCallback<Boolean>() {

					@Override
					public void onSuccess(Boolean result) {
						if (result.booleanValue()) {
							goTo(RequestPlace.Tokenizer.fromProjectDefault(RestClient.currentlyOpenedProject));
						} else {
							if (RestClient.isDebug()) {
								Log.error("Unable delete endpoint. Unknown error.");
							}
							StatusNotification.notify("Unable delete endpoint. Unknown error.",
									StatusNotification.TIME_SHORT);
						}
					}

					@Override
					public void onError(Throwable e) {
						if (RestClient.isDebug()) {
							Log.error("Unable delete endpoint ", e);
						}
						StatusNotification.notify("Unable delete endpoint", StatusNotification.TIME_SHORT);
					}
				});

			} catch (Exception e) {
				if (RestClient.isDebug()) {
					Log.error("Unable read project's endpoint ID", e);
				}
				StatusNotification.notify("Unable read project data", StatusNotification.TIME_SHORT);
			}
		} else if (place.isProject()) {
			clientFactory.getRequestDataStore().getService().getProjectDefaultRequests(entryId,
					new ListCallback<RequestObject>() {

						@Override
						public void onFailure(DataServiceException error) {
							if (RestClient.isDebug()) {
								Log.error("Can't find selected endpoint.", error);
							}
							StatusNotification.notify("Can't find selected endpoint.");
						}

						@Override
						public void onSuccess(List<RequestObject> result) {
							if (result == null || result.size() == 0) {
								if (RestClient.isDebug()) {
									Log.error("Can't find selected endpoint. No database entries.");
								}
								StatusNotification.notify("Can't find selected endpoint. No database entries.");
								return;
							}

							clientFactory.getRequestDataStore().remove(result.get(0).getId(),
									new StoreResultCallback<Boolean>() {

								@Override
								public void onSuccess(Boolean result) {
									if (result.booleanValue()) {
										goTo(RequestPlace.Tokenizer
												.fromProjectDefault(RestClient.currentlyOpenedProject));
									} else {
										if (RestClient.isDebug()) {
											Log.error("Unable delete endpoint. Unknown error.");
										}
										StatusNotification.notify("Unable delete endpoint. Unknown error.",
												StatusNotification.TIME_SHORT);
									}
								}

								@Override
								public void onError(Throwable e) {
									if (RestClient.isDebug()) {
										Log.error("Unable delete endpoint ", e);
									}
									StatusNotification.notify("Unable delete endpoint", StatusNotification.TIME_SHORT);
								}
							});
						}
					});
		}
	}

	@Override
	public EditProjectView getEditProjectDialog() {
		return clientFactory.getEditProjectView();
	}

	private void setViewParameters(RequestObject result) {
		if (result == null) {
			requestView.setUrl(null);
			requestView.setMethod(null);
			requestView.setHeaders(null);
			requestView.setPayload(null);
			requestView.setEncoding(null);
			requestView.setRequestName(null);
			return;
		}

		if (result.getGDriveId() != null) {
			// Google Drive item
			// goTo(RequestPlace.Tokenizer.fromDriveFile(result.getGDriveId()));
			// return;
			RestClient.CURRENT_GOOGLE_DRIVE_ITEM = result.getGDriveId();
			requestView.setGDriveConstrols();
		}

		if (result.getId() > 0) {
			RestClient.RESTORED_REQUEST = result.getId();
		}

		if (result.getProject() > 0) {
			showProjectRelatedData(result.getProject(), null);
			RestClient.currentlyOpenedProject = result.getProject();
		} else {
			// if name is available, set name
			requestView.setRequestName(result.getName());
			if (result.getId() > 0) {
				clientFactory.getRequestDataStore().getByKey(result.getId(), new StoreResultCallback<RequestObject>() {

					@Override
					public void onSuccess(RequestObject result) {
						if (result == null) {
							return;
						}
						requestView.setRequestName(result.getName());
					}

					@Override
					public void onError(Throwable e) {
					}
				});
			}
		}

		requestView.setUrl(result.getURL());
		requestView.setMethod(result.getMethod());
		requestView.setHeaders(result.getHeaders());
		requestView.setPayload(result.getPayload());
		setUserDefinedContentEncodingValues(result.getEncoding(), result.getHeaders());
		RestClient.fixChromeLayout();
	}

	@Override
	public void refreshCurrentDriveItem() {
		String entryId = RestClient.CURRENT_GOOGLE_DRIVE_ITEM;
		if (entryId == null || entryId.isEmpty()) {
			if (RestClient.isDebug()) {
				Log.debug("Not a Google Drive™ item.");
			}
			return;
		}
		fromGoogleDriveFile(entryId);
	}

	@Override
	public void changeSavedName(final String name, final Callback<Boolean, Throwable> callback) {
				if (place.isGdrive() || place.isProject() || place.isProjectsEndpoint()) {
			callback.onSuccess(false);
			return;
		}
		if (RestClient.RESTORED_REQUEST != null) {
			clientFactory.getRequestDataStore().getService().updateName(name, RestClient.RESTORED_REQUEST, new VoidCallback() {
				@Override
				public void onFailure(DataServiceException error) {
					if (RestClient.isDebug()) {
						Log.error("Unable to change name :(", error);
					}
					StatusNotification.notify("Unable to change name :(", StatusNotification.TIME_SHORT);
					callback.onFailure(error);
				}

				@Override
				public void onSuccess() {
					callback.onSuccess(true);
				}
			});

			return;
		}

		RestClient.collectRequestData(new Callback<RequestObject, Throwable>() {

			@Override
			public void onSuccess(final RequestObject result) {
				result.setName(name);
				RestClient.saveRequestData(result, new Callback<RequestObject, Throwable>() {
					@Override
					public void onSuccess(RequestObject result) {
						RestClient.RESTORED_REQUEST = result.getId();
						callback.onSuccess(true);
					}

					@Override
					public void onFailure(Throwable reason) {
						StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
						callback.onFailure(reason);
					}
				});
			}

			@Override
			public void onFailure(Throwable reason) {
				if (RestClient.isDebug()) {
					Log.error("Unable to save request data. Can't collect current request data.", reason);
				}
				StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
				callback.onFailure(reason);
			}
		});

	}

	@Override
	public void urlContextMenuOpenedAction() {
		GoogleAnalytics.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget context menu", "Open menu");
		GoogleAnalyticsApp.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget toggle", "Open menu");
	}

	@Override
	public void urlContextMenuActionPerformed(String actionName) {
		GoogleAnalytics.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget context menu action", actionName);
		GoogleAnalyticsApp.sendEvent(ANALYTICS_EVENT_CATEGORY, "URL widget toggle action", actionName);
	}

	@Override
	public void performCopyAction(String body) {

		clientFactory.getChromeMessagePassing().postMessage("copyToClipboard", body, new BackgroundJsCallback() {
			@Override
			public void onSuccess(Object message) {
				// TODO: add toast confirmation message
			}

			@Override
			public void onError(String message) {
			}
		});
		GoogleAnalytics.sendEvent(ANALYTICS_EVENT_CATEGORY, "Copy to clipboard", "Action performed");
		GoogleAnalyticsApp.sendEvent(ANALYTICS_EVENT_CATEGORY, "Copy to clipboard", "Action performed");
	}

}
