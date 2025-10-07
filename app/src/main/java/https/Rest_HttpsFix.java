package https;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpProtocolParams;

public class Rest_HttpsFix {

	public static DefaultHttpClient GetHttpsSupportedHttpClientV2() {
		HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

		DefaultHttpClient client = new DefaultHttpClient();

		// Set custom User-Agent
		HttpProtocolParams.setUserAgent(client.getParams(), "Trackify Mobile App Android");

		SchemeRegistry registry = new SchemeRegistry();

		registry.register(new Scheme("https", new TlsSniSocketFactory(), 443));

		SingleClientConnManager mgr = new SingleClientConnManager(client.getParams(), registry);
		DefaultHttpClient httpClient = new DefaultHttpClient(mgr, client.getParams());

		// Set custom User-Agent for the final client as well
		HttpProtocolParams.setUserAgent(httpClient.getParams(), "Trackify Mobile App Android");

		// Set verifier
		HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

		return httpClient;
	}
}
