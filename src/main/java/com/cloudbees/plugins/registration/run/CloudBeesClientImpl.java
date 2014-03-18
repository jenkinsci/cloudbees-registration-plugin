/*
 * The MIT License
 *
 * Copyright 2014 CloudBees.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.plugins.registration.run;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.BodyGenerator;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class CloudBeesClientImpl extends CloudBeesClient {

    @Nullable
    private final ProxyServer proxyServer;

    @NonNull
    private final String format;

    @NonNull
    private final String apiEndpointUrl;

    @NonNull
    private final String apiKey;

    @NonNull
    private final String apiSecret;

    @NonNull
    private final String apiVersion;

    @NonNull
    private final SignatureAlgorithm signatureAlgorithm;

    @NonNull
    private final AsyncHttpClient client;

    /**
     * If the client is shared, don't close it ever.
     */
    private final boolean sharedClient;

    CloudBeesClientImpl(boolean sharedClient, @NonNull AsyncHttpClient client, @NonNull String apiEndpointUrl,
                        @NonNull String apikey, @NonNull String apiSecret,
                        @NonNull String format, @NonNull String apiVersion,
                        @Nullable ProxyServer proxyServer,
                        @NonNull SignatureAlgorithm signatureAlgorithm) {
        client.getClass();
        apiEndpointUrl.getClass();
        apikey.getClass();
        apiSecret.getClass();
        format.getClass();
        apiVersion.getClass();
        signatureAlgorithm.getClass();
        this.apiEndpointUrl = apiEndpointUrl;
        this.apiKey = apikey;
        this.apiSecret = apiSecret;
        this.format = format;
        this.apiVersion = apiVersion;
        this.proxyServer = proxyServer;
        this.signatureAlgorithm = signatureAlgorithm;
        this.client = client;
        this.sharedClient = sharedClient;
    }

    public void close() {
        if (!sharedClient) {
        client.close();
        }
    }

    public ListenableFuture<Response> executeRequest(String method, String apiMethod,
                                                     Map<String, String> apiMethodParams) throws IOException {
        return client.executeRequest(createRequest(method, apiMethod, apiMethodParams, null));
    }

    public <T> ListenableFuture<T> executeRequest(String method, String apiMethod,
                                                  Map<String, String> apiMethodParams,
                                                  AsyncHandler<T> handler) throws IOException {
        return client.executeRequest(createRequest(method, apiMethod, apiMethodParams, null), handler);
    }

    public ListenableFuture<Response> executeRequest(String apiMethod, Map<String, String> apiMethodParams,
                                                     BodyGenerator body) throws IOException {
        return client.executeRequest(createRequest("POST", apiMethod, apiMethodParams, body));
    }

    public <T> ListenableFuture<T> executeRequest(String apiMethod,
                                                  Map<String, String> apiMethodParams, BodyGenerator body,
                                                  AsyncHandler<T> handler) throws IOException {
        return client.executeRequest(createRequest("POST", apiMethod, apiMethodParams, body), handler);
    }

    private Request createRequest(String method, String apiMethod, Map<String, String> apiMethodParams,
                                  BodyGenerator bodyGenerator) {
        RequestBuilder builder = new RequestBuilder(method);
        builder.setProxyServer(proxyServer);
        builder.setUrl(apiEndpointUrl);
        if (bodyGenerator != null && !"GET".equals(method) && !"HEAD".equals(method)) {
            builder.setBody(bodyGenerator);
        }
        Map<String, String> queryParams = defaultQueryParams();
        if (apiMethodParams != null) {
            queryParams.putAll(apiMethodParams);
        }
        queryParams.put("action", apiMethod);
        for (Map.Entry<String, String> queryParameter : queryParams.entrySet()) {
            builder.addQueryParameter(queryParameter.getKey(), queryParameter.getValue());
        }
        builder.addQueryParameter("sig", signatureAlgorithm.getSignature(queryParams, apiSecret));
        return builder.build();
    }

    private Map<String, String> defaultQueryParams() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("format", format);
        result.put("v", apiVersion);
        result.put("api_key", apiKey);
        result.put("timestamp", Long.toString(System.currentTimeMillis() / 1000));
        result.put("sig_version", signatureAlgorithm.getVersion());
        return result;
    }

}
