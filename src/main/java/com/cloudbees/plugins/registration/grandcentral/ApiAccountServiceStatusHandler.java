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

import com.cloudbees.EndPoints;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * @author stephenc
 * @since 19/01/2012 15:58
 */
public class ApiAccountServiceStatusHandler extends AsyncCompletionHandler<String> {

    public static ListenableFuture<String> executeRequest(AsyncHttpClient client, String uid, String accountName)
            throws IOException {
        RequestBuilder builder = new RequestBuilder("POST");

        JSONObject params = new JSONObject();
        params.put("uid", uid);
        params.put("account", accountName);

        return client.executeRequest(builder.setUrl(EndPoints.grandCentral() + "/account/service_status")
                .addHeader("content-type", "application/json")
                .setBody(params.toString()).build(), new ApiAccountServiceStatusHandler());
    }

    @Override
    public String onCompleted(Response response) throws Exception {
        if (response.getStatusCode() == 200) {
            JSONObject json = JSONObject.fromObject(response.getResponseBody());
            return json.optString("username");
        }
        throw new IOException("HTTP " + response.getStatusCode() + "/" + response.getStatusText());
    }
}
