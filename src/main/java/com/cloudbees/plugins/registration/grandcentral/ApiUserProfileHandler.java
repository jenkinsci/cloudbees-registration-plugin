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
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.registration.CloudBeesAccountImpl;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author stephenc
 * @since 19/01/2012 16:09
 */
public class ApiUserProfileHandler extends AsyncCompletionHandler<ApiUserProfileResponse> {

    public static ListenableFuture<ApiUserProfileResponse> executeRequest(AsyncHttpClient client, String uid, String email)
            throws IOException {
        RequestBuilder builder = new RequestBuilder("POST");

        JSONObject params = new JSONObject();
        params.put("uid", uid);
        params.put("email", email);

        return client.executeRequest(builder.setUrl(EndPoints.grandCentral() + "/user/profile")
                .addHeader("content-type", "application/json")
                .setBody(params.toString()).build(), new ApiUserProfileHandler());
    }

    @Override
    public ApiUserProfileResponse onCompleted(Response response) throws Exception {
        if (response.getStatusCode() == 200) {
            JSONObject json = JSONObject.fromObject(response.getResponseBody());
            try {
                String firstName = json.getString("first_name");
                String lastName = json.getString("last_name");
                String fullName = json.getString("full_name");
                String username = json.getString("username");
                List<CloudBeesAccount> accountNames = new ArrayList<CloudBeesAccount>();
                JSONArray accounts = json.getJSONArray("accounts");
                for (int i = 0; i < accounts.size(); i++) {
                    JSONObject account = accounts.getJSONObject(i);
                    String company = account.getString("company_name");
                    String accountName = account.getString("account");
                    accountNames.add(new CloudBeesAccountImpl(accountName,
                            getDisplayNameOf(company, accountName)));
                }
                return new ApiUserProfileResponse(firstName, lastName, fullName, username, accountNames);
            } catch (JSONException e) {
                IOException ioe = new IOException("Unexpected response");
                ioe.initCause(e);
                throw ioe;
            }
        }
        throw new IOException("HTTP " + response.getStatusCode() + "/" + response.getStatusText());

    }

    /**
     * Computes the display name from the company name (which is optional) and the account name
     * (which is unique but more like ID.)
     *
     * When one company signs up multiple accounts, the company name has a tendency to be the same on
     * all these accounts, so for a display name to be unambiguous, we attach the account name.
     */
    private String getDisplayNameOf(String company, String accountName) {
        if (StringUtils.isBlank(company))
            return accountName;
        else
            return company+" ("+accountName+")";
    }
}
