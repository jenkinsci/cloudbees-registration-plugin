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

package com.cloudbees.plugins.registration.grandcentral;

import com.cloudbees.Domain;
import com.cloudbees.plugins.registration.CloudBeesWidget;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An account health status query handler.
 */
public class ApiAccountHealthStatusHandler extends AsyncCompletionHandler<List<CloudBeesWidget.StatusLine>> {

    /**
     * Represents the type of authentication to query with.
     */
    public static enum Authentication {
        UID("uid"),
        ACCOUNT_API_KEY("acc_api_key");

        private final String key;

        private Authentication(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Submits an account health status query.
     *
     * @param client      the client to make the request with.
     * @param uid         the uid of the user to make the request as.
     * @param accountName the account
     * @return
     * @throws IOException
     * @deprecated use {@link #executeRequest(com.ning.http.client.AsyncHttpClient,
     *             com.cloudbees.plugins.registration.grandcentral.ApiAccountHealthStatusHandler.Authentication,
     *             String, String)}
     */
    @Deprecated
    public static ListenableFuture<List<CloudBeesWidget.StatusLine>> executeRequest(AsyncHttpClient client,
                                                                                    String uid,
                                                                                    String accountName)
            throws IOException {
        return executeRequest(client, Authentication.UID, uid, accountName);
    }

    /**
     * Submits an account health status query.
     *
     * @param client             the client to make the request with.
     * @param authenticationType the type of authentication.
     * @param authentication     the authentication.
     * @param accountName
     * @return
     * @throws IOException
     */
    public static ListenableFuture<List<CloudBeesWidget.StatusLine>> executeRequest(AsyncHttpClient client,
                                                                                    Authentication authenticationType,
                                                                                    String authentication,
                                                                                    String accountName)
            throws IOException {
        RequestBuilder builder = new RequestBuilder("POST");

        JSONObject params = new JSONObject();
        params.put(authenticationType.getKey(), authentication);
        params.put("account", accountName);

        return client.executeRequest(builder.setUrl(System.getProperty("dsp.url","https://dev-provider.cloudbees.com") + "/api/account/health_status")
                .addHeader("content-type", "application/json")
                .setFollowRedirects(true)
                .setBody(params.toString()).build(), new ApiAccountHealthStatusHandler());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudBeesWidget.StatusLine> onCompleted(Response response) throws Exception {
        if (response.getStatusCode() == 200) {
            JSONObject json = JSONObject.fromObject(response.getResponseBody());
            if (json.containsKey("remaining_minutes")) {
                JSONObject minutes = json.getJSONObject("remaining_minutes");
                List<CloudBeesWidget.StatusLine> result = new ArrayList<CloudBeesWidget.StatusLine>();
                for (String key : (Set<String>) minutes.keySet()) {
                    long value = minutes.optLong(key);
                    result.add(new CloudBeesWidget.StatusLine("status-build.png", "build." + key, value));
                }
                return result;
            } else {
                return Collections.emptyList();
            }
        }
        throw new IOException("HTTP " + response.getStatusCode() + "/" + response.getStatusText() + "\n" + response
                .getResponseBody());
    }
}
