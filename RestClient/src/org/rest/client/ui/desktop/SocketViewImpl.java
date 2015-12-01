package org.rest.client.ui.desktop;

import java.util.Date;

import org.rest.client.analytics.GoogleAnalytics;
import org.rest.client.suggestion.SocketSuggestOracle;
import org.rest.client.tutorial.TutorialFactory;
import org.rest.client.ui.SocketView;
import org.rest.client.ui.TutorialDialog;
import org.rest.client.ui.TutorialDialog.Direction;
import org.rest.client.ui.desktop.widget.SocketResponseLine;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.websocket.client.SocketMessage;
import com.google.gwt.websocket.client.WebSocket;

public class SocketViewImpl extends Composite implements SocketView {

	private static SocketViewImplUiBinder uiBinder = GWT
			.create(SocketViewImplUiBinder.class);

	interface SocketViewImplUiBinder extends UiBinder<Widget, SocketViewImpl> {
	}
	class WidgetStyle {
		String urlInput = "Socket_View_urlInput";
		String connected = "Socket_View_connected";
		String disconnected = "Socket_View_disconnected";
	}
	class UrlsSuggestionDisplay extends DefaultSuggestionDisplay {
		@Override
		public boolean isAnimationEnabled() {
			return false;
		}
	}
	
	@UiField HTMLPanel urlPanel;
	@UiField DivElement outputPanel;
	WidgetStyle style = new WidgetStyle();
	@UiField Button connectButton;
	@UiField Button disconnectButton;
	@UiField SpanElement statusImage;
	@UiField InlineLabel connectionStatusDisplay;
	@UiField TextArea messageField;
	@UiField Anchor saveAnchor;
	
	private SuggestBox urlField;
	private Presenter listener;
	private UrlsSuggestionDisplay suggestionsDisplay;
	private SocketSuggestOracle suggestOracle;
	
	public SocketViewImpl() {
		initWidget(uiBinder.createAndBindUi(this));
		
		messageField.getElement().setAttribute("placeholder", "Type your message...");
	}
	

	@Override
	public void setPresenter(Presenter listener) {
		this.listener = listener;
		setUpSuggestions();
	}
	
	private void setUpSuggestions(){
		suggestOracle = listener.getUrlsSuggestOracle();
		suggestionsDisplay = new UrlsSuggestionDisplay();
		suggestionsDisplay.setAnimationEnabled(false);
		urlField = new SuggestBox(suggestOracle, new TextBox(), suggestionsDisplay);
		urlField.getElement().setAttribute("placeholder", "URL");
		urlField.addStyleName(style.urlInput);
		urlPanel.add(urlField);
		//connect on enter
		urlPanel.addDomHandler(new KeyDownHandler() {
			@Override
			public void onKeyDown(KeyDownEvent event) {
				if(event.getNativeKeyCode() == KeyCodes.KEY_ENTER){
					doConnect();
				}
			}
		}, KeyDownEvent.getType());
	}
	
	@Override
	public void setUpTutorial(final TutorialFactory factory) {
		new Timer() {
			@Override
			public void run() {
				doTutorial(factory);
			}
		}.schedule(500);
		
	}


	private void doTutorial(TutorialFactory factory) {
		TutorialDialog url = TutorialFactory.createItem();
		url.setReferencedElement(urlPanel.getElement(), Direction.BOTTOM);
		url.setPositionCorrection(0, -13);
		url.setHTML("Type in socket URL. For example: ws://echo.websocket.org");
		url.showArrow(Direction.TOP);
		factory.addItem(url);
		
		TutorialDialog message = TutorialFactory.createItem();
		message.setReferencedElement(messageField.getElement(), Direction.LEFT);
		message.setPositionCorrection(0, 600);
		message.setHTML("Here you can type in you message. All you type in will be sent to server.<br/>Use CTRL + ENTER for quick send.");
		message.showArrow(Direction.LEFT);
		factory.addItem(message);
		
		
		TutorialDialog saveToFile = TutorialFactory.createItem();
		saveToFile.setReferencedElement(saveAnchor.getElement(), Direction.RIGHT);
		saveToFile.setPositionCorrection(-9, 70);
		saveToFile.setHTML("You can save log file with whole conversation.<br/>Remeber only, messages are stored in browsers memory until you clear it or navigate to another panel.");
		saveToFile.showArrow(Direction.LEFT);
		factory.addItem(saveToFile);
		
		factory.start();
	}

	@Override
	public void setUrl(String url) {
		urlField.setValue(url);
	}

	@Override
	public String getUrl() {
		return urlField.getValue();
	}

	@Override
	public void setResponse(SocketMessage message) {
		String msg = message.getStringMessage();
		SocketResponseLine widget = new SocketResponseLine(true, msg);
		if(outputPanel.getChildCount()>0){
			outputPanel.insertBefore(widget.getElement(), outputPanel.getFirstChild());
		} else {
			outputPanel.appendChild(widget.getElement());
		}
		stayOnTop();
	}

	@Override
	public void setConnectionStatus(int status) {
		switch(status){
		case WebSocket.CLOSED:
			statusImage.replaceClassName(style.connected, style.disconnected);
			connectionStatusDisplay.setText("disconnected");
			disconnectButton.setVisible(false);
			connectButton.setVisible(true);
			connectButton.setEnabled(true);
			break;
		case WebSocket.CLOSING:
			statusImage.replaceClassName(style.connected, style.disconnected);
			connectionStatusDisplay.setText("disconnecting");
			disconnectButton.setVisible(false);
			break;
		case WebSocket.OPEN:
			statusImage.replaceClassName(style.disconnected, style.connected);
			connectionStatusDisplay.setText("connected");
			disconnectButton.setVisible(true);
			connectButton.setVisible(false);
			connectButton.setEnabled(true);
			break;
		case WebSocket.CONNECTING:
			statusImage.replaceClassName(style.disconnected, style.connected);
			connectionStatusDisplay.setText("connecting");
			disconnectButton.setVisible(false);
			connectButton.setEnabled(false);
			break;
		}
	}
	
	@UiHandler("messageField")
	void onMessageKeyDown(KeyDownEvent e){
		if(e.isControlKeyDown()){
			if(e.getNativeKeyCode() == KeyCodes.KEY_ENTER){
				e.preventDefault();
				doSendMessage();
			}
		}
	}
	
	@UiHandler("connectButton")
	void onConnectClick(ClickEvent e){
		doConnect();
		GoogleAnalytics.sendEvent("Engagement", "Click", "Connect to socket");
	}
	
	private void doConnect(){
		listener.disconnect();
		String url = urlField.getValue();
		if(url == null || url.isEmpty()){
			StatusNotification.notify("You must enter socket URL.", StatusNotification.TYPE_NORMAL, StatusNotification.TIME_SHORT);
			return;
		}
		listener.connect(url);
		connectButton.setEnabled(false);
		outputPanel.setInnerHTML("");
	}
	
	@UiHandler("disconnectButton")
	void onDisconnectClick(ClickEvent e){
		listener.disconnect();
	}
	@UiHandler("sendButton")
	void onSend(ClickEvent e){
		doSendMessage();
		GoogleAnalytics.sendEvent("Engagement", "Click", "Send message to socket");
	}


	private void doSendMessage() {
		if(!listener.canSendMessage()){
			StatusNotification.notify("Socket not ready.",StatusNotification.TYPE_ERROR, StatusNotification.TIME_SHORT);
			return;
		}
		
		String data = messageField.getValue();
		SocketResponseLine widget = new SocketResponseLine(false, data);
		if(outputPanel.getChildCount()>0){
			outputPanel.insertBefore(widget.getElement(), outputPanel.getFirstChild());
		} else {
			outputPanel.appendChild(widget.getElement());
		}
		stayOnTop();
		listener.sendMessage(data);
	}
	
	@UiHandler("clearAnchor")
	void onClearLog(ClickEvent e){
		e.preventDefault();
		outputPanel.setInnerHTML("");
		listener.clearLog();
	}
	@UiHandler("saveAnchor")
	void onSaveAsFile(ClickEvent e){
		final Anchor anchor = (Anchor)e.getSource();
		final Element anchorElement = anchor.getElement();
		String download = anchorElement.getAttribute("download");
		if(download != null && !download.isEmpty()){
			//already have download.
			if(!anchorElement.getAttribute("disabled").isEmpty()){
				return;
			}
			anchorElement.setAttribute("disabled", "true");
			Timer t = new Timer() {
				@Override
				public void run() {
					anchor.setHref("about:blank");
					anchor.setText("save as file");
					anchorElement.removeAttribute("download");
					anchorElement.removeAttribute("data-downloadurl");
					anchorElement.removeAttribute("disabled");
					listener.revokeDownloadData();
				}
			};
			t.schedule(1500);
			return;
		}
		e.preventDefault();
		listener.prepareDownloadData(new Callback<String, Throwable>() {
			@Override
			public void onSuccess(String downloadUrl) {
				anchor.setHref(downloadUrl);
				String date = DateTimeFormat.getFormat(PredefinedFormat.DATE_TIME_MEDIUM).format(new Date());
				String fileName = "arc-socket-"+date+".log";
				anchorElement.setAttribute("download", fileName);
				anchorElement.setAttribute("data-downloadurl", "text/plain:"+fileName+":"+downloadUrl);
				anchor.setText("Download");
			}
			
			@Override
			public void onFailure(Throwable reason) {
				
			}
		});
	}
	
	private final native void stayOnTop()/*-{
		$wnd.scrollTo(0,0);
	}-*/;
}
