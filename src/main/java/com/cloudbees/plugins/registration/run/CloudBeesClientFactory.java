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

import com.cloudbees.EndPoints;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A factory for creating {@link CloudBeesClient} instances.
 */
public class CloudBeesClientFactory {

    @Nullable
    private final ProxyServer proxyServer;

    @NonNull
    private final String format;

    @NonNull
    private final String apiEndpointUrl;

    @CheckForNull
    private final String apiKey;

    @CheckForNull
    private final String apiSecret;

    @NonNull
    private final String apiVersion;

    @NonNull
    private final SignatureAlgorithm signatureAlgorithm;

    @NonNull
    private final AsyncHttpClientConfig clientConfig;

    public CloudBeesClientFactory() {
        proxyServer = null;
        format = "xml";
        apiEndpointUrl = EndPoints.runAPI();
        apiKey = null;
        apiSecret = null;
        apiVersion = "1.0";
        signatureAlgorithm = new SignatureAlgorithmV1Impl();
        clientConfig = new AsyncHttpClientConfig.Builder().build();
    }

    private CloudBeesClientFactory(@Nullable ProxyServer proxyServer, @NonNull String format,
                                   @NonNull String apiEndpointUrl, @CheckForNull String apiKey,
                                   @CheckForNull String apiSecret, @NonNull String apiVersion,
                                   @NonNull SignatureAlgorithm signatureAlgorithm,
                                   @NonNull AsyncHttpClientConfig clientConfig) {
        this.proxyServer = proxyServer;
        this.format = format;
        this.apiEndpointUrl = apiEndpointUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiVersion = apiVersion;
        this.signatureAlgorithm = signatureAlgorithm;
        this.clientConfig = clientConfig;
    }

    @NonNull
    public CloudBeesClientFactory onEndpoint(@NonNull String apiEndpointUrl) {
        apiEndpointUrl.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withFormat(@NonNull String format) {
        format.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withApiVersion(@NonNull String apiVersion) {
        apiVersion.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withProxyServer(@Nullable ProxyServer proxyServer) {
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withoutProxyServer() {
        return new CloudBeesClientFactory(null, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withClientConfig(@NonNull AsyncHttpClientConfig clientConfig) {
        clientConfig.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withAuthentication(@NonNull String apiKey, @NonNull String apiSecret) {
        apiKey.getClass();
        apiSecret.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClientFactory withSignatureAlgorithm(@NonNull SignatureAlgorithm signatureAlgorithm) {
        signatureAlgorithm.getClass();
        return new CloudBeesClientFactory(proxyServer, format, apiEndpointUrl, apiKey, apiSecret, apiVersion,
                signatureAlgorithm, clientConfig);
    }

    @NonNull
    public CloudBeesClient build() {
        if (apiKey == null) {
            throw new IllegalStateException("Must provide a non-null API key");
        }
        if (apiSecret == null) {
            throw new IllegalStateException("Must provide a non-null API secret");
        }
        return new CloudBeesClientImpl(false, new AsyncHttpClient(clientConfig), apiEndpointUrl, apiKey, apiSecret,
                format, apiVersion, proxyServer, signatureAlgorithm);
    }

    @NonNull
    public CloudBeesClient build(AsyncHttpClient client) {
        if (apiKey == null) {
            throw new IllegalStateException("Must provide a non-null API key");
        }
        if (apiSecret == null) {
            throw new IllegalStateException("Must provide a non-null API secret");
        }
        return new CloudBeesClientImpl(true, client, apiEndpointUrl, apiKey, apiSecret, format,
                apiVersion, proxyServer, signatureAlgorithm);
    }

}
