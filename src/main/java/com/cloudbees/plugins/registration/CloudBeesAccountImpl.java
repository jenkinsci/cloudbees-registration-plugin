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

import com.cloudbees.plugins.credentials.cloudbees.AbstractCloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Plain vanilla implementation of {@link CloudBeesAccount}
 */
public class CloudBeesAccountImpl extends AbstractCloudBeesAccount {

    private static final long serialVersionUID = 1L;

    private final String name;

    private final String displayName;

    @DataBoundConstructor
    public CloudBeesAccountImpl(String name, String displayName) {
        this.name = name;
        this.displayName = Util.fixEmptyAndTrim(displayName) == null ? name : displayName;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CloudBeesAccountImpl");
        sb.append("{name='").append(name).append('\'');
        sb.append("{displayName='").append(displayName).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
