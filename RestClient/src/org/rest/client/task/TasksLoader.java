package org.rest.client.task;

import java.util.ArrayList;
import java.util.List;

import org.rest.client.RestClient;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

/**
 * Class provide interface to load data before application can run.
 * Eg. need to update contacts info, get device data etc.
 * It's should be doing in background with loader splash screen
 * an after all tasks return loaded status codes
 * than application should show welcome (or whatever) screen.
 * 
 * 
 * @author jarrod
 *
 */
public class TasksLoader {
	private static boolean running = false;
	/**
	 * Number of tasks on start running
	 */
	private static int initialTasksCount = 0;
	private static int taskFinished = 0;
	private static Callback<Void, Void> appCallback;
	static LoaderWidget loaderWidget = null;
	
	/**
	 * Tasks que.
	 */
	private static List<LoadTask> tasks = new ArrayList<LoadTask>();
	/**
	 * Add new loader task.
	 * @param task
	 */
	public static void addTask(LoadTask task){
		tasks.add(task);
	}
	/**
	 * Check if has more tasks.
	 * If there is no more tasks app should remove splash screen an init app.
	 * @return true if loader has more tasks
	 */
	public static boolean hasMoreTasks(){
		return tasks.size() > 0;
	}
	/**
	 * Remove task from que after finish.
	 * @param task
	 */
	private static void removeTask(LoadTask task){
		tasks.remove(task);
	}
	
	/**
	 * @param callback
	 */
	public static void runTasks(Callback<Void, Void> callback){
		//time("Tasks loader");
		Void v = GWT.create(Void.class);
		if(!hasMoreTasks()){
			callback.onSuccess(v);
			return;
		}
		
		if(running){
			callback.onFailure(v);
			return;
		}
		running = true;
		createSplashScreen();
		
		for(LoadTask task : tasks){
			task.setLoader(loaderWidget);
			initialTasksCount += task.getTasksCount();
		}
		appCallback = callback;
		
		new Timer() {
			@Override
			public void run() {
				try{
					runTasks();
				} catch(Exception e){
					Log.error("Unable to load tasks.", e);
				}
			}
		}.schedule(0);
		
	}
	/**
	 * Create user friendly information about loading page elements.
	 */
	private static void createSplashScreen(){
		loaderWidget = new LoaderWidget();
	}
	/**
	 * Remove splash screen.
	 */
	private static void removeSplashScreen(){
		final Element splash = DOM.getElementById("loader-screen");
		if(splash != null){			
			splash.removeFromParent();
			loaderWidget = null;
		}
		Element appNav = DOM.getElementById("appNavigation");
		if(appNav != null){
			appNav.removeClassName("hidden");
		}
	}
	
	private static void updateLoader(){
		if(loaderWidget == null){
			return;
		}
		int percent = taskFinished*100/initialTasksCount;
		if(RestClient.isDebug()){
			Log.debug( (initialTasksCount-taskFinished)+" tasks left to do of: "+initialTasksCount );
		}
		loaderWidget.setProgress(percent);
	}
	
	private static void runTasks(){
		if(!hasMoreTasks()){
			running = false;
			
			Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
				@Override
				public void execute() {
					removeSplashScreen();
				}
			});
			Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
				@Override
				public void execute() {
					Void v = GWT.create(Void.class);
					appCallback.onSuccess(v);
					//timeEnd("Tasks loader");
				}
			});
			return;
		}
		
		final LoadTask task = tasks.get(0);
		callTask(task, true);
	}
	
	private static void callTask(final LoadTask task, final boolean tryAgainOnFailure){
		if(RestClient.isDebug()){
			Log.debug("Calling task: " + task.getClass().getSimpleName());
		}
		//final String timeName = "Starting task: " + task.getClass().getSimpleName();
		//time(timeName);
		task.run(new TasksCallback() {
			
			@Override
			public void onSuccess() {
				removeTask(task);
				new Timer() {
					@Override
					public void run() {
						//timeEnd(timeName);
						runTasks();
					}
				}.schedule(0);
			}
			
			@Override
			public void onInnerTaskFinished(int _taskFinished) {
				taskFinished += _taskFinished;
				updateLoader();
			}
			
			@Override
			public void onFailure(int finished) {
				if(tryAgainOnFailure){
					//timeEnd(timeName);
					callTask(task, false);
					return;
				}
				taskFinished += task.getTasksCount() - finished;
				//timeEnd(timeName);
				removeTask(task);
				updateLoader();
				runTasks();
			}

			@Override
			public void onFatalError(String message) {
				loaderWidget.setFatalError(message);
			}

		}, !tryAgainOnFailure);
	}
	
	private final native static void time(String name) /*-{
		console.time(name);
	}-*/;
	private final native static void timeEnd(String name) /*-{
		console.timeEnd(name);
	}-*/;
}
