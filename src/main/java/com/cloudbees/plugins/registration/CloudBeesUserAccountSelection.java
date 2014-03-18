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

package com.cloudbees.plugins.registration;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import java.util.List;

/**
 * A reusable component to select a CloudBees user and account.
 */
public class CloudBeesUserAccountSelection extends AbstractDescribableImpl<CloudBeesUserAccountSelection> {
    private final String user;

    private final String account;

    @DataBoundConstructor
    public CloudBeesUserAccountSelection(String user, String account) {
        this.user = user;
        this.account = account;
    }

    public String getUser() {
        return user;
    }

    public String getAccount() {
        return account;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CloudBeesUserAccountSelection> {
        @Override
        public String getDisplayName() {
            return ""; // unused
        }

        public FormValidation doCheckUser(@QueryParameter String user) {
            CloudBeesUser userInstance = null;
            boolean haveUsers = false;
            for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class,
                    Stapler.getCurrentRequest().findAncestorObject(Item.class))) {
                haveUsers = true;
                if (u.getName().equals(user)) {
                    userInstance = u;
                    break;
                }
            }
            if (!haveUsers) {
                return FormValidation.error("No CloudBees users are defined either within the current scope or with the global scope.");
            }
            if (StringUtils.isEmpty(user)) {
                return FormValidation.warning("No user selected");
            }
            List<CloudBeesAccount> accounts = userInstance.getAccounts();
            if (accounts == null) {
                return FormValidation.warning("Cannot connect to CloudBees servers to retrieve the account associated with this user");
            }
            if (accounts.isEmpty()) {
                return FormValidation.warning("This user has not completed the sign-up process. Please complete the sign-up process or select a different user.");
            }
            return FormValidation.ok();
        }


        public ListBoxModel doFillUserItems() {
            ListBoxModel m = new ListBoxModel();

            for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class,
                    Stapler.getCurrentRequest().findAncestorObject(Item.class))) {
                m.add(u.getDisplayName(), u.getName());
            }

            return m;
        }

        public ListBoxModel doFillAccountItems(@QueryParameter String user) {
            ListBoxModel m = new ListBoxModel();

            user = Util.fixEmptyAndTrim(user);
            if (user == null) {
                return m;
            }

            CloudBeesUser u = getCloudBeesUser(user);
            if (u == null) {
                return m;
            }

            for (CloudBeesAccount a : u.getAccounts()) {
                m.add(a.getName(), a.getName());
            }

            return m;
        }

        private CloudBeesUser getCloudBeesUser(String user) {
            for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class,
                    Stapler.getCurrentRequest().findAncestorObject(Item.class))) {
                if (u.getName().equals(user)) {
                    return u;
                }
            }

            return null;
        }
    }

}
