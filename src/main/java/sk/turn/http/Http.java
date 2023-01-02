package sk.turn.http;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class representing a single HTTP request. This class wraps the Java URLConnection class for easy use in most common HTTP(S) scenarios.
 */
public class Http implements Closeable {

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
		 * @see Http#getResponseStream()
		 * @see Http#writeResponseToStream(OutputStream)
		 * @see Http#writeResponseToFile(File)
		 */
		void onHttpResult(Http http);
	}

	/**
	 * A listener interface to receive the result of the asynchronous HTTP call.
	 *
	 * @see Http#send(Listener)
	 */
	public interface ProgressListener {
		/**
		 * Is called every time the amount of data downloaded (or uploaded) changes.
		 *
		 * @param bytesReceived Number of bytes already transmitted.
		 * @param bytesTotal Total number of bytes to transmit, may be -1 if Content-Length header is not set.
		 *
		 * @see Http#setProgressListener(ProgressListener)
		 * @see Http#getResponseData()
		 * @see Http#getResponseString()
		 * @see Http#writeResponseToStream(OutputStream)
		 * @see Http#writeResponseToFile(File)
		 */
		void onHttpProgress(int bytesReceived, int bytesTotal);
	}

	/**
	 * Constant for HTTP get method.
	 */
	public static final String GET = "GET";
	/**
	 * Constant for HTTP post method.
	 */
	public static final String POST = "POST";
	/**
	 * Constant for HTTP put method.
	 */
	public static final String PUT = "PUT";
	/**
	 * Constant for HTTP delete method.
	 */
	public static final String DELETE = "DELETE";
	private static String ENCODING = "utf-8";
	private static ExecutorService executor;
	private static final int BUFFER_SIZE = 8192;

	/**
	 * Same as {@link java.net.URLEncoder#encode(String, String)} using the UTF-8 encoding and no exception throwing.
	 * @param str The string to be URL-encoded.
	 * @return The URL-encoded string.
	 */
	public static String urlEncode(String str) {
		try { return URLEncoder.encode(str, ENCODING); }
		catch (UnsupportedEncodingException e) { return str; }
	}

	/**
	 * Same as {@link java.net.URLDecoder#decode(String, String)} using the UTF-8 encoding and no exception throwing.
	 * @param str The string to be URL-decoded.
	 * @return The URL-decoded string.
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
	private String proxyHost;
	private int proxyPort;
	private byte[] requestData;
	private InputStream inputStream;
	private boolean inputStreamCloseWhenRead;
	private int responseCode;
	private String responseMessage;
	private Map<String, List<String>> responseHeaders;
	private HttpURLConnection connection;
	private SSLContext tlsContext;
	private ProgressListener downloadProgressListener;
	private ProgressListener uploadProgressListener;

	/**
	 * Creates a HTTP object to an URL using a specific method.
	 * @param url The URL of the resource to load. Do not append GET parameters here, use rather {@link Http#addParam(String, String)} method.
	 * @param method The HTTP method to use, you may use {@link Http#GET}, {@link Http#POST}, {@link Http#PUT} or {@link Http#DELETE} helper fields.
	 */
	public Http(String url, String method) {
		this.url = url;
		this.method = method;
		headers = new LinkedHashMap<String, String>();
		params = new LinkedHashMap<String, String>();
	}

	/**
	 * Adds a HTTP request header. If a header with the key already exists, it is overwritten.
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
	 * @param key The name of the parameter.
	 * @param value The value of the parameter, the value will be encoded when sending the request.
	 * @return This Http object for easy call chaining.
	 */
	public Http addParam(String key, String value) {
		params.put(key, value);
		return this;
	}

	/**
	 * Sets the connection timeout for this request in milliseconds.
	 * @param ms Timeout in milliseconds.
	 * @return This Http object for easy call chaining.
	 */
	public Http setConnectTimeout(int ms) {
		timeoutConnect = ms;
		return this;
	}

	/**
	 * Sets the read timeout for this request in milliseconds.
	 * @param ms Timeout in milliseconds.
	 * @return This Http object for easy call chaining.
	 */
	public Http setReadTimeout(int ms) {
		timeoutRead = ms;
		return this;
	}

	/**
	 * Sets the proxy for this request.
	 * @param host The proxy host.
	 * @param port The proxy port.
	 * @return This Http object for easy call chaining.
	 */
	public Http setProxy(String host, int port) {
		proxyHost = host;
		proxyPort = port;
		return this;
	}

	/**
	 * Sets the data to send as HTTP post body.
	 * @param data Raw HTTP post data.
	 * @return This Http object for easy call chaining.
	 */
	public Http setData(byte[] data) {
		requestData = data;
		if (inputStream != null && inputStreamCloseWhenRead) {
			try { inputStream.close(); }
			catch (IOException e) { }
			inputStream = null;
		}
		return this;
	}

	/**
	 * Sets the string to send as HTTP post body.
	 * @param data HTTP post data, will be converted to bytes using UTF-8 encoding.
	 * @return This Http object for easy call chaining.
	 */
	public Http setData(String data) {
		try { return setData(data.getBytes(ENCODING)); }
		catch (Exception e) { return this; }
	}

	/**
	 * Sets the data to send as HTTP post body as an input stream. Note, that the input stream must be
	 * readable and only be closed after the actual request is sent, either synchronously (after calling {@link Http#send()})
	 * or asynchronously (in {@link Listener#onHttpResult(Http)} implementation).
	 * @param inputStream The input stream to read the data from.
	 * @param closeWhenRead Whether to close the input stream when the data has been read, otherwise
	 *                         it is your responsibility to close the data after {@link Http#send()} has been called
	 *                         or in {@link Listener#onHttpResult(Http)} implementation.
	 * @return This Http object for easy call chaining.
	 */
	public Http setData(InputStream inputStream, boolean closeWhenRead) {
		this.inputStream = inputStream;
		inputStreamCloseWhenRead = closeWhenRead;
		requestData = null;
		return this;
	}

	/**
	 * Sets the data to send as HTTP post body to be read from a file. Note, that the file must exist
	 * when the actual request is being sent, either synchronously (during calling {@link Http#send()})
	 * or asynchronously (before {@link Http.Listener#onHttpResult(Http)} is called).
	 * If header "Content-Length" is not already set, it will be set to the supplied file size in bytes.
	 * @param file A valid {@link java.io.File} reference.
	 * @return This Http object for easy call chaining.
	 * @throws IOException When the supplied file cannot be opened.
	 */
	public Http setData(File file) throws IOException {
		if (!file.exists() || !file.isFile()) {
			throw new IOException("Invalid file");
		}
		if (!headers.containsKey("Content-Length")) {
			addHeader("Content-Length", String.valueOf(file.length()));
		}
		return setData(new FileInputStream(file), true);
	}

	/**
	 * Sets the root certificate to be trusted (certificate pinning). When using this, the System certificate
	 * store is ignored and only this certificate is implicitly trusted.
	 * @param certificate A trusted certificate
	 * @return This Http object for easy call chaining.
	 */
	public Http setTrustedRoot(X509Certificate certificate) throws IllegalArgumentException {
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setCertificateEntry("ca", certificate);
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(keyStore);
			tlsContext = SSLContext.getInstance("TLS");
			tlsContext.init(null, tmf.getTrustManagers(), null);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
		return this;
	}

	/**
	 * Sends the HTTP request synchronously.
	 * @return This Http object for easy call chaining.
	 * @throws IOException When anything in the network communication goes wrong.
	 */
	public Http send() throws IOException {
		if (connection != null || responseCode != 0) {
			throw new IllegalStateException("The connection is in progress or has already been used, create a new instance.");
		}
		String params = null;
		if (this.params.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (String param : this.params.keySet()) {
				sb.append(param).append("=").append(Http.urlEncode(this.params.get(param))).append("&");
			}
			params = sb.substring(0, sb.length() - 1);
		}
		URL url = new URL(this.url + (method.equalsIgnoreCase(GET) && params != null ? "?" + params : ""));
		if (proxyHost == null || proxyHost.length() == 0 || proxyPort == 0) {
			connection = (HttpURLConnection) url.openConnection();
		} else {
			connection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
		}
		if (tlsContext != null) {
			if (connection instanceof HttpsURLConnection) {
				((HttpsURLConnection) connection).setSSLSocketFactory(tlsContext.getSocketFactory());
			} else {
				throw new IllegalStateException("Trusted root was set but no HTTPS was used.");
			}
		}
		connection.setRequestMethod(method);
		connection.setInstanceFollowRedirects(false);
		connection.setUseCaches(false);
		if (timeoutConnect != -1) {
			connection.setConnectTimeout(timeoutConnect);
		}
		if (timeoutRead != -1) {
			connection.setReadTimeout(timeoutRead);
		}
		for (String header : headers.keySet()) {
			connection.setRequestProperty(header, headers.get(header));
		}
		if (!method.equalsIgnoreCase(GET) && !method.equalsIgnoreCase(DELETE)) {
			if (!headers.containsKey("Content-Type")) {
				connection.setRequestProperty("Content-Type", requestData != null ? "application/octet-stream" : "application/x-www-form-urlencoded");
			}
			if (inputStream != null) {
				connection.setDoOutput(true);
				int contentLength = parseContentLength(headers.get("Content-Length"));
				copyStream(inputStream, connection.getOutputStream(), new CopyProgressListener(uploadProgressListener, contentLength));
				connection.getOutputStream().flush();
				if (inputStreamCloseWhenRead) {
					try { inputStream.close(); }
					catch (IOException e) { }
				}
			} else {
				if (requestData == null) {
					requestData = (params != null && params.length() > 0 ? params.getBytes(ENCODING) : new byte[0]);
				}
				connection.setRequestProperty("Content-Length", Integer.toString(requestData.length));
				connection.setDoOutput(true);
				copyStream(new ByteArrayInputStream(requestData), connection.getOutputStream(), new CopyProgressListener(uploadProgressListener, requestData.length));
				connection.getOutputStream().flush();
				requestData = null;
			}
		}
		responseCode = connection.getResponseCode();
		responseMessage = connection.getResponseMessage();
		responseHeaders = reindexHeaderMap(connection.getHeaderFields());
		return this;
	}

	/**
	 * Sends the HTTP request asynchronously. A cached thread pool executor is used to spawn new or reuse threads
	 * (see {@link java.util.concurrent.Executors#newCachedThreadPool()}). In case the connection fails with an
	 * {@link java.io.IOException}, the {@link Http#getResponseCode()} is set to -1 and {@link Http#getResponseMessage()}
	 * to {@link java.io.IOException#toString()}. The listener is called from the background thread, not from the thread
	 * this method is called.
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
	 * @return HTTP response code, e.g. 200 for success.
	 */
	public int getResponseCode() { return responseCode; }

	/**
	 * Returns the HTTP response status message.
	 * @return HTTP response status message.
	 */
	public String getResponseMessage() { return responseMessage; }

	/**
	 * Returns the first HTTP response header with the specified key or null if the header does not exist.
	 * @param key The header name to retrieve the first header value for.
	 * @return First HTTP response header value or null if none such header exists.
	 */
	public String getResponseHeader(String key) {
		key = key.toLowerCase();
		return (responseHeaders == null || !responseHeaders.containsKey(key) ? null : responseHeaders.get(key).get(0));
	}

	/**
	 * Returns a list of HTTP response headers with the specified key.
	 * @param key The header name to retrieve the header values for.
	 * @return List of HTTP response header values.
	 */
	public List<String> getResponseHeaders(String key) {
		key = key.toLowerCase();
		return (responseHeaders == null ? new ArrayList<String>() : responseHeaders.get(key));
	}

	/**
	 * Sets the progress listener to watch data download progress.
	 * @param progressListener The callback listener.
	 * @deprecated use Http.setDownloadProgressListener(ProgressListener) instead
	 */
	@Deprecated
	public void setProgressListener(ProgressListener progressListener) {
		setDownloadProgressListener(progressListener);
	}

	/**
	 * Sets the progress listener to watch data download progress.
	 * @param downloadProgressListener The callback listener.
	 * @return This Http object for easy call chaining.
	 * @see Http#getResponseData()
	 * @see Http#getResponseString()
	 * @see Http#writeResponseToStream(OutputStream)
	 * @see Http#writeResponseToFile(File)
	 */
	public Http setDownloadProgressListener(ProgressListener downloadProgressListener) {
		this.downloadProgressListener = downloadProgressListener;
		return this;
	}

	/**
	 * Sets the progress listener to watch data uploaded progress.
	 * @param uploadProgressListener The callback listener.
	 * @return This Http object for easy call chaining.
	 * @see Http#setData(File)
	 * @see Http#setData(InputStream, boolean)
	 */
	public Http setUploadProgressListener(ProgressListener uploadProgressListener) {
		this.uploadProgressListener = uploadProgressListener;
		return this;
	}

	/**
	 * Returns the raw response data.
	 * @return Raw response data.
	 * @throws IOException When the underlying response stream cannot be read.
	 */
	public byte[] getResponseData() throws IOException {
		int contentLength = parseContentLength(getResponseHeader("Content-Length"));
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(contentLength > 0 ? contentLength : BUFFER_SIZE);
		try {
			copyStream(getResponseStream(), buffer, new CopyProgressListener(downloadProgressListener, contentLength));
			return buffer.toByteArray();
		} finally {
			close();
			buffer.close();
		}
	}

	/**
	 * Returns the response data as a string.
	 * @return Response data decoded to string using UTF-8 encoding.
	 */
	public String getResponseString() {
		try {
			return new String(getResponseData(), ENCODING);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns the response stream. Note that you are responsible for closing the stream and calling
	 * {@link #close()} when finished working with the stream.
	 * @return The response stream.
	 * @throws IOException When the underlying response stream cannot be opened.
	 */
	public InputStream getResponseStream() throws IOException {
		if (connection == null) {
			throw new IllegalStateException("Method send() has not been called yet or the connection was already closed.");
		}
		try {
			return connection.getInputStream();
		} catch (FileNotFoundException e) {
			return connection.getErrorStream();
		}
	}

	/**
	 * Returns the response deserialized as an object. This method assumes that the response is a valid JSON.
	 * This method uses {@link #getResponseStream()} method and takes care of closing the stream.
	 * @param classType Class of object to return
	 * @param <T> Type of object to return
	 * @return The object deserialized from the response JSON.
	 * @throws IOException When the underlying response stream cannot be opened.
	 * @throws JsonParseException When the response cannot be parsed as a valid JSON.
	 */
	public <T> T getResponseObject(Class<? extends T> classType) throws IOException, JsonParseException {
		InputStream inputStream = null;
		try {
			inputStream = getResponseStream();
			return new Gson().fromJson(new InputStreamReader(inputStream), classType);
		} finally {
			if (inputStream != null) {
				try { inputStream.close(); }
				catch (IOException e) { }
			}
		}
	}

	/**
	 * Writes the response data directly to a stream. The underlying connection is automatically closed
	 * when the data is copied.
	 * @param stream The stream to write the response data to.
	 * @return The number of bytes written.
	 * @throws IOException When the underlying response stream cannot be opened or read,
	 * or when the data cannot be written to the output stream.
	 */
	public int writeResponseToStream(OutputStream stream) throws IOException {
		int contentLength = parseContentLength(getResponseHeader("Content-Length"));
		try {
			return copyStream(getResponseStream(), stream, new CopyProgressListener(downloadProgressListener, contentLength));
		} finally {
			close();
		}
	}

	/**
	 * Writes the response data directly to a file. The underlying connection is automatically closed
	 * when the data is copied.
	 * @param file A valid file to write the response data to.
	 * @return The number of bytes written.
	 * @throws IOException When the underlying response stream cannot be opened or read,
	 * or when the data cannot be written to the file.
	 */
	public int writeResponseToFile(File file) throws IOException {
		FileOutputStream fileStream = null;
		try {
			fileStream = new FileOutputStream(file);
			return writeResponseToStream(fileStream);
		} finally {
			if (fileStream != null) {
				fileStream.close();
			}
		}
	}

	/**
	 * Closes the underlying connection. This method is automatically called from {@link Http#getResponseData()},
	 * {@link Http#getResponseString()}, {@link #writeResponseToStream(OutputStream)} and {@link #writeResponseToFile(File)}.
	 * You must called this method manually if you're using {@link #getResponseStream()}.
	 */
	@Override
	public void close() {
		if (connection != null) {
			connection.disconnect();
			connection = null;
		}
	}

	private int copyStream(InputStream input, OutputStream output, CopyProgressListener progress) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int read, total = 0;
		progress.onCopyProgress(total);
		while ((read = input.read(buffer, 0, buffer.length)) >= 0) {
			output.write(buffer, 0, read);
			total += read;
			progress.onCopyProgress(total);
		}
		return total;
	}

	private Map<String, List<String>> reindexHeaderMap(Map<String, List<String>> headers) {
		Map<String, List<String>> newHeaders = new HashMap<>();
		for (Map.Entry<String, List<String>> header : headers.entrySet()) {
			if (header.getKey() != null) {
				newHeaders.put(header.getKey().toLowerCase(), header.getValue());
			}
		}
		return newHeaders;
	}

	private int parseContentLength(String contentLengthString) {
		return (contentLengthString != null && contentLengthString.matches("^[0-9]+$")) ? Integer.parseInt(contentLengthString) : -1;
	}

	private static class CopyProgressListener {
		private final ProgressListener listener;
		private final int total;
		CopyProgressListener(ProgressListener listener, int total) {
			this.listener = listener;
			this.total = total;
		}
		void onCopyProgress(int received) {
			if (listener != null) {
				listener.onHttpProgress(received, total);
			}
		}
	}

}
