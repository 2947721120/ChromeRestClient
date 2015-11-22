package org.rest.client;

import org.rest.client.storage.store.LocalStore;

import com.google.gwt.chrome.storage.Storage.StorageChangeHandler;
import com.google.gwt.chrome.storage.StorageArea.StorageItemsCallback;
import com.google.gwt.chrome.storage.StorageChangeObject;
import com.google.gwt.chrome.storage.SyncStorageArea;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.storage.client.Storage;

/**
 * Apps' adapter for chrome.sync API.
 * 
 * @TODO: remove any reference to local storage and replace it with chrome.storage.local API. This app must be CSP compliant.
 * 
 * @author Pawel Psztyc
 *
 */
public class SyncAdapter {
	/**
	 * Is debug setting enabled.
	 */
	public static boolean debug = true;
	/**
	 * Id history setting enabled.
	 */
	public static boolean history = true;
	/**
	 * Are notifications from developer setting enabled.
	 */
	public static boolean notifications = false;
	/**
	 * Are magic variables setting enabled.
	 */
	public static boolean magicVars = true;
	/**
	 * is code mirror support for headers setting enabled.
	 */
	public static boolean codeMirrorHeaders = false;
	/**
	 * s code mirror support for payload setting enabled.
	 */
	public static boolean codeMirrorPayload = false;
	/**
	 * True if this adapter already set storage event handlers.
	 */
	public static boolean observing = false;
	
	public static void sync(){
		com.google.gwt.chrome.storage.Storage chromeStore = com.google.gwt.chrome.storage.Storage.getStorage();
		final Storage store = Storage.getLocalStorageIfSupported();
		SyncStorageArea sync = chromeStore.getSync();
		JSONObject query = new JSONObject();
		query.put(LocalStore.DEBUG_KEY, new JSONString("true"));
		query.put(LocalStore.HISTORY_KEY, new JSONString("true"));
		query.put(LocalStore.NOTIFICATIONS_ENABLED_KEY, new JSONString("false"));
		query.put(LocalStore.MAGIC_VARS_ENABLED_KEY, new JSONString("true"));
		query.put(LocalStore.CODE_MIRROR_HEADERS_KEY, new JSONString("false"));
		query.put(LocalStore.CODE_MIRROR_PAYLOAD_KEY, new JSONString("false"));
		
		sync.get(query.getJavaScriptObject(), new StorageItemsCallback() {
			@Override
			public void onResult(JavaScriptObject _data) {
				if(_data == null){
					return;
				}
				SyncData data = _data.cast();
				String _debugValue = data.getDebug();
				String _historyValue = data.getHistory();
				String _notificationsValue = data.getNotifications();
				String _magicVarsValue = data.getMagicVariables();
				String _codeMirrorHeaders = data.getCodeMirrorHeaders();
				String _codeMirrorPayload = data.getCodeMirrorPayload();
				
				if(_debugValue != null){
					if(_debugValue.equals("true")){
						debug = true;
						store.setItem(LocalStore.DEBUG_KEY, "true");
					} else {
						debug = false;
						store.setItem(LocalStore.DEBUG_KEY, "false");
					}
				}
				
				if(_historyValue != null){
					if(_historyValue.equals("true")){
						history = true;
						store.setItem(LocalStore.HISTORY_KEY, "true");
					} else {
						history = false;
						store.setItem(LocalStore.HISTORY_KEY, "false");
					}
				}
				
				
				if(_notificationsValue != null){
					if(_notificationsValue.equals("true")){
						notifications = true;
						store.setItem(LocalStore.NOTIFICATIONS_ENABLED_KEY, "true");
					} else {
						notifications = false;
						store.setItem(LocalStore.NOTIFICATIONS_ENABLED_KEY, "false");
					}
				}
				
				if(_magicVarsValue != null){
					if(_magicVarsValue.equals("true")){
						magicVars = true;
						store.setItem(LocalStore.MAGIC_VARS_ENABLED_KEY, "true");
					} else {
						magicVars = false;
						store.setItem(LocalStore.MAGIC_VARS_ENABLED_KEY, "false");
					}
				}
				
				if(_codeMirrorHeaders != null){
					if(_codeMirrorHeaders.equals("true")){
						codeMirrorHeaders = true;
					} else {
						codeMirrorHeaders = false;
					}
				}
				if(_codeMirrorPayload != null){
					if(_codeMirrorPayload.equals("true")){
						codeMirrorPayload = true;
					} else {
						codeMirrorPayload = false;
					}
				}
			}
			
			@Override
			public void onError(String message) {
				
			}
		});
	}
	/**
	 * Observe changes to the storage.
	 */
	public static void observe(){
		if(observing) return;
		observing = true;
		com.google.gwt.chrome.storage.Storage chromeStore = com.google.gwt.chrome.storage.Storage.getStorage();
		chromeStore.addChangeHandler(new StorageChangeHandler() {
			@Override
			public void onChange(StorageChangeObject data, String areaName) {
				if(areaName.equals(com.google.gwt.chrome.storage.Storage.SYNC)){
					sync();
				}
			}
		});
	}
}
