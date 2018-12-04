package com.microsoft.appcenter.http;

import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.microsoft.appcenter.utils.AppCenterLog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import static com.microsoft.appcenter.AppCenter.LOG_TAG;
import static com.microsoft.appcenter.http.DefaultHttpClient.CHARSET_NAME;
import static com.microsoft.appcenter.http.DefaultHttpClient.CONTENT_ENCODING_KEY;
import static com.microsoft.appcenter.http.DefaultHttpClient.CONTENT_ENCODING_VALUE;
import static com.microsoft.appcenter.http.DefaultHttpClient.CONTENT_TYPE_KEY;
import static com.microsoft.appcenter.http.DefaultHttpClient.CONTENT_TYPE_VALUE;
import static com.microsoft.appcenter.http.DefaultHttpClient.METHOD_POST;
import static java.lang.Math.max;
import static java.lang.Math.min;

class DefaultHttpClientCallTask extends AsyncTask<Void, Void, Object> {

    /**
     * Thread stats tag for App Center HTTP calls.
     */
    private static final int THREAD_STATS_TAG = 0xD83DDC19;

    /**
     * Default string builder capacity.
     */
    private static final int DEFAULT_STRING_BUILDER_CAPACITY = 16;

    /**
     * Minimum payload length in bytes to use gzip.
     */
    private static final int MIN_GZIP_LENGTH = 1400;

    /**
     * Read buffer size.
     */
    private static final int READ_BUFFER_SIZE = 1024;

    /**
     * Write buffer size.
     */
    private static final int WRITE_BUFFER_SIZE = 1024;

    /**
     * HTTP connection timeout.
     */
    private static final int CONNECT_TIMEOUT = 60000;

    /**
     * HTTP read timeout.
     */
    private static final int READ_TIMEOUT = 20000;

    /**
     * Maximum payload length to use prettify for logging.
     */
    private static final int MAX_PRETTIFY_LOG_LENGTH = 4 * 1024;

    /**
     * Pattern used to replace token in url encoded parameters.
     */
    private static final Pattern TOKEN_REGEX_URL_ENCODED = Pattern.compile("token=[^&]+");

    /**
     * Pattern used to replace token in json responses.
     */
    private static final Pattern TOKEN_REGEX_JSON = Pattern.compile("token\":\"[^\"]+\"");

    private final String mUrl;

    private final String mMethod;

    private final Map<String, String> mHeaders;

    private final HttpClient.CallTemplate mCallTemplate;

    private final ServiceCallback mServiceCallback;

    private final Tracker mTracker;

    DefaultHttpClientCallTask(String url, String method, Map<String, String> headers, HttpClient.CallTemplate callTemplate, ServiceCallback serviceCallback, Tracker tracker) {
        mUrl = url;
        mMethod = method;
        mHeaders = headers;
        mCallTemplate = callTemplate;
        mServiceCallback = serviceCallback;
        mTracker = tracker;
    }

    /**
     * Write payload to output stream.
     */
    private void writePayload(OutputStream out, byte[] payload) throws IOException {
        for (int i = 0; i < payload.length; i += WRITE_BUFFER_SIZE) {
            out.write(payload, i, min(payload.length - i, WRITE_BUFFER_SIZE));
            if (isCancelled()) {
                break;
            }
        }
    }

    /**
     * Dump response stream to a string.
     */
    private String readResponse(HttpURLConnection urlConnection) throws IOException {

        /*
         * Though content length header value is less than actual payload length (gzip), we want to init
         * buffer with a reasonable start size to optimize (default is 16 and is way too low for this
         * use case).
         */
        StringBuilder builder = new StringBuilder(max(urlConnection.getContentLength(), DEFAULT_STRING_BUILDER_CAPACITY));
        InputStream stream = getInputStream(urlConnection);

        //noinspection TryFinallyCanBeTryWithResources
        try {
            Reader reader = new InputStreamReader(stream, CHARSET_NAME);
            char[] buffer = new char[READ_BUFFER_SIZE];
            int len;
            while ((len = reader.read(buffer)) > 0) {
                builder.append(buffer, 0, len);
                if (isCancelled()) {
                    break;
                }
            }
            return builder.toString();
        } finally {
            stream.close();
        }
    }

    private static InputStream getInputStream(HttpURLConnection urlConnection) throws IOException {
        int status = urlConnection.getResponseCode();
        if (status >= 200 && status < 400) {
            return urlConnection.getInputStream();
        } else {
            return urlConnection.getErrorStream();
        }
    }

    /**
     * Do http call.
     */
    private String doHttpCall() throws Exception {

        /* HTTP session. */
        URL url = new URL(mUrl);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {

            /*
             * Make sure we use TLS 1.2 when the device supports it but not enabled by default.
             * Don't hardcode TLS version when enabled by default to avoid unnecessary wrapping and
             * to support future versions of TLS such as say 1.3 without having to patch this code.
             */
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
                urlConnection.setSSLSocketFactory(new TLS1_2SocketFactory());
            }

            /* Configure connection timeouts. */
            urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
            urlConnection.setReadTimeout(READ_TIMEOUT);

            /* Build payload now if POST. */
            urlConnection.setRequestMethod(mMethod);
            String payload = null;
            byte[] binaryPayload = null;
            boolean shouldCompress = false;
            boolean isPost = mMethod.equals(METHOD_POST);
            if (isPost && mCallTemplate != null) {

                /* Get bytes, check if large enough to compress. */
                payload = mCallTemplate.buildRequestBody();
                binaryPayload = payload.getBytes(CHARSET_NAME);
                shouldCompress = binaryPayload.length >= MIN_GZIP_LENGTH;

                /* If no content type specified, assume json. */
                if (!mHeaders.containsKey(CONTENT_TYPE_KEY)) {
                    mHeaders.put(CONTENT_TYPE_KEY, CONTENT_TYPE_VALUE);
                }
            }

            /* If about to compress, add corresponding header. */
            if (shouldCompress) {
                mHeaders.put(CONTENT_ENCODING_KEY, CONTENT_ENCODING_VALUE);
            }

            /* Send headers. */
            for (Map.Entry<String, String> header : mHeaders.entrySet()) {
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (isCancelled()) {
                return null;
            }

            /* Call back before the payload is sent. */
            if (mCallTemplate != null) {
                mCallTemplate.onBeforeCalling(url, mHeaders);
            }

            /* Send payload. */
            if (binaryPayload != null) {

                /* Log payload. */
                if (AppCenterLog.getLogLevel() <= Log.VERBOSE) {
                    if (payload.length() < MAX_PRETTIFY_LOG_LENGTH) {
                        payload = TOKEN_REGEX_URL_ENCODED.matcher(payload).replaceAll("token=***");
                        if (CONTENT_TYPE_VALUE.equals(mHeaders.get(CONTENT_TYPE_KEY))) {
                            payload = new JSONObject(payload).toString(2);
                        }
                    }
                    AppCenterLog.verbose(LOG_TAG, payload);
                }

                /* Compress payload if large enough to be worth it. */
                if (shouldCompress) {
                    ByteArrayOutputStream gzipBuffer = new ByteArrayOutputStream(binaryPayload.length);
                    GZIPOutputStream gzipStream = new GZIPOutputStream(gzipBuffer);
                    gzipStream.write(binaryPayload);
                    gzipStream.close();
                    binaryPayload = gzipBuffer.toByteArray();
                }

                /* Send payload on the wire. */
                urlConnection.setDoOutput(true);
                urlConnection.setFixedLengthStreamingMode(binaryPayload.length);
                OutputStream out = urlConnection.getOutputStream();

                //noinspection TryFinallyCanBeTryWithResources
                try {
                    writePayload(out, binaryPayload);
                } finally {
                    out.close();
                }
            }
            if (isCancelled()) {
                return null;
            }

            /* Read response. */
            int status = urlConnection.getResponseCode();
            String response = readResponse(urlConnection);
            if (AppCenterLog.getLogLevel() <= Log.VERBOSE) {
                String contentType = urlConnection.getHeaderField(CONTENT_TYPE_KEY);
                String logPayload;
                if (contentType == null || contentType.startsWith("text/") || contentType.startsWith("application/")) {
                    logPayload = TOKEN_REGEX_JSON.matcher(response).replaceAll("token\":\"***\"");
                } else {
                    logPayload = "<binary>";
                }
                AppCenterLog.verbose(LOG_TAG, "HTTP response status=" + status + " payload=" + logPayload);
            }

            /* Accept all 2xx codes. */
            if (status >= 200 && status < 300) {
                return response;
            }

            /* Generate exception on failure. */
            throw new HttpException(status, response);
        } finally {

            /* Release connection. */
            urlConnection.disconnect();
        }
    }

    @Override
    protected Object doInBackground(Void... params) {

        /* Do tag socket to avoid strict mode issue. */
        TrafficStats.setThreadStatsTag(THREAD_STATS_TAG);
        try {
            return doHttpCall();
        } catch (Exception e) {
            return e;
        } finally {
            TrafficStats.clearThreadStatsTag();
        }
    }

    @Override
    protected void onPreExecute() {
        mTracker.onStart(this);
    }

    @Override
    protected void onPostExecute(Object result) {
        mTracker.onFinish(this);
        if (result instanceof Exception) {
            mServiceCallback.onCallFailed((Exception) result);
        } else {
            mServiceCallback.onCallSucceeded(result.toString());
        }
    }

    @Override
    protected void onCancelled(Object result) {

        /* Handle the result even if it was cancelled. */
        if (result instanceof String || result instanceof HttpException) {
            onPostExecute(result);
        } else {
            mTracker.onFinish(this);
        }
    }

    interface Tracker {

        void onStart(DefaultHttpClientCallTask task);

        void onFinish(DefaultHttpClientCallTask task);
    }
}
