package com.asfoundation.wallet.util;

import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class LogInterceptor implements Interceptor {
  private static final String TAG = "HTTP_TRACE";
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private static String requestPath(HttpUrl url) {
    String path = url.encodedPath();
    String query = url.encodedQuery();
    return url.scheme() + "://" + url.host() + (query != null ? (path + '?' + query) : path);
  }

  @Override public Response intercept(@NonNull Chain chain) throws IOException {
    StringBuilder logBuilder = new StringBuilder();
    try {
      Request request = chain.request();
      RequestBody requestBody = request.body();
      logBuilder.append(
          "<---------------------------BEGIN REQUEST---------------------------------->");
      logBuilder.append("\n");
      logBuilder.append("Request encoded url: ")
          .append(request.method())
          .append(" ")
          .append(requestPath(request.url()));
      logBuilder.append("\n");
      String decodeUrl = requestDecodedPath(request.url());
      if (!TextUtils.isEmpty(decodeUrl)) {
        logBuilder.append("Request decoded url: ")
            .append(request.method())
            .append(" ")
            .append(decodeUrl);
      }

      Headers headers = request.headers();
      logBuilder.append("\n=============== Headers ===============\n");
      for (int i = headers.size() - 1; i > -1; i--) {
        logBuilder.append(headers.name(i))
            .append(" : ")
            .append(headers.get(headers.name(i)))
            .append("\n");
      }
      logBuilder.append("\n=============== END Headers ===============\n");

      if (requestBody != null) {
        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);

        MediaType contentType = requestBody.contentType();
        if (contentType != null) {
          contentType.charset(UTF8);
        }

        logBuilder.append(buffer.readString(UTF8));
      }
      long startNs = System.nanoTime();
      Response response = chain.proceed(request);
      long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

      ResponseBody responseBody = response.body();
      logBuilder.append("\n");
      logBuilder.append("Response timeout: ")
          .append(tookMs)
          .append("ms");
      logBuilder.append("\n");
      logBuilder.append("Response message: ")
          .append(response.message());
      logBuilder.append("\n");
      logBuilder.append("Response code: ")
          .append(response.code());

      if (responseBody != null) {
        BufferedSource source = responseBody.source();
        source.request(Long.MAX_VALUE); // Buffer the entire body.
        Buffer buffer = source.buffer();

        Charset charset = null;
        MediaType contentType = responseBody.contentType();
        if (contentType != null) {
          charset = contentType.charset(UTF8);
        }

        if (charset == null) {
          charset = UTF8;
        }

        if (responseBody.contentLength() != 0) {
          logBuilder.append("\n");
          logBuilder.append("Response body: \n")
              .append(buffer.clone()
                  .readString(charset));
        }
      }
      headers = response.headers();
      logBuilder.append("\n=============== Headers ===============\n");
      for (int i = headers.size() - 1; i > -1; i--) {
        logBuilder.append(headers.name(i))
            .append(" : ")
            .append(headers.get(headers.name(i)))
            .append("\n");
      }
      logBuilder.append("\n=============== END Headers ===============\n");

      logBuilder.append("\n");
      logBuilder.append(
          "<-----------------------------END REQUEST--------------------------------->");
      logBuilder.append("\n\n\n");
      Log.d(TAG, logBuilder.toString());
      return response;
    } catch (Exception exception) {
      if (logBuilder.length() > 0) {
        Log.d(TAG, logBuilder.toString());
      }
      throw exception;
    }
  }

  private String requestDecodedPath(HttpUrl url) {
    try {
      String path = URLDecoder.decode(url.encodedPath(), "UTF-8");
      String query = URLDecoder.decode(url.encodedQuery(), "UTF-8");
      return url.scheme() + "://" + url.host() + (query != null ? (path + '?' + query) : path);
    } catch (Exception ex) {
      /* Quality */
    }
    return null;
  }
}