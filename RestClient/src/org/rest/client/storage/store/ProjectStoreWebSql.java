package org.rest.client.storage.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rest.client.storage.StoreResultCallback;
import org.rest.client.storage.WebSqlAdapter;
import org.rest.client.storage.store.objects.ProjectObject;
import org.rest.client.storage.websql.ProjectService;

import com.allen_sauer.gwt.log.client.Log;
import com.google.code.gwt.database.client.service.DataServiceException;
import com.google.code.gwt.database.client.service.ListCallback;
import com.google.code.gwt.database.client.service.RowIdListCallback;
import com.google.code.gwt.database.client.service.VoidCallback;
import com.google.gwt.core.client.GWT;

public class ProjectStoreWebSql extends WebSqlAdapter<Integer, ProjectObject> {
	
	ProjectService service = GWT.create(ProjectService.class);
	
	
	
	public ProjectService getService(){
		return service;
	}

	@Override
	public void keys(StoreResultCallback<List<Integer>> callback) {
		callback.onError(null);
	}

	@Override
	public void put(ProjectObject obj, Integer key,
			final StoreResultCallback<Integer> callback) {
		if(key == null){
			service.insert(obj, new RowIdListCallback() {
				
				@Override
				public void onFailure(DataServiceException error) {
					callback.onError(error);
				}
				
				@Override
				public void onSuccess(List<Integer> rowIds) {
					if(rowIds.size() == 0){
						callback.onError(null);
						return;
					}
					callback.onSuccess(rowIds.get(0));
				}
			});
		} else {
			service.update(obj, key.intValue(), new VoidCallback() {
				
				@Override
				public void onFailure(DataServiceException error) {
					callback.onError(error);
				}
				
				@Override
				public void onSuccess() {
					Log.debug("Update object: success");
					callback.onSuccess(null);
				}
			});
		}
	}

	@Override
	public void getByKey(Integer key,
			final StoreResultCallback<ProjectObject> callback) {
		service.get(key, new ListCallback<ProjectObject>(){

			@Override
			public void onFailure(DataServiceException error) {
				callback.onError(error);
			}

			@Override
			public void onSuccess(List<ProjectObject> result) {
				if(result.size() == 0){
					callback.onSuccess(null);
					return;
				}
				callback.onSuccess(result.get(0));
			}});
	}

	@Override
	public void exists(Integer key, StoreResultCallback<Boolean> callback) {
		callback.onError(null);
	}

	@Override
	public void all(final StoreResultCallback<Map<Integer, ProjectObject>> callback) {
		service.all(new ListCallback<ProjectObject>() {
			
			@Override
			public void onFailure(DataServiceException error) {
				callback.onError(error);
			}
			
			@Override
			public void onSuccess(List<ProjectObject> result) {
				HashMap<Integer, ProjectObject> res = new HashMap<Integer, ProjectObject>();
				for(ProjectObject obj : result){
					res.put(obj.getId(), obj);
				}
				callback.onSuccess(res);
			}
		});
	}

	@Override
	public void remove(Integer key, final StoreResultCallback<Boolean> callback) {
		service.delete(key.intValue(), new VoidCallback() {
			
			@Override
			public void onFailure(DataServiceException error) {
				callback.onError(error);
			}
			
			@Override
			public void onSuccess() {
				callback.onSuccess(true);
			}
		});
	}

	@Override
	public void countAll(StoreResultCallback<Integer> callback) {
		callback.onError(null);
	}

	@Override
	public void query(String query, String index,
			StoreResultCallback<Map<Integer, ProjectObject>> callback) {
		callback.onError(null);
	}

}