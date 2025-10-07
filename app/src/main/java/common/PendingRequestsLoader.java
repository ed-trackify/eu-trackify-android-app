package common;

import android.content.ContentValues;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import common.DatabaseManager.TableType;

public class PendingRequestsLoader {

	public class PendingRequestItem {
		public int ID;
		public String Request;
		public String Timestamp; // 2017-11-17 14:11:20
		public boolean IsGetRequest;
		public boolean Uploaded;

		List<PostParamItem> Params = new ArrayList<PostParamItem>();
	}

	public class PostParamItem {
		public int ID;
		public int PendingRequestID;
		public String Param;
		public String Value;
	}

	public List<PendingRequestItem> GetPendings() {
		List<PendingRequestItem> All = new ArrayList<PendingRequestItem>();
		try {
			ArrayList<HashMap<String, String>> rows = AppModel.Object.db.SelectFormatted(TableType.PendingRequests, null, null, null);
			for (HashMap<String, String> row : rows) {
				PendingRequestItem item = new PendingRequestItem();
				item.ID = Integer.parseInt(row.get("ID"));
				item.Request = row.get("Request");
				item.Timestamp = row.get("Timestamp");
				item.IsGetRequest = row.get("IsGetRequest").equalsIgnoreCase("1") || row.get("IsGetRequest").equalsIgnoreCase("true");
				item.Uploaded = row.get("Uploaded").equalsIgnoreCase("1") || row.get("Uploaded").equalsIgnoreCase("true");

				if (!item.IsGetRequest) {
					ArrayList<HashMap<String, String>> detRows = AppModel.Object.db.SelectFormatted(TableType.PostParams, null, "PendingRequestID = " + item.ID, null);
					for (HashMap<String, String> dr : detRows) {
						PostParamItem pi = new PostParamItem();
						pi.ID = Integer.parseInt(dr.get("ID"));
						pi.PendingRequestID = Integer.parseInt(dr.get("PendingRequestID"));
						pi.Param = dr.get("Param");
						pi.Value = dr.get("Value");
						item.Params.add(pi);
					}
				}

				All.add(item);
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "PendingRequestsLoader::GetPendings");
		}
		return All;
	}

	public void InsertGet(String url) {
		try {
			ContentValues cv = new ContentValues();
			cv.put("ID", AppModel.Object.db.GetTableMaxID(TableType.PendingRequests, null) + 1);
			cv.put("Request", url);
			cv.put("Timestamp", AppModel.SERVER_FORMAT.format(new Date()));
			cv.put("IsGetRequest", true);
			cv.put("Uploaded", false);
			AppModel.Object.db.Insert(TableType.PendingRequests, cv);
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "PendingRequestsLoader::InsertGet");
		}
	}

	public void InsertPost(String url, HashMap<String, Object> params) {
		try {
			int id = AppModel.Object.db.GetTableMaxID(TableType.PendingRequests, null) + 1;

			ContentValues cv = new ContentValues();
			cv.put("ID", id);
			cv.put("Request", url);
			cv.put("Timestamp", AppModel.SERVER_FORMAT.format(new Date()));
			cv.put("IsGetRequest", false);
			cv.put("Uploaded", false);
			AppModel.Object.db.Insert(TableType.PendingRequests, cv);

			for (String key : params.keySet()) {
				cv = new ContentValues();
				cv.put("ID", AppModel.Object.db.GetTableMaxID(TableType.PostParams, null) + 1);
				cv.put("PendingRequestID", id);
				cv.put("Param", key);
				cv.put("Value", params.get(key).toString());
				AppModel.Object.db.Insert(TableType.PostParams, cv);
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "PendingRequestsLoader::InsertPost");
		}
	}

	public void MarkUploaded(int reqId) {
		try {
			AppModel.Object.db.Delete(TableType.PendingRequests, "ID = " + reqId);
			AppModel.Object.db.Delete(TableType.PostParams, "PendingRequestID = " + reqId);
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "PendingRequestsLoader::MarkUploaded");
		}
	}
}
