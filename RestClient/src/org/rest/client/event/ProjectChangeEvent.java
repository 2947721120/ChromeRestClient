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
package org.rest.client.event;

import org.rest.client.jso.ProjectObject;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

/**
 * The user has changed project metadata (name) and changes has been saved.
 * 
 * <p>
 * It will be propagated through the applications {@link EventBus} and fired on
 * browser's document object as custom event {@link CustomEvent#PROJECT_CHANGE} so
 * external extensions can handle action.
 * </p>
 * <p>
 * For example:
 * 
 * <pre>
 * document.addEventListener('arc:projectchange', function(e){ //do something... });
 * </pre>
 * 
 * </p>
 * <p>
 * <b>Note</b> there is no sure that this value has been saved to storage.</p>
 * 
 * @author Paweł Psztyć
 */
public class ProjectChangeEvent extends Event<ProjectChangeEvent.Handler> {
	public static final Type<Handler> TYPE = new Type<Handler>();

	/**
	 * Register an handler for this event.
	 * 
	 * @param eventBus
	 * @param handler
	 * @return
	 */
	public static HandlerRegistration register(EventBus eventBus,
			Handler handler) {
		return eventBus.addHandler(TYPE, handler);
	}

	/**
	 * Handles {@link ProjectChangeEvent}.
	 */
	public interface Handler {
		/**
		 * Fired when Add Encoding dialog has been closed either by user or
		 * programmatically.
		 * 
		 * @param encoding
		 *            New encoding value or NULL if user cancel dialog
		 */
		void onProjectChange(ProjectObject project);
	}

	private final ProjectObject project;

	public ProjectChangeEvent(ProjectObject project) {
		this.project = project;
	}

	@Override
	protected void dispatch(Handler handler) {
		handler.onProjectChange(project);
	}

	@Override
	public Event.Type<Handler> getAssociatedType() {
		return TYPE;
	}
}
