package com.restclient.client.widgets;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.xhr2.client.Header;
import com.restclient.client.Utils;
/**
 * Object representing sigle header line.
 * @author Paweł Psztyć
 *
 */
public class HeaderLine extends Composite {
	interface Binder extends UiBinder<Widget, HeaderLine> {}
	
	interface LineStyle extends CssResource {
		String opened();
	}
	@UiField Anchor hintHandler;
	@UiField SpanElement headerNameTitle;
	@UiField SpanElement headerValueTitle;
	@UiField SpanElement desc;
	@UiField SpanElement example;
	@UiField SpanElement headerName;
	@UiField DivElement hintParent;
	@UiField LineStyle style;
	/**
	 * 
	 * @param header
	 */
	public HeaderLine(Header header) {
		initWidget(GWT.<Binder> create(Binder.class).createAndBindUi(this));
		String name = header.getName();
		if( name != null ){
			headerNameTitle.setInnerText(name);
		}
		String value = header.getValue();
		if( value != null ){
			headerValueTitle.setInnerHTML( Utils.autoLinkUrls(value) );
		}
		hintHandler.setVisible(false);
	}
	/**
	 * Update header name value.
	 * @param name
	 */
	public void updateName(String name){
		if( name != null && !name.equals("") ){
			this.headerName.setInnerHTML(name);
			this.headerName.removeClassName("hidden");
			this.headerNameTitle.setInnerHTML(name);
		}
	}
	/**
	 * 
	 * @param desc
	 */
	public void updateDesc(String desc){
		if( desc != null ){
			this.desc.setInnerHTML( desc );
			hintHandler.setVisible(true);
		}
	}
	/**
	 * 
	 * @param example
	 */
	public void updateExample(String example){
		if( example != null && !example.equals("") ){
			this.example.setInnerHTML(example);
			this.example.removeClassName("hidden");
		}
	}
	
	
	@UiHandler("hintHandler")
	void handleClick(ClickEvent e) {
		Element parent = hintParent.getParentElement();
		String currentClass = parent.getClassName();
		if( currentClass.contains( style.opened() ) ){
			parent.removeClassName( style.opened() );
			hintParent.setAttribute("style", "height:0px");
		} else {
			parent.addClassName( style.opened() );
			hintParent.setAttribute("style", "height:"+hintParent.getScrollHeight()+"px");
		}
	}
}
