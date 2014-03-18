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

import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Corresponds to a single line in the "cloudbees" status widget.
 *
 * @author Kohsuke Kawaguchi
 * @see DataPointContributor
 */
public abstract class DataPoint {
    private final String iconFileName;

    protected DataPoint(String iconFileName) {
        this.iconFileName = iconFileName;
    }

    /**
     * Icon name like "foo.png". Must be 16x16.
     *
     * If the value contains no path portion, it points to the icon inside the cloudbees-registration plugin.
     *
     * If the value starts from '/', it is interpreted as relative to the context path where Jenkins is deployed.
     * If you point to an icon file inside your plugin, don't forget to prepend it with
     * {@code Jenkins.RESOURCE_PATH} to benefit from caching.
     *
     * Otherwise the value is interpreted as an absolute URL.
     */
    public String getIconFileName() {
        return iconFileName;
    }

    public String getIconUrl(StaplerRequest req) {
        if (!iconFileName.contains("/")) {// just the file name
            return req.getContextPath()+Jenkins.RESOURCE_PATH+"/plugin/cloudbees-registration/images/16x16/"+getIconFileName();
        }

        if (iconFileName.startsWith("/")) {// relative to the context path
            return req.getContextPath()+iconFileName;
        }

        return iconFileName;    // otherwise it better be absolute
    }

    /**
     * Title of the data point in a few words.
     *
     * The casing should be "Something Like This".
     * This value is shown to the left column.
     */
    public abstract String getMessage();

    /**
     * The value of the data point in a few words.
     *
     * This casing should be "something like this".
     * This value is shown to the right column.
     *
     * To further control the rendering of this in HTML,
     * override {@code data.jelly}
     */
    public abstract String getFormattedValue();
}
