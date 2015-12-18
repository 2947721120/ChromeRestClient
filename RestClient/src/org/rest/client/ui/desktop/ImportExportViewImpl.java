package org.rest.client.ui.desktop;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.rest.client.RestClient;
import org.rest.client.analytics.GoogleAnalytics;
import org.rest.client.analytics.GoogleAnalyticsApp;
import org.rest.client.deprecated.ImportListingDialog;
import org.rest.client.deprecated.ImportRequest;
import org.rest.client.deprecated.ImportSuggestionsCallback;
import org.rest.client.deprecated.LoaderDialog;
import org.rest.client.deprecated.SuggestionImportItem;
import org.rest.client.importparser.ImportParser;
import org.rest.client.importparser.ImportResult;
import org.rest.client.place.SettingsPlace;
import org.rest.client.request.ApplicationRequest;
import org.rest.client.storage.store.objects.ProjectObject;
import org.rest.client.storage.store.objects.RequestObject;
import org.rest.client.ui.ImportExportView;
import org.rest.client.ui.html5.HTML5FileUpload;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.chrome.tabs.CreateProperties;
import com.google.gwt.chrome.tabs.Tab;
import com.google.gwt.chrome.tabs.TabCallback;
import com.google.gwt.chrome.tabs.Tabs;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.ParagraphElement;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.FontWeight;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.file.client.File;
import com.google.gwt.file.client.FileError;
import com.google.gwt.file.client.FileList;
import com.google.gwt.filereader.client.ErrorHandler;
import com.google.gwt.filereader.client.FileReader;
import com.google.gwt.filereader.client.LoadHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class ImportExportViewImpl extends Composite implements ImportExportView {

	private static ImportExportViewImplUiBinder uiBinder = GWT.create(ImportExportViewImplUiBinder.class);

	interface ImportExportViewImplUiBinder extends UiBinder<Widget, ImportExportViewImpl> {
	}

	private Presenter listener;
	private ImportResult currentFileImport = null;
	@UiField
	Anchor fileDownload;
	@UiField
	DivElement downloadFileAnchor;
	@UiField
	DivElement collapsePanel;
	@UiField
	HTML5FileUpload fileImport;
	@UiField
	InlineLabel importFileLog;
	@UiField
	HTMLPanel importPreview;

	public ImportExportViewImpl() {
		initWidget(uiBinder.createAndBindUi(this));

		setCollapsablePanel(collapsePanel);
		//
		// OLD SYSTEM SETUP
		//
		statusInfo.setText("Checking connection status...");
	}

	native void setCollapsablePanel(DivElement panel) /*-{
		//		var actionPanel = panel.querySelector('.Import_Export_expandPanel.');
		var handler = function(e) {
			e.preventDefault();
			if (panel.classList.contains('expanded')) {
				panel.classList.remove('expanded');
			} else {
				panel.classList.add('expanded');
			}
		};
		panel.addEventListener('click', handler, false);
	}-*/;

	@Override
	public void setPresenter(Presenter listener) {
		this.listener = listener;
	}

	@UiHandler("fileExport")
	void onfileExportClick(ClickEvent e) {
		prepareFileExport();
	}

	@UiHandler("fileDownload")
	void onDownloadFileClick(ClickEvent e) {
		if (!fileDownload.getElement().getAttribute("disabled").isEmpty()) {
			return;
		}
		fileDownload.getElement().setAttribute("disabled", "true");
		Timer t = new Timer() {
			@Override
			public void run() {
				downloadFileAnchor.addClassName("hidden");
				fileDownload.setHref("about:blank");
				listener.revokeDownloadData();
			}
		};
		t.schedule(1500);
	}

	void prepareFileExport() {
		listener.prepareDataToFile(new StringCallback() {
			@Override
			public void onResult(final String result) {

				Scheduler.get().scheduleDeferred(new Command() {
					public void execute() {
						String fileObjectUrl = listener.createDownloadData(result);
						fileDownload.setHref(fileObjectUrl);
						String date = DateTimeFormat.getFormat(PredefinedFormat.DATE_TIME_MEDIUM).format(new Date());
						String fileName = "arc-" + date + ".json";
						fileDownload.getElement().setAttribute("download", fileName);
						fileDownload.getElement().setAttribute("data-downloadurl",
								"application/json:" + fileName + ":" + fileObjectUrl);
						fileDownload.setVisible(true);
						downloadFileAnchor.removeClassName("hidden");
					}
				});

			}
		});
	}

	@UiHandler("fileImport")
	void onFileImportChange(ChangeEvent e) {

		FileList files = fileImport.getFiles();
		if (files.size() == 0)
			return;

		if (previewGrid != null) {
			previewGrid.clear();
		}
		importPreview.setVisible(false);
		fileImport.setEnabled(false);
		importFileLog.setText("working...");

		File file = files.get(0);
		FileReader reader = FileReader.create();

		reader.addErrorHandler(new ErrorHandler() {
			@Override
			public void onError(File file, FileError error) {
				fileImport.setEnabled(true);
				importFileLog.setText("");
				String msg = "";
				switch (error.getCode()) {
				case FileError.ABORT_ERR:
					msg += " ABORT_ERR::";
					break;
				case FileError.ENCODING_ERR:
					msg += " ENCODING_ERR::";
					break;
				case FileError.NOT_FOUND_ERR:
					msg += " NOT_FOUND_ERR::";
					break;
				case FileError.NOT_READABLE_ERR:
					msg += " NOT_READABLE_ERR::";
					break;
				case FileError.SECURITY_ERR:
					msg += " SECURITY_ERR::";
					break;
				}
				msg += " Unable read file.";
				if (RestClient.isDebug()) {
					Log.error(msg + " Error code: " + error.getCode());
				}
				StatusNotification.notify(msg, StatusNotification.TIME_MEDIUM);
			}
		});
		reader.addLoadHandler(new LoadHandler() {
			@Override
			public void onLoad(File file) {
				fileImport.setEnabled(true);
				importFileLog.setText("");
				String data = file.getResult();
				ImportParser parser = new ImportParser(data);
				parser.parse(new ImportParser.ImportParserHandler() {

					@Override
					public void onParse(ImportResult result) {
						if (result == null) {
							StatusNotification.notify("Unable to parse input file.", StatusNotification.TIME_MEDIUM);
							return;
						}
						showImportTable(result);
					}
				});

			}
		});
		reader.readAsText(file);
	}

	private Grid previewGrid = null;

	private void showImportTable(ImportResult result) {
		ArrayList<ProjectObject> projects = result.getProjects();
		ArrayList<RequestObject> requests = result.getRequests();

		if (requests == null || requests.size() == 0) {
			StatusNotification.notify("There is nothing to update", StatusNotification.TIME_MEDIUM);
			return;
		}

		int len = requests.size();

		if (previewGrid != null) {
			previewGrid.clear();
		} else {
			previewGrid = new Grid();
		}
		previewGrid.resize(len + 1, 4);
		previewGrid.setCellPadding(7);
		previewGrid.setCellSpacing(3);
		previewGrid.setWidth("100%");

		Label nameLabel = new Label("Name");
		Label methodLabel = new Label("Method");
		Label urlLabel = new Label("URL");
		Label projectsLabel = new Label("Project");
		nameLabel.getElement().getStyle().setFontWeight(FontWeight.BOLD);
		methodLabel.getElement().getStyle().setFontWeight(FontWeight.BOLD);
		urlLabel.getElement().getStyle().setFontWeight(FontWeight.BOLD);
		projectsLabel.getElement().getStyle().setFontWeight(FontWeight.BOLD);
		previewGrid.setWidget(0, 0, nameLabel);
		previewGrid.setWidget(0, 1, methodLabel);
		previewGrid.setWidget(0, 2, urlLabel);
		previewGrid.setWidget(0, 3, projectsLabel);

		for (int i = 0; i < len; i++) {
			RequestObject request = requests.get(i);
			previewGrid.setText(i + 1, 0, request.getName());
			previewGrid.setText(i + 1, 1, request.getMethod());
			previewGrid.setText(i + 1, 2, request.getURL());
			String projectName = "";
			int projectId = request.getProject();
			if (projectId > 0) {
				for (ProjectObject project : projects) {
					if (project.getId() == projectId) {
						projectName = project.getName();
						break;
					}
				}
			}
			previewGrid.setText(i + 1, 3, projectName);
		}
		importPreview.add(previewGrid);
		importPreview.setVisible(true);
		currentFileImport = result;
	}

	private void saveCurrentFileImportData() {
		if (currentFileImport == null) {
			cleanUpImportData();
			return;
		}
		listener.saveImportedFileData(currentFileImport, new Callback<Boolean, Void>() {

			@Override
			public void onSuccess(Boolean result) {
				if (result != null && result) {
					cleanUpImportData();
					StatusNotification.notify("Data saved.", StatusNotification.TIME_SHORT);
				} else {
					StatusNotification.notify("Data save error.", StatusNotification.TIME_MEDIUM);
				}
			}

			@Override
			public void onFailure(Void reason) {
				StatusNotification.notify("Data save error.", StatusNotification.TIME_MEDIUM);
			}
		});
	}

	private void cleanUpImportData() {
		if (previewGrid != null) {
			previewGrid.clear();
		}
		importPreview.setVisible(false);
		currentFileImport = null;
	}

	@UiHandler("saveImportedData")
	void onSaveImportedData(ClickEvent e) {
		saveCurrentFileImportData();
	}

	@UiHandler("backSettings")
	void onGoBackToSettings(ClickEvent e) {
		e.preventDefault();
		listener.goTo(new SettingsPlace(null));
	}

	//
	// OLD SYSTEM
	//
	@UiField
	InlineLabel loggedInInfo;
	@UiField
	InlineLabel statusInfo;
	@UiField
	DivElement storeDataPanel;
	@UiField
	DivElement shareUrlPanel;
	@UiField
	ParagraphElement connectNote;
	@UiField
	PreElement shareLink;

	@UiField
	Button connectButton;
	@UiField
	Button storeData;
	@UiField
	Button restoreData;

	@Override
	public void setIsUserView() {
		statusInfo.setText("");

		//
		// OLD SYSTEM
		//
		hideConnectControls();
		showShareLink();
	}

	/**
	 * Hide controls like "connect to application"
	 */
	private void hideConnectControls() {
		loggedInInfo.setVisible(true);
		connectButton.setVisible(false);
		connectNote.getStyle().setDisplay(Display.NONE);
		storeDataPanel.getStyle().setDisplay(Display.BLOCK);
	}

	private void showShareLink() {
		String applicationUserId = listener.getApplicationUserId();
		if (applicationUserId == null)
			return;

		shareUrlPanel.getStyle().setDisplay(Display.BLOCK);
		String url = "";
		if (Window.Location.getHost().startsWith("127.")) { // DEV mode
			url = "http://127.0.0.1:8888/RestClient.html?gwt.codesvr=127.0.0.1:9997";
		} else {
			// TODO: url = Extension.getURL("/RestClient.html");
			url = "/RestClient.html";

		}
		url += "#ImportExportPlace:import/" + applicationUserId;
		shareLink.setInnerText(url);
	}

	@UiHandler("connectButton")
	void onConnectButton(ClickEvent e) {
		String signInUrl = ApplicationRequest.AUTH_URL + "/signin?ret=";

		String returnPath = "";
		if (Window.Location.getHost().startsWith("127.")) { // DEV MODE
			returnPath = "http://127.0.0.1:8888/auth.html#auth";
		} else {
			// TODO: returnPath = Runtime.getURL("/auth.html#auth");
			returnPath = "/auth.html#auth";
		}
		signInUrl = signInUrl + URL.encodeQueryString(returnPath);
		if (!Tabs.isSupported()) { // DEV MODE
			Window.open(signInUrl, "_blank", "");
		} else {
			Tabs tabs = GWT.create(Tabs.class);
			CreateProperties cp = CreateProperties.create();
			cp.setUrl(signInUrl);
			tabs.create(cp, new TabCallback() {
				@Override
				public void onResult(Tab tab) {
				}
			});
		}
	}

	@Override
	public void setIsNotUserView() {
		statusInfo.setText("");
	}

	@Override
	public void serverControlsSetEnabled(boolean enabled) {
		storeData.setEnabled(enabled);
		restoreData.setEnabled(enabled);
	}

	@UiHandler("storeData")
	void onStoreDataClick(ClickEvent e) {
		String applicationUserId = listener.getApplicationUserId();
		if (applicationUserId == null) {
			StatusNotification.notify("Connect to application first (not logged in)", StatusNotification.TIME_MEDIUM);
			return;
		}
		storeData.setEnabled(false);
		restoreData.setEnabled(false);
		listener.serverStoreAction();
	}

	@UiHandler("restoreData")
	void onRestoreDataClick(ClickEvent e) {
		String applicationUserId = listener.getApplicationUserId();
		if (applicationUserId == null) {
			StatusNotification.notify("Connect to application first (not logged in)", StatusNotification.TIME_MEDIUM);
			return;
		}

		storeData.setEnabled(false);
		restoreData.setEnabled(false);
		// Show dialog
		final LoaderDialog dialog = new LoaderDialog("Preparing data to download. Please wait.", false);
		dialog.show();
		GoogleAnalytics.sendEvent("Settings usage", "Import data", "Import server dialog");
		GoogleAnalyticsApp.sendEvent("Settings usage", "Import data", "Import server dialog");

		// Make request
		ImportRequest.getImportSuggestions("me", new ImportSuggestionsCallback() {

			@Override
			public void onSuccess(List<SuggestionImportItem> result) {
				dialog.hide();
				if (result == null) {
					StatusNotification.notify("Server returns empty data", StatusNotification.TIME_SHORT);
					restoreData.setEnabled(true);
					return;
				}
				final ImportListingDialog importDialog = new ImportListingDialog(listener);
				importDialog.append(result);
				//
				// delay show dialog for data providers to refresh the
				// list
				// and show dialog in it's place (center)
				//
				new Timer() {
					@Override
					public void run() {
						importDialog.show();
					}
				}.schedule(200);
			}

			@Override
			public void onFailure(String message, Throwable exception) {
				if (RestClient.isDebug()) {
					if (exception != null) {
						Log.error(message, exception);
					} else {
						Log.error(message);
					}
				}
				StatusNotification.notify(message, StatusNotification.TIME_MEDIUM);
				dialog.hide();
				storeData.setEnabled(true);
				restoreData.setEnabled(true);
			}
		});
	}
}
