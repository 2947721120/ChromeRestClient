package com.google.gwt.xhr2.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.Element;

/**
 * This interface describes a single file in a FileList and exposes its name. It
 * inherits from {@link Blob}.
 * <p>
 * See <a href="http://dev.w3.org/2006/webapi/FileAPI/#dfn-File"
 * >http://dev.w3.org/2006/webapi/FileAPI/#dfn-File</a>
 * </p>
 * @author jarrod
 * 
 */
public class File extends Blob {
	protected File() {
	}
	
	public static File create( Element element, int i ) throws IllegalArgumentException {
		if( !(element.getNodeName().toLowerCase().equals("input") &&
				element.getAttribute("type").equals("file")
				) ){
			throw new IllegalArgumentException("Input element is not form file object.");
		}
		return _create(element, i);
	}
	
	private static native File _create(Element element, int i)/*-{
		return element.files[i];
	}-*/;

	public static File create(Element element)  throws IllegalArgumentException  {
		return create(element, 0);
	};

	public final native String getResult()/*-{
		return this.result;
	}-*/;

	public final native JavaScriptObject getArrayResult()/*-{
		$wnd.console.log(this.result);
		return this.result;
	}-*/;

	/**
	 * The name of the file. There are numerous file name variations on
	 * different systems; this is merely the name of the file, without path
	 * information.
	 * 
	 * @return
	 */
	public final native String getName()/*-{
		return this.name;
	}-*/;

	public final native String getLastModifiedDate()/*-{
		return this.lastModifiedDate;
	}-*/;
}
