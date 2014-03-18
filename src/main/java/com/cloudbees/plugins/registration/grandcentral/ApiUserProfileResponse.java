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

import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;

import java.util.List;

/**
 * @author stephenc
 * @since 19/01/2012 16:33
 */
public class ApiUserProfileResponse {
    private String firstName;
    private String lastName;
    private String fullName;
    private String username;
    private List<CloudBeesAccount> accounts;

    public ApiUserProfileResponse(String firstName, String lastName, String fullName, String username,
                                  List<CloudBeesAccount> accounts) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.username = username;
        this.accounts = accounts;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public List<CloudBeesAccount> getAccounts() {
        return accounts;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ApiUserProfileResponse");
        sb.append("{firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", fullName='").append(fullName).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append(", accounts=").append(accounts);
        sb.append('}');
        return sb.toString();
    }
}
