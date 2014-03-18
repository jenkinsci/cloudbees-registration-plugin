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

import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * Contributes additional {@link DataPoint}s into {@link CloudBeesWidget}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class DataPointContributor implements ExtensionPoint {
    /**
     * Collects additional data points and adds them into the given collection.
     *
     * <p>
     * Data point collection happens periodically and somewhat infrequently (2 mins currently), but it is done
     * in a background without blocking the HTTP request handling thread.
     * This design allows this method to perform slow I/O if needed.
     *
     * @param user
     *      User account registered with Jenkins. Never null.
     * @param accountName
     *      Status is reported per account. This is the current account for which data points
     *      are gathered. Never null.
     * @param result
     *      Any data points obtained should be added into this collection.
     */
    public abstract void collect(CloudBeesUser user, String accountName, List<DataPoint> result);

    /**
     * This version is called synchronously from the HTTP request handling thread.
     *
     * <p>
     * This is suitable when your data point can be computed without incurring I/O, and you need to
     * be able to update values more frequently than the 2 mins cycles of {@link #collect(CloudBeesUser, String, List)}
     */
    public abstract void collectSync(CloudBeesUser user, String accountName, List<DataPoint> result);

    public static ExtensionList<DataPointContributor> all() {
        return Jenkins.getInstance().getExtensionList(DataPointContributor.class);
    }

}
