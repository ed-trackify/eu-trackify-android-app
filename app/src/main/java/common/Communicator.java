package common;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.app.DownloadManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import eu.trackify.net.R;
import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

import common.CountingInputStreamEntity.IUploadListener;
import common.PendingRequestsLoader.PendingRequestItem;
import common.PendingRequestsLoader.PostParamItem;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class Communicator {

    public interface IServerResponse {
        public void onCompleted(boolean success, String messageToShow, Object... objs);
    }

    public final static String URL = "https://eu.trackify.net/api";

    public final static String SYNC_MSG = "Update saved locally. Will synchronize with server when you regain internet connection"; // "Internet connection not available, Data saved to sync later";

    public static void SubmitSignature(final String base64String, final String fullName, final String signatureId, String nullablePin, final int status, final IServerResponse callback) {

        final String url = URL + "/upload_signature_v2.php";

        final HashMap<String, Object> reqParams = new HashMap<String, Object>();
        reqParams.put("user_id", App.CurrentUser.user_id);
        reqParams.put("img_string", base64String);
        reqParams.put("shipment_id", signatureId);
        reqParams.put("status", status);
        reqParams.put("full_name", fullName);

        if (nullablePin != null) {
            reqParams.put("pin", nullablePin);

            if (!AppModel.Object.IsNetworkAvailable(false)) {
                callback.onCompleted(false, "Internet connection not available");
                return;
            }
        } else {
            if (!AppModel.Object.IsNetworkAvailable(false)) {
                callback.onCompleted(false, SYNC_MSG);
                AppModel.Pendings.InsertPost(url, reqParams);
                return;
            }
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String json = Rest.Post(url, reqParams);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SubmitSignature");
                }
            }
        })).start();
    }

    public static void Login(final String username, final String password, final IServerResponse callback, Runnable networkIssueCallback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            if (networkIssueCallback != null)
                networkIssueCallback.run();
            else
                callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/app_login_auth.php";

                    HashMap<String, Object> reqParams = new HashMap<String, Object>();
                    reqParams.put("user", username);
                    reqParams.put("pass", password); // Plain text password - WordPress handles hashing server-side

                    String json = Rest.Post(url, reqParams);
                    UserRef obj = new Gson().fromJson(json, UserRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::Login");
                }
            }
        })).start();
    }

    public static void SendDistributorRequest(final String key, final String status, final int statusId, final String comments, final IServerResponse callback) {
        String url = URL + "/update_status.php?key=[K]&user=[U]&status=[S]&status_id=[SID]";
        url = url.replace("[K]", key);
        url = url.replace("[U]", App.CurrentUser.user);
        url = url.replace("[S]", status);
        url = url.replace("[SID]", "" + statusId);
        if (comments != null)
            url += "&comment=" + comments;

        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, SYNC_MSG);
            AppModel.Pendings.InsertGet(url);
            return;
        }

        final String rUrl = url;
        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String json = Rest.GET(rUrl);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendDistributorRequest");
                }
            }
        })).start();
    }

    public static void SendWarehouseManager(final String key, final boolean isStockIn, final int quantity, final Integer statusId, final IServerResponse callback) {
        String url = URL + "/update_stock.php?dir=[S]&sku=[K]&qty=[Q]&user=[U]";
        url = url.replace("[K]", key);
        url = url.replace("[U]", App.CurrentUser.user);
        url = url.replace("[Q]", "" + quantity);
        url = url.replace("[S]", isStockIn ? "in" : "out");
        if (statusId != null)
            url += "&status_id=" + statusId;

        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, SYNC_MSG);
            AppModel.Pendings.InsertGet(url);
            return;
        }

        final String rUrl = url;
        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String json = Rest.GET(rUrl);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendWarehouseManager");
                }
            }
        })).start();
    }

    public static void SendPacker(final String key, final boolean isReady, final int statusId, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/update_status.php?status=[S]&key=[K]&user=[U]&status_id=[SID]";
                    url = url.replace("[K]", key);
                    url = url.replace("[U]", App.CurrentUser.user);
                    url = url.replace("[S]", isReady ? "ready" : "returned");
                    url = url.replace("[SID]", "" + statusId);

                    String json = Rest.GET(url);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendPacker");
                }
            }
        })).start();
    }

    public static void SendWarehouseAdmin(final String key, final IServerResponse callback) {
        SendWarehouseManager(key, false, 1, null, callback);
    }

    // public static void LoadShipments(final Integer statusId, final IServerResponse callback) {
    // if (!AppModel.Object.IsNetworkAvailable(false)) {
    // callback.onCompleted(false, "Internet connection not available");
    // return;
    // }
    //
    // (new Thread(new Runnable() {
    // @Override
    // public void run() {
    //
    // try {
    // String url = URL + "/get_driver_shipments.php?user=[U]";
    // url = url.replace("[U]", App.CurrentUser.user);
    // if (statusId != null)
    // url += "&status_id=" + statusId;
    //
    // String json = Rest.GET(url);
    // boolean hasError = !json.startsWith("["); // Because error is object while shipments is list.
    // if (hasError) {
    // ShipmentError error = new Gson().fromJson(json, ShipmentError.class);
    // callback.onCompleted(false, error.response_txt);
    // } else {
    // List<ShipmentItem> response = AppModel.JsonToArrayList(json, ShipmentItem[].class);
    // callback.onCompleted(true, null, response);
    // }
    // } catch (Exception ex) {
    // callback.onCompleted(false, "Something went wrong, Please try again");
    // AppModel.ApplicationError(ex, "Communicator::LoadShipments");
    // }
    // }
    // })).start();
    // }
    //
    // public static void GetShipmentDetail(final String shipmentId, final IServerResponse callback) {
    // if (!AppModel.Object.IsNetworkAvailable(false)) {
    // callback.onCompleted(false, "Internet connection not available");
    // return;
    // }
    //
    // (new Thread(new Runnable() {
    // @Override
    // public void run() {
    //
    // try {
    // String url = URL + "/get_shipment_info.php?shipment_id=[S]";
    // url = url.replace("[S]", "" + shipmentId);
    //
    // String json = Rest.GET(url);
    // List<ShipmentItemDetail> response = AppModel.JsonToArrayList(json, ShipmentItemDetail[].class);
    // if (response.size() > 0)
    // callback.onCompleted(true, null, response.get(0));
    // else
    // callback.onCompleted(false, "No detail found, Please try again");
    // } catch (Exception ex) {
    // callback.onCompleted(false, "Something went wrong, Please try again");
    // AppModel.ApplicationError(ex, "Communicator::GetShipmentDetail");
    // }
    // }
    // })).start();
    // }
    //
    // public static void GetNotes(final String shipmentId, final IServerResponse callback) {
    // if (!AppModel.Object.IsNetworkAvailable(false)) {
    // callback.onCompleted(false, "Internet connection not available");
    // return;
    // }
    //
    // (new Thread(new Runnable() {
    // @Override
    // public void run() {
    //
    // try {
    // String url = URL + "/get_shipment_comments.php?shipment_id=[S]";
    // url = url.replace("[S]", "" + shipmentId);
    //
    // String json = Rest.GET(url);
    // List<NoteItem> response = AppModel.JsonToArrayList(json, NoteItem[].class);
    // callback.onCompleted(true, null, response);
    // } catch (Exception ex) {
    // callback.onCompleted(false, "Something went wrong, Please try again");
    // AppModel.ApplicationError(ex, "Communicator::GetNotes");
    // }
    // }
    // })).start();
    // }

    public static void LoadShipmentsWithDetails(final ShipmentsType type, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/" + (type == ShipmentsType.MyShipments ? "get_driver_shipments_v2" : (type == ShipmentsType.Returns ? "get_driver_returns" : "get_driver_non_reconciled_v2")) + ".php?user=[U]";
                    url = url.replace("[U]", App.CurrentUser.user);

                    String json = Rest.GET(url);
                    boolean hasError = !json.contains("shipments"); // Because error is object while shipments is list.
                    if (hasError) {
                        ShipmentError error = new Gson().fromJson(json, ShipmentError.class);
                        callback.onCompleted(false, error.response_txt);
                    } else {
                        ShipmentResponse response = new Gson().fromJson(json, ShipmentResponse.class);
                        callback.onCompleted(true, null, response);
                    }
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::LoadShipmentsWithDetails");
                }
            }
        })).start();
    }

    public static void SendShipmentComments_OLD(final String shipmentId, final String comments, final IServerResponse callback) {
        String url = URL + "/add_shipment_comment.php?user_id=[UID]&shipment_id=[S]&comment=[C]";
        url = url.replace("[UID]", "" + App.CurrentUser.user_id);
        url = url.replace("[S]", "" + shipmentId);
        url = url.replace("[C]", comments);

        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, SYNC_MSG);
            AppModel.Pendings.InsertGet(url);
            return;
        }

        final String rUrl = url;
        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String json = Rest.GET(rUrl);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendShipmentComments");
                }
            }
        })).start();
    }

    public static void SendShipmentComments(final String shipmentId, final String noteType, final String comments, final IServerResponse callback) {

        final String url = URL + "/add_shipment_comment_v2.php";

        final HashMap<String, Object> reqParams = new HashMap<String, Object>();
        reqParams.put("user_id", App.CurrentUser.user_id);
        reqParams.put("shipment_id", shipmentId);
        reqParams.put("comment", comments);
        reqParams.put("note_type", noteType);

        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, SYNC_MSG);
            AppModel.Pendings.InsertPost(url, reqParams);
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String json = Rest.Post(url, reqParams);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendShipmentComments");
                }
            }
        })).start();
    }

    public static void GetExpenseTypes(final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/expense_get_types.php";

                    String json = Rest.GET(url);
                    List<ExpenseType> response = AppModel.JsonToArrayList(json, ExpenseType[].class);
                    callback.onCompleted(true, null, response);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::GetExpenseTypes");
                }
            }
        })).start();
    }

    public static void AddExpense(final String expense_type_id, final String amount, final String desc, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/expense_process_add.php?amount=[A]&response_type=[RT]&expense_type_id=[ET]&user_id=[UID]&description=[D]";
                    url = url.replace("[UID]", "" + App.CurrentUser.user_id);
                    url = url.replace("[A]", "" + amount);
                    url = url.replace("[RT]", "2");
                    url = url.replace("[ET]", "" + expense_type_id);
                    url = url.replace("[D]", desc);

                    String json = Rest.GET(url);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::AddExpense");
                }
            }
        })).start();
    }

    public static void GetSettings(final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        try {
            String url = URL + "/prober.php";

            HashMap<String, Object> reqParams = new HashMap<String, Object>();
            reqParams.put("user", App.CurrentUser.user);
            reqParams.put("gps_tracking", GPS.IsConnected ? "on" : "off");

            String json = Rest.Post(url, reqParams);
            SettingsRef[] obj = new Gson().fromJson(json, SettingsRef[].class);
            callback.onCompleted(true, null, obj[0]);
        } catch (Exception ex) {
            callback.onCompleted(false, "Something went wrong, Please try again");
            AppModel.ApplicationError(ex, "Communicator::GetSettings");
        }
    }

    public static void SendMassUpdate(final String key, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/mass_update_status.php";
                    HashMap<String, Object> reqParams = new HashMap<String, Object>();
                    reqParams.put("user_id", App.CurrentUser.user_id);
                    reqParams.put("status_id", "-1");
                    reqParams.put("tracking_id", key);

                    String json = Rest.Post(url, reqParams);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendMassUpdate");
                }
            }
        })).start();
    }

    public static void UpdateGps(final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        try {
            String url = URL + "/gps_update.php";

            HashMap<String, Object> reqParams = new HashMap<String, Object>();
            reqParams.put("user", App.CurrentUser.user);
            reqParams.put("lat", GPS.Lat);
            reqParams.put("long", GPS.Lon);

            String json = Rest.Post(url, reqParams);
            KeyRef obj = new Gson().fromJson(json, KeyRef.class);
            callback.onCompleted(true, null, obj);
        } catch (Exception ex) {
            callback.onCompleted(false, "Something went wrong, Please try again");
            AppModel.ApplicationError(ex, "Communicator::UpdateGps");
        }
    }

    public static void SendSmsStatus(final String shipmentId) {
        // if (!AppModel.Object.IsNetworkAvailable(false)) {
        // callback.onCompleted(false, "Internet connection not available");
        // return;
        // }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/sms_sent.php";
                    HashMap<String, Object> reqParams = new HashMap<String, Object>();
                    reqParams.put("shipment_id ", shipmentId);
                    reqParams.put("user_id", App.CurrentUser.user_id);

                    String json = Rest.Post(url, reqParams);
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    // callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    // callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::SendSmsStatus");
                }
            }
        })).start();
    }

    public static void LogSmsToBackend(final String shipmentId, final String smsText, 
                                      final String receiverPhone, final int statusId, 
                                      final boolean hasCod, final double codAmount) {
        final String url = URL + "/sms/app_auto_sms.php";
        
        final HashMap<String, Object> reqParams = new HashMap<String, Object>();
        reqParams.put("shipment_id", shipmentId);
        reqParams.put("sms_text", smsText);
        reqParams.put("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        reqParams.put("user_id", App.CurrentUser.user_id);
        reqParams.put("receiver_phone", receiverPhone);
        reqParams.put("status_id", statusId);
        reqParams.put("has_cod", hasCod);
        reqParams.put("cod_amount", codAmount);
        
        // Send in background thread - no need to block UI for logging
        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Rest.Post automatically adds headers:
                    // - "api-key": App.CurrentUser.auth_key
                    // - "app-name": App Name
                    String json = Rest.Post(url, reqParams);
                    // Log success silently
                } catch (Exception ex) {
                    // Log failure silently - don't interrupt SMS flow
                    AppModel.ApplicationError(ex, "Communicator::LogSmsToBackend");
                }
            }
        })).start();
    }

    public static boolean SendPending(PendingRequestItem item) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            return false;
        }

        try {
            String url = item.Request;

            if (item.IsGetRequest)
                Rest.GET(url);
            else {
                HashMap<String, Object> reqParams = new HashMap<String, Object>();
                for (PostParamItem pi : item.Params) {
                    reqParams.put(pi.Param, pi.Value);
                }

                Rest.Post(url, reqParams);
            }
            return true;
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "Communicator::SendPending");
            return false;
        }
    }

    public static void UploadShipmentPicture(final String shipmentId, final String comments, final File file, final IServerResponse callback, final IUploadListener listener) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    String url = URL + "/upload_image.php";

                    HashMap<String, Object> reqParams = new HashMap<String, Object>();
                    reqParams.put("ShipmentId", shipmentId);
                    reqParams.put("DriverId", App.CurrentUser.user_id);
                    reqParams.put("Comments", comments);

                    String json = Rest.UploadFile(url, reqParams, file, listener);
                    // String result = new Gson().fromJson(json, String.class); // boolean
                    // if (AppModel.IsNullOrEmpty(result))
                    // throw new Exception("Null Response");
                    KeyRef obj = new Gson().fromJson(json, KeyRef.class);
                    callback.onCompleted(true, null, obj.updated);
                } catch (CustomServerException ex) {
                    callback.onCompleted(false, AppModel.GetExceptionMessage(ex));
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::UploadShipmentPicture");
                }
            }
        })).start();
    }

    public static void GetPrintImage(final String trackingId, final IServerResponse callback) {
        String url = URL + "/print_app_label.php?tracking_ids=";

        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        final String rUrl = url;
        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    InputStream in = Rest.GET_Stream(rUrl);

//                    File outputFile = new File(AppModel.GetAppFolder(AppModel.AppFolderType.Images), UUID.randomUUID().toString() + ".png");
//                    FileOutputStream output = new FileOutputStream(outputFile);
//                    byte[] buffer = new byte[1024];
//                    for (int n = in.read(buffer); n >= 0; n = in.read(buffer))
//                        output.write(buffer, 0, n);
//                    output.flush();
//                    output.close();
//                    in.close();
//                    Bitmap bmp = BitmapFactory.decodeFile(outputFile.getAbsolutePath());

                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    in.close();
                    callback.onCompleted(true, null, bmp);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::GetPrintImage");
                }
            }
        })).start();
    }

    public static void CreateReturnShipment(final String trackingId, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL + "/create_return_shipment.php";
                    HashMap<String, Object> reqParams = new HashMap<String, Object>();
                    reqParams.put("tracking_id ", trackingId);

                    String json = Rest.Post(url, reqParams);
                    ReturnShipment obj = new Gson().fromJson(json, ReturnShipment.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::CreateReturnShipment");
                }
            }
        })).start();
    }

    public static void UploadProfilePicture(final String base64Image, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = URL + "/profile/upload_picture.php";

                    JSONObject params = new JSONObject();
                    params.put("user_id", App.CurrentUser.user_id);
                    params.put("image", base64Image);
                    params.put("format", "jpg");

                    String json = Rest.PostJSON(url, params.toString(), null);
                    JSONObject response = new JSONObject(json);

                    if (response.has("success") && response.getBoolean("success")) {
                        String imageUrl = response.optString("image_url", "");
                        callback.onCompleted(true, "Profile picture uploaded", imageUrl);
                    } else {
                        String error = response.optString("error", "Upload failed");
                        callback.onCompleted(false, error);
                    }
                } catch (Exception ex) {
                    callback.onCompleted(false, "Failed to upload profile picture");
                    AppModel.ApplicationError(ex, "Communicator::UploadProfilePicture");
                }
            }
        })).start();
    }

    public static void CheckAppUpdate(final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String url = URL.replace("/api", "/app") + "/version_check.php";

                    String json = Rest.GET(url);
                    AppUpdate obj = new Gson().fromJson(json, AppUpdate.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::CheckAppUpdate");
                }
            }
        })).start();
    }

    public static long DownloadFileUsingDM(String fileName, String url) {
        // fileName -> fileName with extension
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading " + App.Object.getString(R.string.app_name) + " Update")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);
        DownloadManager downloadManager = (DownloadManager) App.Object.getSystemService(DOWNLOAD_SERVICE);
        return downloadManager.enqueue(request);
    }

    public static void DownloadFileUsingStream(final String fileName, final String urlString, final IServerResponse response) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    // Use public downloads directory for better compatibility
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    File file = new File(downloadDir, fileName);
                    if (file.exists()) {
                        file.delete();
                    }

                    java.net.URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();

                    // Check for valid response
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        final String errorMsg = "Server error: HTTP " + connection.getResponseCode();
                        response.onCompleted(false, errorMsg, null);
                        return;
                    }

                    // Get file size for progress tracking
                    int fileLength = connection.getContentLength();

                    // Download the file with streaming
                    input = connection.getInputStream();
                    output = new FileOutputStream(file);

                    byte[] buffer = new byte[4096];
                    long total = 0;
                    int count;
                    int lastProgress = 0;

                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        output.write(buffer, 0, count);

                        // Calculate and update progress
                        if (fileLength > 0) {
                            int progress = (int) (total * 100 / fileLength);
                            if (progress != lastProgress && progress % 10 == 0) {
                                lastProgress = progress;
                                final int progressToShow = progress;
                                App.Object.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        App.SetDownloadProgress(progressToShow);
                                    }
                                });
                            }
                        }
                    }

                    output.flush();

                    // Verify file was created and has content
                    if (!file.exists() || file.length() == 0) {
                        response.onCompleted(false, "Download completed but file is empty", null);
                        return;
                    }

                    response.onCompleted(true, null, file);
                } catch (java.net.SocketTimeoutException ex) {
                    AppModel.ApplicationError(ex, "App:DownloadFileUsingStream::Timeout");
                    response.onCompleted(false, "Download timeout - please check your internet connection", null);
                } catch (java.io.IOException ex) {
                    AppModel.ApplicationError(ex, "App:DownloadFileUsingStream::IO");
                    response.onCompleted(false, "Network error: " + ex.getMessage(), null);
                } catch (Exception ex) {
                    AppModel.ApplicationError(ex, "App:DownloadFileUsingStream:: " + urlString);
                    response.onCompleted(false, "Download failed: " + ex.getMessage(), null);
                } finally {
                    try {
                        if (output != null) output.close();
                        if (input != null) input.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
            }
        }).start();
    }

    public static void GetSMSHistory(final String shipmentId, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = URL.replace("/api", "") + "/api/sms/get_sms_history.php?shipment_id=" + shipmentId;
                    String json = Rest.GET(url);
                    UserDistributorSMSHistoryFragment.SMSHistoryResponse obj = new Gson().fromJson(json, UserDistributorSMSHistoryFragment.SMSHistoryResponse.class);
                    callback.onCompleted(true, null, obj);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Something went wrong, Please try again");
                    AppModel.ApplicationError(ex, "Communicator::GetSMSHistory");
                }
            }
        })).start();
    }

    public static void ChangePassword(final String currentPassword, final String newPassword, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        if (App.CurrentUser == null || App.CurrentUser.user_id == 0) {
            callback.onCompleted(false, "User session not valid");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Prepare the request data
                    // Passwords are sent as plain text - WordPress handles hashing server-side
                    HashMap<String, Object> data = new HashMap<>();
                    data.put("user_id", String.valueOf(App.CurrentUser.user_id));
                    data.put("current_password", currentPassword);
                    data.put("new_password", newPassword);

                    String json = Rest.Post(URL + "/change_password.php", data);

                    // Parse response
                    JSONObject response = new JSONObject(json);
                    boolean success = response.optBoolean("success", false);
                    String message = response.optString("message", "");

                    callback.onCompleted(success, message);
                } catch (Exception ex) {
                    callback.onCompleted(false, "Failed to change password. Please try again.");
                    AppModel.ApplicationError(ex, "Communicator::ChangePassword");
                }
            }
        })).start();
    }

    /**
     * Submit SMS replies to the server
     * Supports both single and batch submissions
     */
    public static void SubmitSMSReplies(final List<SMSReply> replies, final IServerResponse callback) {
        if (!AppModel.Object.IsNetworkAvailable(false)) {
            callback.onCompleted(false, "Internet connection not available");
            return;
        }

        if (App.CurrentUser == null || App.CurrentUser.auth_key == null) {
            callback.onCompleted(false, "User session not valid");
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = URL.replace("/api", "") + "/api/sms/reply_handler.php";

                    // Convert replies to JSON
                    String jsonPayload;
                    if (replies.size() == 1) {
                        // Single reply - send as object
                        jsonPayload = new Gson().toJson(replies.get(0));
                    } else {
                        // Multiple replies - send as array
                        jsonPayload = new Gson().toJson(replies);
                    }

                    // Create custom headers for this request
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("X-API-Key", "osafu2379jsaf"); // Fixed API key as specified

                    // Send JSON payload
                    String response = Rest.PostJSON(url, jsonPayload, headers);

                    // Parse response
                    JSONObject jsonResponse = new JSONObject(response);
                    String status = jsonResponse.optString("status", "error");
                    String message = jsonResponse.optString("message", "");

                    boolean success = "success".equalsIgnoreCase(status);

                    if (success) {
                        // Log successful submission
                        int processedCount = jsonResponse.optInt("processed_count", replies.size());
                        AppModel.ApplicationError(null, "SMS Replies: Successfully submitted " +
                            processedCount + " replies to server");
                    }

                    callback.onCompleted(success, message, jsonResponse);

                } catch (Exception ex) {
                    callback.onCompleted(false, "Failed to submit SMS replies");
                    AppModel.ApplicationError(ex, "Communicator::SubmitSMSReplies");
                }
            }
        })).start();
    }

    /**
     * Send a single SMS reply to the server
     */
    public static void SendSMSReply(final SMSReplyMonitor.SMSReply reply, final IServerResponse callback) {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = new JSONObject();
                    params.put("phone_from", reply.phoneFrom);
                    params.put("message", reply.message);
                    params.put("received_at", reply.receivedTimestamp);
                    if (reply.shipmentId != null) {
                        params.put("shipment_id", reply.shipmentId);
                    }

                    String url = URL + "/sms/reply";
                    String response = Rest.PostJSON(url, params.toString(), null);

                    boolean success = false;
                    String message = "";

                    if (response != null) {
                        JSONObject jsonResponse = new JSONObject(response);
                        success = jsonResponse.optBoolean("success", false);
                        message = jsonResponse.optString("message", "");
                    }

                    callback.onCompleted(success, message);

                } catch (Exception ex) {
                    callback.onCompleted(false, "Failed to send SMS reply");
                    AppModel.ApplicationError(ex, "Communicator::SendSMSReply");
                }
            }
        })).start();
    }
}
