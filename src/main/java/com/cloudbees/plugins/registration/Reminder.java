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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.PageDecorator;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Displays the reminder that the user needs to register.
 */
@Extension
public class Reminder extends PageDecorator {

    private boolean widgetDisabled;

    private boolean nagDisabled;

    private transient volatile long lastNagTime;

    public Reminder() {
        super(Reminder.class);
        load();
    }

    public boolean isRegistered() {
        return !CredentialsProvider.lookupCredentials(CloudBeesUser.class).isEmpty();
    }

    public boolean isNagDue() {
        if (!(Jenkins.getActiveInstance().servletContext.getAttribute("app") instanceof Hudson)) {
            return false;   // no point in nagging the user during licensing screens
        }
        if (isRegistered()) {
            return false; // no nag when registered
        }
        if (!isPluginEnabled()) {
            return false; // no nag when disabled
        }
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if (session != null) {
            Long nextNagTime = (Long) session.getAttribute(Reminder.class.getName() + ".nextNagTime");
            if (nextNagTime != null) {
                return System.currentTimeMillis() > nextNagTime;
            }
        }
        if (System.currentTimeMillis() < lastNagTime + TimeUnit.SECONDS.toMillis(600)) {
            return false;
        }
        return true;
    }

    private static volatile long nextCheck = 0;
    private static volatile boolean lastEnabledState = false;

    public static boolean isPluginEnabled() {
        if (System.currentTimeMillis() > nextCheck) {
            try {
                lastEnabledState =
                        Jenkins.getActiveInstance().getPluginManager().getPlugin("cloudbees-registration").isEnabled();
                nextCheck = System.currentTimeMillis() + 5000;
            } catch (NullPointerException e) {
                return false;
            }
        }
        return lastEnabledState;
    }

    public static Reminder getInstance() {
        for (PageDecorator d : PageDecorator.all()) {
            if (d instanceof Reminder) {
                return (Reminder) d;
            }
        }
        throw new AssertionError(Reminder.class + " is missing its descriptor");
    }

    public HttpResponse doAct(StaplerRequest request, @QueryParameter(fixEmpty = true) String yes,
                              @QueryParameter(fixEmpty = true) String no,
                              @QueryParameter(fixEmpty = true) String later) throws IOException, ServletException {
        if (yes != null) {
            return HttpResponses.redirectViaContextPath(SystemCredentialsProvider.getInstance().getUrlName());
        } else if (no != null) {
            Jenkins.getActiveInstance().getPluginManager().getPlugin("cloudbees-registration").disable();
            return HttpResponses
                    .redirectViaContextPath(Jenkins.getActiveInstance().getPluginManager().getSearchUrl() + "/installed");
        } else if (later != null) {
            HttpSession session = Stapler.getCurrentRequest().getSession(true);
            session.setAttribute(Reminder.class.getName() + ".nextNagTime",
                    Long.valueOf(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(600)));
            return HttpResponses.forwardToPreviousPage();
        } else { //remind later
//            lastNagTime = System.currentTimeMillis();
            return HttpResponses.forwardToPreviousPage();
        }
    }

    public boolean isNagDisabled() {
        return nagDisabled;
    }

    public void setNagDisabled(boolean nagDisabled) {
        if (this.nagDisabled != nagDisabled) {
            this.nagDisabled = nagDisabled;
            save();
        }
    }

    public boolean isWidgetDisabled() {
        return widgetDisabled;
    }

    public void setWidgetDisabled(boolean widgetDisabled) {
        if (this.widgetDisabled != widgetDisabled) {
            this.widgetDisabled = widgetDisabled;
            save();
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        if (super.configure(req, json)) {
            save();
            return true;
        } else {
            return false;
        }
    }
}
