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

import com.cloudbees.Domain;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUserWithAccountApiKey;
import com.cloudbees.plugins.registration.grandcentral.ApiAccountHealthStatusHandler;
import com.cloudbees.plugins.registration.run.CloudBeesClient;
import com.cloudbees.plugins.registration.run.CloudBeesClientFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import hudson.Extension;
import hudson.Util;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.widgets.Widget;
import jenkins.model.Jenkins;
import jenkins.plugins.asynchttpclient.AHCUtils;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

@Extension
public class CloudBeesWidget extends Widget {

    private final static ResourceBundleHolder holder = ResourceBundleHolder.get(CloudBeesWidget.class);

    private static final Logger LOGGER = Logger.getLogger(CloudBeesWidget.class.getName());

    public static final PermissionGroup GROUP =
            new PermissionGroup(CloudBeesWidget.class, Messages._CloudBeesWidget_PermissionsTitle());

    public static final Permission VIEW =
            new Permission(GROUP, "View", Messages._CloudBeesWidget_PermissionView_Description(),
                    Jenkins.ADMINISTER, PermissionScope.JENKINS);

    public boolean isEnabled() {
        return Reminder.isPluginEnabled() && !Reminder.getInstance().isWidgetDisabled();
    }

    public boolean isRegistered() {
        return !CredentialsProvider.lookupCredentials(CloudBeesUser.class).isEmpty();
    }

    public boolean isUserNotARobot() {
        final StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            return true;
        }
        return !Util.fixNull(request.getQueryString()).contains("i_am_a_robot");
    }

    @Override
    public String getUrlName() {
        return "cloudbees-status";
    }

    public String getRemoteUrl() {
        return "https://" + Domain.grandCentral();
    }

    public String getWidgetUrl() {
        List<Widget> list = Hudson.getInstance().getWidgets();
        Iterator<Widget> iterator = list.iterator();
        for (int i = 0; i < list.size() && iterator.hasNext(); i++) {
            Widget widget = iterator.next();
            if (widget == this) {
                return "/widgets/" + i;
            }
        }
        return null;
    }

    public List<String> getAccounts() {
        Set<CloudBeesUser> users = new LinkedHashSet<CloudBeesUser>();
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUserWithAccountApiKey.class));
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUser.class));
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUser.class, Hudson.getAuthentication()));
        Set<String> result = new TreeSet<String>();
        for (CloudBeesUser user : users) {
            List<CloudBeesAccount> accounts = user.getAccounts();
            if (accounts != null) {
                for (CloudBeesAccount account : accounts) {
                    result.add(account.getName());
                }
            }
        }
        return new ArrayList<String>(result);
    }

    public CloudBeesUser findUser(String accountName) {
        Set<CloudBeesUser> users = new LinkedHashSet<CloudBeesUser>();
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUserWithAccountApiKey.class));
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUser.class));
        users.addAll(CredentialsProvider.lookupCredentials(CloudBeesUser.class, Hudson.getAuthentication()));
        for (CloudBeesUser user : users) {
            List<CloudBeesAccount> accounts = user.getAccounts();
            if (accounts != null) {
                for (CloudBeesAccount account : accounts) {
                    if (accountName.endsWith(account.getName())) {
                        return user;
                    }
                }
            }
        }
        return null;
    }

    public List<DataPoint> getAccountStatus(String accountName) throws Exception {
        StatusCacheEntry result = statusCache.get(accountName);
        if (result == null || (result.timestamp + TimeUnit.SECONDS.toMillis(120) < System.currentTimeMillis()
                && !result.requested)) {
            CloudBeesUser user = findUser(accountName);
            if (user != null) {
                if (result != null) {
                    result.requested = true;
                }
                pendingStatus.offer(new AbstractMap.SimpleEntry<CloudBeesUser, String>(user, accountName));
            }
        }
        if (result==null)
            return null;

        List<DataPoint> r = new ArrayList<DataPoint>(result.value);
        for (DataPointContributor dpc : DataPointContributor.all()) {
            dpc.collectSync(findUser(accountName),accountName,r);
        }
        return r;
    }

    private static final Queue<Map.Entry<CloudBeesUser, String>> pendingStatus =
            new ConcurrentLinkedQueue<Map.Entry<CloudBeesUser, String>>();
    private static final Map<String, StatusCacheEntry> statusCache = new MapMaker()
            .concurrencyLevel(16)
            .expiration(600, TimeUnit.SECONDS)
            .makeMap();

    private static class StatusCacheEntry {
        private final long timestamp;
        private final List<DataPoint> value;
        private volatile boolean requested;

        private StatusCacheEntry(List<? extends DataPoint> value) {
            this.timestamp = System.currentTimeMillis();
            this.value = ImmutableList.copyOf(value);
        }

        private StatusCacheEntry(DataPoint... values) {
            this(Arrays.asList(values));
        }
    }

    public static class StatusLine extends DataPoint {

        private final String messageKey;

        private final long value;

        /**
         * CSS style on the whole row. Useful to highlight errors, etc.
         */
        private final String styleClass;

        public StatusLine(String iconFileName, String messageKey, long value) {
            this(iconFileName, messageKey, value, "");
        }

        public StatusLine(String iconFileName, String messageKey, long value, String styleClass) {
            super(iconFileName);
            this.messageKey = messageKey;
            this.value = value;
            this.styleClass = styleClass;
        }

        public Object getValue() {
            return value;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getMessage() {
            return holder.format(messageKey);
        }

        public String getFormattedValue() {
            if (value == 0L) {
                return holder.format(messageKey + ".zero");
            }
            if (value < 0L) {
                return holder.format(messageKey + ".used", -value);
            }
            return holder.format(messageKey + ".avail", value);
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    @Extension
    public static class StatusUpdater extends PeriodicWork {

        private static final ConcurrentMap<String, Boolean> inProgress = new ConcurrentHashMap<String, Boolean>();

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(5);
        }

        @Override
        protected void doRun() throws Exception {
            while (true) {
                final Map.Entry<CloudBeesUser, String> entry = pendingStatus.poll();
                if (entry == null) {
                    return;
                }
                final CloudBeesUser user = entry.getKey();
                final String accountName = entry.getValue();
                for (Iterator<Map.Entry<CloudBeesUser, String>> i = pendingStatus.iterator(); i.hasNext(); ) {
                    Map.Entry<CloudBeesUser, String> e = i.next();
                    if (e.getValue().equals(accountName) && e.getKey().getName().equals(user.getName())) {
                        i.remove();
                    }
                }
                CloudBeesUserImpl.threadPoolForUpdating.submit(new Runnable() {
                    public void run() {
                        long expire = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
                        boolean addContributors = true;
                        if (null != inProgress.putIfAbsent(accountName, Boolean.TRUE)) {
                            LOGGER.log(FINE, "Health check for {0} already in progress", accountName);
                            return;
                        }
                        LOGGER.log(FINE, "Starting Health check for {0}", accountName);
                        try {
                            final String prefix = accountName + '/';

                            List<DataPoint> result = new ArrayList<DataPoint>();
                            AsyncHttpClientConfig.Builder httpClientConfig =
                                    new AsyncHttpClientConfig.Builder()
                                            .setRequestTimeoutInMs(25000)
                                            .setProxyServer(AHCUtils.getProxyServer());
                            AsyncHttpClient gcClient = new AsyncHttpClient(httpClientConfig.build());
                            try {
                                String uid = user.getUID();
                                String apiKey = user.getAPIKey();
                                String apiSecret = user.getAPISecret().getPlainText();
                                ListenableFuture<List<StatusLine>> futureHealthResponse = null;
                                if (uid != null) {
                                    LOGGER.log(Level.FINER, "Getting remaining minutes for {0}", accountName);
                                    try {
                                        futureHealthResponse = ApiAccountHealthStatusHandler
                                                .executeRequest(gcClient,
                                                        ApiAccountHealthStatusHandler.Authentication.UID, uid,
                                                        accountName);
                                        // request can trundle along in background
                                    } catch (ConnectException e) {
                                        throw e;
                                    } catch (IOException e) {
                                        LOGGER.log(FINE, e.getMessage(), e);
                                    }
                                }
                                if (futureHealthResponse == null && user instanceof CloudBeesUserWithAccountApiKey) {
                                    String accountApiKey =
                                            CloudBeesUserWithAccountApiKey.class.cast(user).getAccountApiKey();
                                    if (accountApiKey != null) {
                                        LOGGER.log(Level.FINER, "Getting remaining minutes for {0}", accountName);
                                        try {
                                            futureHealthResponse = ApiAccountHealthStatusHandler
                                                    .executeRequest(gcClient,
                                                            ApiAccountHealthStatusHandler.Authentication
                                                                    .ACCOUNT_API_KEY,
                                                            accountApiKey, accountName);
                                            // request can trundle along in background
                                        } catch (ConnectException e) {
                                            throw e;
                                        } catch (IOException e) {
                                            LOGGER.log(FINE, e.getMessage(), e);
                                        }
                                    }
                                }
                                if (apiKey != null && apiSecret != null) {
                                    CloudBeesClient client = new CloudBeesClientFactory()
                                            .withProxyServer(AHCUtils.getProxyServer())
                                            .withAuthentication(apiKey, apiSecret)
                                            .build(gcClient);
                                    try {
                                        LOGGER.log(Level.FINER, "Getting app statuses for {0}", accountName);
                                        Map<String, AtomicLong> summary = new TreeMap<String, AtomicLong>();
                                        for (Map.Entry<String, String> appStatus : client
                                                .getApplicationsStatuses(accountName)
                                                .get(Math.max(1, expire - System.currentTimeMillis()),
                                                        TimeUnit.MILLISECONDS).entrySet()) {
                                            if (appStatus.getKey().startsWith(prefix)) {
                                                String status = appStatus.getValue();
                                                AtomicLong count = summary.get(status);
                                                if (count == null) {
                                                    summary.put(status, count = new AtomicLong());
                                                }
                                                count.incrementAndGet();
                                            }
                                        }
                                        LOGGER.log(Level.FINER, "Got app statuses for {0}", accountName);
                                        for (Map.Entry<String, AtomicLong> part : summary.entrySet()) {
                                            String key = StringUtils.lowerCase(part.getKey());
                                            result.add(new StatusLine("status-" + key + ".png", "app." + key,
                                                    part.getValue().longValue()));
                                        }
                                    } finally {
                                        client.close();
                                    }
                                }
                                if (futureHealthResponse != null) {
                                    result.addAll(
                                            futureHealthResponse.get(Math.max(1, expire - System.currentTimeMillis()),
                                                    TimeUnit.MILLISECONDS));
                                    LOGGER.log(Level.FINER, "Got health response for {0}", accountName);
                                }
                                addContributors = false; // from this point onwards, don't try and re-add them
                                for (DataPointContributor dpc : DataPointContributor.all()) {
                                    try {
                                        dpc.collect(user,accountName,result);
                                    } catch (Throwable e) {
                                        LOGGER.log(FINE, "Collector " + dpc + " threw: " + e.getMessage(), e);
                                        if (System.currentTimeMillis() > expire) {
                                            // we have taken too long already
                                            throw e;
                                        }
                                    }
                                }
                                statusCache.put(accountName, new StatusCacheEntry(result));
                            } catch (InterruptedException e) {
                                LOGGER.log(FINE, e.getMessage(), e);
                            } catch (ExecutionException e) {
                                StatusLine sl;
                                if (e.getCause() instanceof ConnectException) {
                                    sl = new StatusLine("status-offline.png", "app.offline", 0L);
                                } else {
                                    sl = new StatusLine("status-ioerror.png", "app.ioerror", 0L);
                                }
                                onError(e,sl, expire, addContributors, result);
                            } catch (TimeoutException e) {
                                LOGGER.log(FINE, e.getMessage(), e);
                            } catch (ConnectException e) {
                                onError(e, new StatusLine("status-offline.png", "app.offline", 0L), expire, addContributors, result);
                            } catch (IOException e) {
                                onError(e, new StatusLine("status-ioerror.png", "app.ioerror", 0L), expire, addContributors, result);
                            } catch (Throwable e) {
                                LOGGER.log(FINE, e.getMessage(), e);
                            } finally {
                                gcClient.close();
                            }
                        } finally {
                            inProgress.remove(accountName);
                            LOGGER.log(FINE, "Finished Health check for {0}", accountName);
                        }
                    }

                    private void onError(Throwable e, StatusLine sl, long expire, boolean addContributors, List<DataPoint> result) {
                        result.add(sl);
                        if (addContributors) {
                            for (DataPointContributor dpc : DataPointContributor.all()) {
                                try {
                                    dpc.collect(user, accountName, result);
                                } catch (Throwable e1) {
                                    LOGGER.log(FINE, e1.getMessage(), e1);
                                    if (System.currentTimeMillis() > expire) {
                                        // we have taken too long already
                                        break;
                                    }
                                }
                            }
                        }
                        statusCache.put(accountName, new StatusCacheEntry(result));
                        LOGGER.log(FINE, e.getMessage(), e);
                    }
                });
            }
        }
    }
}
