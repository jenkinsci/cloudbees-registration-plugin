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
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import hudson.util.FormValidation;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * @author stephenc
 * @since 19/01/2012 16:09
 */
public class ApiUserKeysUsingAuthHandler extends AsyncCompletionHandler<ApiUserKeysUsingAuthResponse> {

    public static ListenableFuture<ApiUserKeysUsingAuthResponse> executeRequest(AsyncHttpClient client, String email,
                                                                                String password)
            throws IOException {
        RequestBuilder builder = new RequestBuilder("POST");

        JSONObject params = new JSONObject();
        params.put("email", email);
        params.put("password", password);

        Request request = builder.setUrl(EndPoints.grandCentral() + "/user/keys_using_auth")
                .addHeader("content-type", "application/json")
                .setBody(params.toString()).build();
        return client.executeRequest(request, new ApiUserKeysUsingAuthHandler());
    }

    @Override
    public ApiUserKeysUsingAuthResponse onCompleted(Response response) throws Exception {
        if (response.getStatusCode() == 200) {
            JSONObject json = JSONObject.fromObject(response.getResponseBody());
            if (json.containsKey("uid") && json.containsKey("api_key") && json.containsKey("secret_key")) {
                return new ApiUserKeysUsingAuthResponse(json.getString("uid"), json.getString("api_key"),
                        json.getString("secret_key"));
            }
        }
        if (response.getStatusCode() == 400) {
            try {
            JSONObject json = JSONObject.fromObject(response.getResponseBody());
                if (json.containsKey("message")) {
                    throw FormValidation.error(json.getString("message"));
                }
            } catch (JSONException e) {
                // ignore
            }
        }
        throw new IOException("HTTP " + response.getStatusCode() + "/" + response.getStatusText());

    }
}
