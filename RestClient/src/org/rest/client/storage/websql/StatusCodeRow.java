package org.rest.client.storage.websql;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Class representing single row of Status Codes database table.
 * 
 * @author jarrod
 * 
 */
public class StatusCodeRow extends JavaScriptObject {
	/**
	 * Empty.
	 */
	protected StatusCodeRow() {
	}
	/**
	 * Create an instance of an overlay JS object.
	 * This is only method to create an instance of this object.
	 * @return Instance of this object
	 */
	public static final native StatusCodeRow create() /*-{
		return {
			label : null,
			code : -1,
			desc : null
		};
	}-*/;
	/**
	 * Create an instance of an overlay JS object.
	 * This is only method to create an instance of this object.
	 * @return Instance of this object
	 */
	public static final native StatusCodeRow create(int code, String label, String desc) /*-{
		return {
			label: label,
			code: code,
			desc: desc
		};
	}-*/;

	public final native void setLabel(String label) /*-{
		this.label = label;
	}-*/;

	public final native void setCode(int code) /*-{
		this.code = code;
	}-*/;

	public final native void setDesc(String desc) /*-{
		this.desc = desc;
	}-*/;

	/**
	 * Return label field.
	 * 
	 * @return label string
	 */
	public final native String getLabel()/*-{
		return this.label;
	}-*/;

	/**
	 * 
	 * @return database ID
	 */
	public final native int getId()/*-{
		return this.ID;
	}-*/;

	/**
	 * 
	 * @return "code" column value
	 */
	public final native int getCode()/*-{
		return this.code;
	}-*/;

	/**
	 * 
	 * @return "desc" column value
	 */
	public final native String getDesc()/*-{
		return this.desc;
	}-*/;

}
