package common;

import eu.trackify.net.R;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import common.CountingInputStreamEntity.IUploadListener;
import https.Rest_HttpsFix;

public class Rest {

    private static volatile boolean isReAuthenticating = false;

    private static HashMap<String, String> GetStaticHeaders() {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("app-name", App.Object.getString(R.string.app_name));
        headers.put("api-key", (App.CurrentUser == null ? "" : App.CurrentUser.auth_key));
        return headers;
    }

    private static boolean isUnauthorizedResponse(String response) {
        if (response == null) return false;
        String lower = response.toLowerCase();
        return lower.contains("unauthorized") || lower.contains("\"authenticated\":\"false\"");
    }

    private static synchronized boolean reAuthenticate() {
        if (isReAuthenticating) return false;
        isReAuthenticating = true;

        try {
            String username = AppModel.Object.GetVariable(AppModel.USER_KEY);
            String storedPassword = AppModel.Object.GetVariable(AppModel.PASS_KEY);
            String isEncrypted = AppModel.Object.GetVariable(AppModel.PASS_ENCRYPTED_KEY);

            if (AppModel.IsNullOrEmpty(username) || AppModel.IsNullOrEmpty(storedPassword)) {
                return false;
            }

            String password = null;
            if ("true".equals(isEncrypted)) {
                String deviceId = PasswordSecurityUtil.getDeviceId(AppModel.Object.context);
                password = PasswordSecurityUtil.decryptFromStorage(storedPassword, deviceId);
            } else {
                password = storedPassword;
            }

            if (AppModel.IsNullOrEmpty(password)) {
                return false;
            }

            // Perform synchronous login (we're already on a background thread)
            String url = Communicator.URL + "/app_login_auth.php";
            HashMap<String, Object> reqParams = new HashMap<String, Object>();
            reqParams.put("user", username);
            reqParams.put("pass", password);

            // Use PostReq directly to avoid re-auth loop
            String json = PostReqRaw(url, reqParams);
            UserRef user = new Gson().fromJson(json, UserRef.class);

            if (user != null && !AppModel.IsNullOrEmpty(user.authenticated)
                    && user.authenticated.equalsIgnoreCase("true")) {
                App.CurrentUser = user;
                AppModel.Object.SaveVariable(AppModel.AUTH_TOKEN, user.auth_key);
                AppModel.Object.SaveVariable(AppModel.USER_CACHE_KEY, new Gson().toJson(user));
                return true;
            }

            return false;
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "Rest::reAuthenticate");
            return false;
        } finally {
            isReAuthenticating = false;
        }
    }

    /**
     * Raw POST without re-auth logic, used by reAuthenticate() to avoid infinite loops.
     */
    private static String PostReqRaw(String url, HashMap<String, Object> reqParams) throws Exception {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(reqParams.size());
        for (String key : reqParams.keySet()) {
            nameValuePairs.add(new BasicNameValuePair(key, reqParams.get(key).toString()));
        }

        HttpClient httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2();
        HttpPost httppost = new HttpPost(url);

        InputStream inputStream = null;
        String result = "";

        try {
            for (String hKey : GetStaticHeaders().keySet()) {
                httppost.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse httpResponse = httpclient.execute(httppost);

            inputStream = httpResponse.getEntity().getContent();

            if (inputStream != null) {
                result = convertInputStreamToString(inputStream);
                StatusLine sl = httpResponse.getStatusLine();
                if (sl != null && sl.getStatusCode() != HttpStatus.SC_OK)
                    throw new HttpResponseException(sl.getStatusCode(), result);
            } else
                throw new Exception("Unexpected Error!");
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (httpclient != null)
                httpclient.getConnectionManager().shutdown();
        }

        return result;
    }

    public static String UploadFile(String url, HashMap<String, Object> reqParams, File file, final IUploadListener listener) throws Exception {
        String jsonrtn = UploadFileRaw(url, reqParams, file, listener);

        // Check for unauthorized response and retry with re-auth
        if (isUnauthorizedResponse(jsonrtn) && !isReAuthenticating) {
            if (reAuthenticate()) {
                jsonrtn = UploadFileRaw(url, reqParams, file, listener);
            }
        }

        boolean error = (!jsonrtn.equals("[]") && jsonrtn.replace("\"", "").length() > 1 && jsonrtn.replace("\"", "").substring(0, 2).equals("E:"));
        if (error)
            throw new CustomServerException(jsonrtn.replace("\"", "").substring(2));

        return jsonrtn;
    }

    private static String UploadFileRaw(String url, HashMap<String, Object> reqParams, File file, final IUploadListener listener) throws Exception {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String reqJson = gson.toJson(reqParams);

        HttpClient httpclient = null;
        String jsonrtn = "";
        try {
            httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2();

            HttpPost httppost = new HttpPost(url);

            // Add Class Object
            httppost.addHeader("DataItem", reqJson);

            for (String hKey : GetStaticHeaders().keySet()) {
                httppost.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            // Add Streaming File
            InputStreamEntity reqEntity = new InputStreamEntity(new FileInputStream(file), file.length());

            httppost.setEntity(reqEntity);
            HttpResponse response = httpclient.execute(httppost);
            jsonrtn = EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            throw e;
        } finally {
            if (httpclient != null)
                httpclient.getConnectionManager().shutdown();
        }

        return jsonrtn;
    }

    public static String Post(String url, HashMap<String, Object> reqParams) throws Exception {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String reqJson = gson.toJson(reqParams);

        int failedCounts = 0;
        String jsonrtn = "";
        while (failedCounts < 3) {
            try {
                jsonrtn = PostReq(url, reqParams);
                break;
            } catch (HttpResponseException hre) {
                // Handle HTTP 401 by re-authenticating
                if (hre.getStatusCode() == 401 && !isReAuthenticating) {
                    if (reAuthenticate()) {
                        jsonrtn = PostReq(url, reqParams);
                        break;
                    }
                }
                throw hre;
            } catch (Exception ex) {
                throw ex;
            }
        }

        // Check for unauthorized response in body and retry with re-auth
        if (isUnauthorizedResponse(jsonrtn) && !isReAuthenticating) {
            if (reAuthenticate()) {
                jsonrtn = PostReq(url, reqParams);
            }
        }

        boolean error = (!jsonrtn.equals("[]") && jsonrtn.replace("\"", "").length() > 1 && jsonrtn.replace("\"", "").substring(0, 2).equals("E:"));
        if (error)
            throw new CustomServerException(jsonrtn.replace("\"", "").substring(2));

        return jsonrtn;
    }

    private static String PostReq(String url, HashMap<String, Object> reqParams) throws Exception {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(reqParams.size());
        for (String key : reqParams.keySet()) {
            nameValuePairs.add(new BasicNameValuePair(key, reqParams.get(key).toString()));
        }

        // Create a new HttpClient and Post Header
        HttpClient httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2();
        HttpPost httppost = new HttpPost(url);

        InputStream inputStream = null;
        String result = "";

        try {

            for (String hKey : GetStaticHeaders().keySet()) {
                httppost.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            // Execute HTTP Post Request
            HttpResponse httpResponse = httpclient.execute(httppost);

            inputStream = httpResponse.getEntity().getContent();

            if (inputStream != null) {
                result = convertInputStreamToString(inputStream);
                StatusLine sl = httpResponse.getStatusLine();
                if (sl != null && sl.getStatusCode() != HttpStatus.SC_OK)
                    throw new HttpResponseException(sl.getStatusCode(), result);
            } else
                throw new Exception("Unexpected Error!");
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (httpclient != null)
                httpclient.getConnectionManager().shutdown();
        }

        return result;
    }

    public static String GET(String url) {
        String result = GETRaw(url);

        // Check for unauthorized response and retry with re-auth
        if (isUnauthorizedResponse(result) && !isReAuthenticating) {
            if (reAuthenticate()) {
                result = GETRaw(url);
            }
        }

        return result;
    }

    private static String GETRaw(String url) {
        InputStream inputStream = null;
        String result = "";

        url = url.replace(" ", "%20").replace("\n", "%5Cn");

        try {

            // create HttpClient
            HttpClient httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2(); // new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(url);

            for (String hKey : GetStaticHeaders().keySet()) {
                httpGet.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            // make GET request to the given URL
            HttpResponse httpResponse = httpclient.execute(httpGet);

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

            // convert inputstream to string
            if (inputStream != null)
                result = convertInputStreamToString(inputStream);

        } catch (Exception exp) {
            exp.printStackTrace();
        }

        return result;
    }

    public static InputStream GET_Stream(String url) {
        InputStream inputStream = null;
        String result = "";

        url = url.replace(" ", "%20").replace("\n", "%5Cn");

        try {

            // create HttpClient
            HttpClient httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2(); // new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(url);

            for (String hKey : GetStaticHeaders().keySet()) {
                httpGet.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            // make GET request to the given URL
            HttpResponse httpResponse = httpclient.execute(httpGet);

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

            // convert inputstream to string
            if (inputStream != null)
                return inputStream;

        } catch (Exception exp) {
            exp.printStackTrace();
        }

        return null;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while ((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    /**
     * POST JSON data with custom headers
     * @param url The URL to post to
     * @param jsonPayload The JSON string to send
     * @param customHeaders Additional headers to include
     * @return The server response
     */
    public static String PostJSON(String url, String jsonPayload, HashMap<String, String> customHeaders) throws Exception {
        String result;
        try {
            result = PostJSONRaw(url, jsonPayload, customHeaders);
        } catch (HttpResponseException hre) {
            if (hre.getStatusCode() == 401 && !isReAuthenticating) {
                if (reAuthenticate()) {
                    return PostJSONRaw(url, jsonPayload, customHeaders);
                }
            }
            throw hre;
        }

        // Check for unauthorized response in body and retry with re-auth
        if (isUnauthorizedResponse(result) && !isReAuthenticating) {
            if (reAuthenticate()) {
                result = PostJSONRaw(url, jsonPayload, customHeaders);
            }
        }

        return result;
    }

    private static String PostJSONRaw(String url, String jsonPayload, HashMap<String, String> customHeaders) throws Exception {
        HttpClient httpclient = Rest_HttpsFix.GetHttpsSupportedHttpClientV2();
        HttpPost httppost = new HttpPost(url);

        InputStream inputStream = null;
        String result = "";

        try {
            // Add default headers
            for (String hKey : GetStaticHeaders().keySet()) {
                httppost.addHeader(hKey, GetStaticHeaders().get(hKey));
            }

            // Add custom headers
            if (customHeaders != null) {
                for (String hKey : customHeaders.keySet()) {
                    httppost.setHeader(hKey, customHeaders.get(hKey));
                }
            }

            // Set JSON content
            httppost.setEntity(new org.apache.http.entity.StringEntity(jsonPayload, "UTF-8"));

            // Execute HTTP Post Request
            HttpResponse httpResponse = httpclient.execute(httppost);

            inputStream = httpResponse.getEntity().getContent();

            if (inputStream != null) {
                result = convertInputStreamToString(inputStream);
                StatusLine sl = httpResponse.getStatusLine();
                if (sl != null && sl.getStatusCode() != HttpStatus.SC_OK)
                    throw new HttpResponseException(sl.getStatusCode(), result);
            } else {
                throw new Exception("Unexpected Error!");
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (httpclient != null)
                httpclient.getConnectionManager().shutdown();
        }

        return result;
    }
}
