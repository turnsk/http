package sk.turn.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class representing a single HTTP request. This class wraps the Java URLConnection class for easy use in most common HTTP(S) scenarios.
 */
public class Http {

	/**
	 * A listener interface to receive the result of the asynchronous HTTP call.
	 * 
	 * @see Http#send(Listener)
	 */
	public interface Listener {
		/**
		 * Is called when using the asynchronous version of {@link Http#send(Listener)}
		 * 
		 * @param http The Http object containing the response fields.
		 * 
		 * @see Http#getResponseCode()
		 * @see Http#getResponseMessage()
		 * @see Http#getResponseHeader(String)
		 * @see Http#getResponseData()
		 * @see Http#getResponseString()
		 */
		void onHttpResult(Http http);
	}

	/**
	 * Constant for HTTP get method.
	 */
	public static final String GET = "GET";
	/**
	 * Constant for HTTP post method.
	 */
	public static final String POST = "POST";
	private static String ENCODING = "utf-8";
	private static ExecutorService executor;

	/**
	 * Same as {@link java.net.URLEncoder#encode(String, String)} using the UTF-8 encoding and no exception throwing.
	 */
	public static String urlEncode(String str) {
		try { return URLEncoder.encode(str, ENCODING); }
		catch (UnsupportedEncodingException e) { return str; }
	}

	/**
	 * Same as {@link java.net.URLDecoder#decode(String, String)} using the UTF-8 encoding and no exception throwing.
	 */
	public static String urlDecode(String str) {
		try { return URLDecoder.decode(str, ENCODING); }
		catch (UnsupportedEncodingException e) { return str; }
	}

	private final String url;
	private final String method;
	private final Map<String, String> headers;
	private final Map<String, String> params;
	private int timeoutConnect = -1;
	private int timeoutRead = -1;
	private byte[] requestData;
	private int responseCode;
	private String responseMessage;
	private Map<String, List<String>> responseHeaders;
	private byte[] responseData;

	/**
	 * Creates a HTTP object to an URL using a specific method.
	 */
	public Http(String url, String method) {
		this.url = url;
		this.method = method;
		headers = new LinkedHashMap<String, String>();
		params = new LinkedHashMap<String, String>();
	}

	/**
	 * Adds a HTTP request header. If a
	 * 
	 * @param key The request header name.
	 * @param value The request header value.
	 * @return This Http object for easy call chaining.
	 */
	public Http addHeader(String key, String value) {
		headers.put(key, value);
		return this;
	}

	/**
	 * Adds a request GET/POST parameter.
	 * 
	 * @param key The name of the parameter.
	 * @param value The value of the parameter, the value will be encoded when sending the request.
	 * @return This Http object for easy call chaining.
	 */
	public Http addParam(String key, String value) {
		params.put(key, value);
		return this;
	}

	/**
	 * Sets the data to send as HTTP post body.
	 * 
	 * @param data Raw HTTP post data.
	 * @return This Http object for easy call chaining.
	 */
	public Http setData(byte[] data) {
		requestData = data;
		return this;
	}

	/**
	 * Sets the string to send as HTTP post body.
	 * 
	 * @param data HTTP post data, will be converted to bytes using UTF-8 encoding.
	 * @return This Http object for easy call chaining.
	 */
	public Http setData(String data) {
		try { return setData(data.getBytes(ENCODING)); }
		catch (Exception e) { return this; }
	}

	/**
	 * Sets the connection timeout for this request in milliseconds.
	 * 
	 * @param ms Timeout in milliseconds.
	 * @return This Http object for easy call chaining.
	 */
	public Http setConnectTimeout(int ms) {
		timeoutConnect = ms;
		return this;
	}

	/**
	 * Sets the read timeout for this request in milliseconds.
	 * 
	 * @param ms Timeout in milliseconds.
	 * @return This Http object for easy call chaining.
	 */
	public Http setReadTimeout(int ms) {
		timeoutRead = ms;
		return this;
	}

	/**
	 * Sends the HTTP request synchronously.
	 * 
	 * @return This Http object for easy call chaining.
	 * @throws IOException When anything in the network communication goes wrong.
	 */
	public Http send() throws IOException {
		String params = null;
		if (this.params.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (String param : this.params.keySet()) {
				sb.append(param).append("=").append(Http.urlEncode(this.params.get(param))).append("&");
			}
			params = sb.substring(0, sb.length() - 1);
		}
		URL url = new URL(this.url + (method.equalsIgnoreCase(GET) && params != null ? "?" + params : ""));
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod(method);
		conn.setInstanceFollowRedirects(false);
		conn.setUseCaches(false);
		if (timeoutConnect != -1) {
			conn.setConnectTimeout(timeoutConnect);
		}
		if (timeoutRead != -1) {
			conn.setReadTimeout(timeoutRead);
		}
		for (String header : headers.keySet()) {
			conn.setRequestProperty(header, headers.get(header));
		}
		if (method.equalsIgnoreCase(POST)) {
			if (!headers.containsKey("Content-Type")) {
				conn.setRequestProperty("Content-Type", requestData != null ? "application/octet-stream" : "application/x-www-form-urlencoded");
			}
			if (requestData == null) {
				requestData = (params != null && params.length() > 0 ? params.getBytes(ENCODING) : new byte[0]);
			}
			conn.setRequestProperty("Content-Length", Integer.toString(requestData.length));
			conn.setDoOutput(true);
			conn.getOutputStream().write(requestData);
			conn.getOutputStream().flush();
		}
		responseCode = conn.getResponseCode();
		responseMessage = conn.getResponseMessage();
		responseHeaders = conn.getHeaderFields();
		String contentLength = getResponseHeader("Content-Length");
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(contentLength != null ? Integer.parseInt(contentLength) : 1024);
		byte[] b = new byte[1024];
		for (int read; (read = conn.getInputStream().read(b)) != -1;) {
			buffer.write(b, 0, read);
		}
		conn.disconnect();
		responseData = buffer.toByteArray();
		return this;
	}

	/**
	 * Sends the HTTP request asynchronously. A cached thread pool executor is used to spawn new or reuse threads 
	 * (see {@link java.util.concurrent.Executors#newCachedThreadPool()}). In case the connection fails with an 
	 * {@link java.io.IOException}, the {@link Http#getResponseCode()} is set to -1 and {@link Http#getResponseMessage()} 
	 * to {@link java.io.IOException#toString()}. The listener is called from the background thread, not from the thread 
	 * this method is called.
	 * 
	 * @param listener Callback interface to receive the {@link Listener#onHttpResult(Http)} notification.
	 * @return This Http object for easy call chaining.
	 */
	public Http send(final Listener listener) {
		if (executor == null) {
			executor = Executors.newCachedThreadPool();
		}
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					send();
				} catch (Exception e) {
					responseCode = -1;
					responseMessage = e.toString();
				}
				listener.onHttpResult(Http.this);
			}
		});
		return this;
	}

	/**
	 * Returns the HTTP response code.
	 * 
	 * @return HTTP response code, e.g. 200 for success.
	 */
	public int getResponseCode() { return responseCode; }

	/**
	 * Returns the HTTP response status message.
	 * 
	 * @return HTTP response status message.
	 */
	public String getResponseMessage() { return responseMessage; }

	/**
	 * Returns the first HTTP response header with the specified key or null if the header does not exist.
	 * 
	 * @return First HTTP response header value or null if none such header exists.
	 */
	public String getResponseHeader(String key) {
		return (responseHeaders == null || !responseHeaders.containsKey(key) ? null : responseHeaders.get(key).get(0));
	}

	/**
	 * Returns a list of HTTP response headers with the specified key.
	 * 
	 * @return List of HTTP response header values.
	 */
	public List<String> getResponseHeaders(String key) {
		return (responseHeaders == null ? new ArrayList<String>() : responseHeaders.get(key));
	}

	/**
	 * Returns the raw response data.
	 * 
	 * @return Raw response data.
	 */
	public byte[] getResponseData() { return responseData; }

	/**
	 * Returns the response data as a string.
	 * 
	 * @return Response data decoded to string using UTF-8 encoding.
	 */
	public String getResponseString() { try { return new String(responseData, ENCODING); } catch (Exception e) { return null; } }

}
