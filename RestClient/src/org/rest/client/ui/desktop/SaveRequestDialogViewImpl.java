package org.rest.client.ui.desktop;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.rest.client.RestClient;
import org.rest.client.StatusNotification;
import org.rest.client.analytics.GoogleAnalytics;
import org.rest.client.analytics.GoogleAnalyticsApp;
import org.rest.client.gdrive.GoogleDrive;
import org.rest.client.gdrive.DriveFileItem;
import org.rest.client.place.RequestPlace;
import org.rest.client.request.URLParser;
import org.rest.client.storage.StoreResultCallback;
import org.rest.client.storage.store.StoreKeys;
import org.rest.client.storage.store.ProjectStoreWebSql;
import org.rest.client.storage.store.objects.ProjectObject;
import org.rest.client.storage.store.objects.RequestObject;
import org.rest.client.ui.SaveRequestDialogView;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class SaveRequestDialogViewImpl implements CloseHandler<PopupPanel>, KeyDownHandler, SaveRequestDialogView {
	
	interface Binder extends UiBinder<DialogBox, SaveRequestDialogViewImpl> {
		Binder BINDER = GWT.create(Binder.class);
	}
	
	@UiField DialogBox dialog;
	@UiField TextBox name;
	@UiField TextBox prevUrlTextBox;
	@UiField TextBox projectName;
	@UiField CheckBox addToProject;
	@UiField ListBox projectList;
	@UiField DivElement projectNameContainer;
	@UiField DivElement requestOverwriteContainer;
	@UiField Button save;
	@UiField Button overwrite;
	@UiField Button gdrive;
	
	@UiField CheckBox protocolStatus;
	@UiField CheckBox serverStatus;
	@UiField CheckBox pathStatus;
	@UiField CheckBox parametersStatus;
	@UiField CheckBox tokenStatus;
	@UiField CheckBox methodStatus;
	@UiField CheckBox payloadStatus;
	@UiField CheckBox headersStatus;
	
	String requestOrygURL = "";
	int overwriteId = -1;
	boolean forceOverwrite = false;
	String gDriveItem = null;
	private String gDriveCreateFolder = null;
	
	public SaveRequestDialogViewImpl(){
		setPreviewURL();
		Binder.BINDER.createAndBindUi(this);
		
		RestClient.isSaveDialogEnabled = true;
		
		if(requestOrygURL == null || requestOrygURL.isEmpty()){
			return;
		}
		dialog.addDomHandler(this, KeyDownEvent.getType());
		dialog.addCloseHandler(this);
		
		name.getElement().setAttribute("placeholder", "name...");
		projectName.getElement().setAttribute("placeholder", "project name...");
		
		addToProject.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				if(event.getValue()){
					projectList.setEnabled(true);
					gdrive.setEnabled(false);
				} else {
					projectList.setEnabled(false);
					gdrive.setEnabled(true);
				}
				projectListValueChange();
			}
		});
		
		projectList.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				projectListValueChange();
			}
		});
		
		ValueChangeHandler<Boolean> optionStatusChange = new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				updatePreviewURL();
			}
		};
		
		protocolStatus.addValueChangeHandler(optionStatusChange);
		serverStatus.addValueChangeHandler(optionStatusChange);
		pathStatus.addValueChangeHandler(optionStatusChange);
		parametersStatus.addValueChangeHandler(optionStatusChange);
		tokenStatus.addValueChangeHandler(optionStatusChange);
//		methodStatus.addValueChangeHandler(optionStatusChange);
//		payloadStatus.addValueChangeHandler(optionStatusChange);
		
		prevUrlTextBox.setText(requestOrygURL);
		updatePreviewURL();
		setProjectsList();
		
		//
		//check if it is a restored request. The user may want to overwrite existing request.
		//
		Storage store = Storage.getSessionStorageIfSupported();
		String restored = store.getItem(StoreKeys.RESTORED_REQUEST);
		gDriveItem = store.getItem(StoreKeys.CURRENT_GOOGLE_DRIVE_ITEM);
		gDriveCreateFolder = store.getItem(StoreKeys.GOOGLE_DRIVE_CREATE_FOLDER_ID);
		
		if(restored != null && !restored.isEmpty()){
			int restoredId = -1;
			try{
				restoredId = Integer.parseInt(restored);
			} catch(Exception e){}
			if(restoredId > 0){
				overwriteId = restoredId;
				RestClient.getClientFactory().getRequestDataStore().getByKey(restoredId, new StoreResultCallback<RequestObject>(){
					@Override
					public void onSuccess(RequestObject result) {
						if (result == null) {
							overwriteId = -1;
							return;
						}
						
						name.setValue(result.getName());
						overwrite.setVisible(true);
						save.setText("Save as new");
					}

					@Override
					public void onError(Throwable e) {
						overwriteId = -1;
						Log.error("Unable read stored data :(", e);
					}});
			}
		}
		if(gDriveItem != null && !gDriveItem.isEmpty()){
			RestClient.collectRequestData(new Callback<RequestObject, Throwable>() {
				@Override
				public void onSuccess(RequestObject result) {
//					Log.debug("NAME: " + result.getName());
					name.setValue(result.getName());
					overwrite.setVisible(true);
					overwrite.addStyleName("driveButton");
					gdrive.setVisible(false);
//					save.setVisible(false);
//					name.setEnabled(false);
//					addToProject.setEnabled(false);
				}
				
				@Override
				public void onFailure(Throwable reason) {
					if(RestClient.isDebug()){
						Log.error("Unable collect request data", reason);
					}
				}
			});
		} else if(gDriveCreateFolder != null && !gDriveCreateFolder.isEmpty()){
			overwrite.setVisible(false);
			save.setVisible(false);
			addToProject.setEnabled(false);
		}
	}
	
	
	private void setProjectsList(){
		final ProjectStoreWebSql store = RestClient.getClientFactory().getProjectsStore();
		store.all(new StoreResultCallback<Map<Integer,ProjectObject>>() {
			@Override
			public void onSuccess(Map<Integer, ProjectObject> result) {
				
				Iterator<Entry<Integer, ProjectObject>> it = result.entrySet().iterator();
				while(it.hasNext()){
					Entry<Integer, ProjectObject> set = it.next();
					ProjectObject project = set.getValue();
					if(project == null){
						continue;
					}
					String name = project.getName();
					if(name == null || name.isEmpty()){
						continue;
					}
					int id = project.getId();
					projectList.addItem(name, String.valueOf(id));
				}
			}
			@Override
			public void onError(Throwable e) {
				if(RestClient.isDebug()){
					Log.error("Unable to read stored projects. Error during read operation.", e);
				}
				StatusNotification.notify("Unable to set projects data..", StatusNotification.TIME_MEDIUM);
			}
		});
			
		
	}
	
	private void setPreviewURL(){
		if(History.getToken().startsWith("RequestPlace")){
			requestOrygURL = RestClient.getClientFactory().getRequestView().getUrl();
		} else {
			RequestObject
			.restoreLatest(new Callback<RequestObject, Throwable>() {
				@Override
				public void onSuccess(RequestObject result) {
					requestOrygURL = result.getURL();
				}
	
				@Override
				public void onFailure(Throwable caught) {
				}
			});
		}
	}
	private void updatePreviewURL(){
		if(requestOrygURL == null || requestOrygURL.isEmpty()){
			return;
		}
		
		URLParser data = new URLParser().parse(requestOrygURL);
		String url = "";
		if(protocolStatus.getValue()){
			url += "[FUTURE]";
		} else {
			url += data.getProtocol();
		}
		url += "://";
		
		if(serverStatus.getValue()){
			url += "[FUTURE]";
		} else {
			url += data.getAuthority();
		}
		
		if(pathStatus.getValue()){
			url += "/[FUTURE]/";
		} else {
			if(data.getPath() != null && !data.getPath().isEmpty()){
				url += data.getPath();
			}
		}
		if(parametersStatus.getValue()){
			url += "?[FUTURE]";
		} else {
			if(data.getQuery() != null && !data.getQuery().isEmpty()){
				url += "?" + data.getQuery();
			}
		}
		if(tokenStatus.getValue()){
			url += "#[FUTURE]";
		} else {
			if(data.getAnchor() != null && !data.getAnchor().isEmpty()){
				url += "#" + data.getAnchor();
			}
		}
		prevUrlTextBox.setText(url);
	}
	
	private void projectListValueChange(){
		if(!addToProject.getValue()){
			requestOverwriteContainer.addClassName("hidden");
			projectNameContainer.addClassName("hidden");
			return;
		}
		
		
		String _projectName = projectList.getValue(projectList.getSelectedIndex());
		if(_projectName.equals("")){
			requestOverwriteContainer.addClassName("hidden");
			projectNameContainer.addClassName("hidden");
			return;
		}
		
		requestOverwriteContainer.removeClassName("hidden");
		
		if(_projectName.equals("__new__")){
			projectNameContainer.removeClassName("hidden");
			requestOverwriteContainer.addClassName("hidden");
		} else {
			int parojectId = -1;
			projectNameContainer.addClassName("hidden");
			try{
				parojectId = Integer.parseInt(_projectName);
			} catch(Exception e){}
			if(parojectId == -1){
				StatusNotification.notify("This is not a valid project!", StatusNotification.TIME_SHORT);
			}
		}
		
		
		dialog.center();
	}
	
	@Override
	public void show() {
		if(requestOrygURL == null || requestOrygURL.isEmpty()){
			StatusNotification.notify("Current request has no URL value :/", StatusNotification.TIME_SHORT);
			return;
		}
		dialog.show();
		dialog.center();
	}
	
	@UiHandler("cancel")
	void onDismiss(ClickEvent event) {
		dialog.hide();
	}
	@UiHandler("save")
	void onSave(ClickEvent event) {
		save.setEnabled(false);
		forceOverwrite = false;
		doSaveRequest();
	}
	@UiHandler("overwrite")
	void onOverwrite(ClickEvent event){
		save.setEnabled(false);
		overwrite.setEnabled(false);
		forceOverwrite = true;
		
		if(gDriveItem != null && !gDriveItem.isEmpty()){
			doSaveGdrive();
			return;
		}
		
		doSaveRequest();
	}
	
	
	
	@Override
	public void onClose(CloseEvent<PopupPanel> event) {
		RestClient.isSaveDialogEnabled = false;
	}
	
	
	@Override
	public void onKeyDown(KeyDownEvent event) {
		
		int keyCode = event.getNativeKeyCode();
		if (keyCode == KeyCodes.KEY_ENTER) {
			onSave(null);
		} else if (keyCode == KeyCodes.KEY_ESCAPE) {
			onDismiss(null);
		}
	}

	@Override
	public Widget asWidget() {
		return dialog.asWidget();
	}
	
	@UiHandler("gdrive")
	void onGdriveSave(ClickEvent e){
		e.preventDefault();
		
		if(name.getValue().isEmpty()){
			StatusNotification.notify("Name can't be empty.", StatusNotification.TIME_SHORT);
			save.setEnabled(true);
			return;
		}
		
		gdrive.setEnabled(false);
		save.setEnabled(false);
		
		doSaveGdrive();
	}
	/**
	 * TODO: views shouldn't perform logic actions.
	 */
	void doSaveGdrive(){
		this.dialog.setVisible(false);
		RestClient.collectRequestData(new Callback<RequestObject, Throwable>() {
			
			@Override
			public void onSuccess(final RequestObject result) {
				if(!forceOverwrite){
					result.setName(name.getValue());
				} else {
					result.setGDriveId(gDriveItem);
				}
				result.setSkipHeaders(headersStatus.getValue());
				result.setSkipHistory(tokenStatus.getValue());
				result.setSkipMethod(methodStatus.getValue());
				result.setSkipParams(parametersStatus.getValue());
				result.setSkipPayload(payloadStatus.getValue());
				result.setSkipProtocol(protocolStatus.getValue());
				result.setSkipServer(serverStatus.getValue());
				result.setSkipPath(pathStatus.getValue());
				if(gDriveCreateFolder != null && gDriveCreateFolder.isEmpty()){
					gDriveCreateFolder = null;
				}
				
				GoogleDrive.saveRequestFile(result, gDriveCreateFolder, new Callback<DriveFileItem, Throwable>() {
					
					@Override
					public void onSuccess(DriveFileItem result) {
						save.setEnabled(true);
						gdrive.setEnabled(true);
						
						if(result == null){
							//only if cancel
							return;
						}
						
						dialog.hide();
						StatusNotification.notify("File saved", StatusNotification.TIME_SHORT);
						
						Storage store = Storage.getSessionStorageIfSupported();
						store.removeItem(StoreKeys.CURRENT_GOOGLE_DRIVE_ITEM);
						store.removeItem(StoreKeys.GOOGLE_DRIVE_CREATE_FOLDER_ID);
						
						RestClient.getClientFactory().getPlaceController().goTo(RequestPlace.Tokenizer.fromDriveFile(result.getId()));
					}
					
					@Override
					public void onFailure(Throwable reason) {
						save.setEnabled(true);
						gdrive.setEnabled(true);
						if(RestClient.isDebug()){
							Log.error("Unable to save request data.", reason);
						}
						StatusNotification.notify(reason.getMessage(), StatusNotification.TIME_MEDIUM);
					}
				});
				
				
			}
			
			@Override
			public void onFailure(Throwable reason) {
				save.setEnabled(true);
				gdrive.setEnabled(true);
				if(RestClient.isDebug()){
					Log.error("Unable to save request data. Can't collect current request data.", reason);
				}
				StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
			}
		});
	}
	
	private void doSaveRequest(){
		if(name.getValue().isEmpty()){
			StatusNotification.notify("Name can't be empty.", StatusNotification.TIME_SHORT);
			save.setEnabled(true);
			return;
		}
		GoogleAnalytics.sendEvent("Engagement", "Click", "Save request");
		GoogleAnalyticsApp.sendEvent("Engagement", "Click", "Save request");
		
		RestClient.collectRequestData(new Callback<RequestObject, Throwable>() {
			
			@Override
			public void onSuccess(final RequestObject result) {
				result.setName(name.getValue());
				result.setSkipHeaders(headersStatus.getValue());
				result.setSkipHistory(tokenStatus.getValue());
				result.setSkipMethod(methodStatus.getValue());
				result.setSkipParams(parametersStatus.getValue());
				result.setSkipPayload(payloadStatus.getValue());
				result.setSkipProtocol(protocolStatus.getValue());
				result.setSkipServer(serverStatus.getValue());
				result.setSkipPath(pathStatus.getValue());
				if(forceOverwrite && overwriteId > 0){
					result.setId(overwriteId);
				}
				//
				//check project data
				//
				String _projectName = projectList.getValue(projectList.getSelectedIndex());
				if(_projectName.equals("__new__")){
					String newProjectName = projectName.getValue();
					//add new project
					RestClient.saveRequestData(result, newProjectName, new Callback<RequestObject, Throwable>() {
						@Override
						public void onSuccess(RequestObject result) {
							save.setEnabled(true);
							
							RestClient.getClientFactory().getPlaceController().goTo(RequestPlace.Tokenizer.fromSaved(result.getId()));
							dialog.hide();
						}
						@Override
						public void onFailure(Throwable reason) {
							save.setEnabled(true);
							StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
						}
					});
					return;
				} else if (!_projectName.equals("")){
					int projectId = -1;
					projectNameContainer.addClassName("hidden");
					try{
						projectId = Integer.parseInt(_projectName);
					} catch(Exception e){}
					
					if(projectId == -1){
						save.setEnabled(true);
						StatusNotification.notify("This is not a valid project!", StatusNotification.TIME_SHORT);
						if(RestClient.isDebug()){
							Log.error("Unable to save request data. Selected project has no numeric value.");
						}
						return;
					}
					result.setProject(projectId);
				}
				RestClient.saveRequestData(result, new Callback<RequestObject, Throwable>() {
					@Override
					public void onSuccess(RequestObject result) {
						save.setEnabled(true);
						dialog.hide();
//						if(gDriveItem != null && !gDriveItem.isEmpty()){
						RestClient.getClientFactory().getPlaceController().goTo(RequestPlace.Tokenizer.fromSaved(result.getId()));
//						}
					}
					@Override
					public void onFailure(Throwable reason) {
						save.setEnabled(true);
						StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
					}
				});
			}
			
			@Override
			public void onFailure(Throwable reason) {
				save.setEnabled(true);
				if(RestClient.isDebug()){
					Log.error("Unable to save request data. Can't collect current request data.", reason);
				}
				StatusNotification.notify("Unable to save request data!", StatusNotification.TIME_MEDIUM);
			}
		});
	}
}
