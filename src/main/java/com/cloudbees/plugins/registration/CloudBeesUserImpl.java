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

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.cloudbees.AbstractCloudBeesUser;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUserWithPassword;
import com.cloudbees.plugins.registration.grandcentral.ApiAccountNamesHandler;
import com.cloudbees.plugins.registration.grandcentral.ApiAccountServiceStatusHandler;
import com.cloudbees.plugins.registration.grandcentral.ApiUserKeysUsingAuthHandler;
import com.cloudbees.plugins.registration.grandcentral.ApiUserKeysUsingAuthResponse;
import com.cloudbees.plugins.registration.grandcentral.ApiUserProfileHandler;
import com.cloudbees.plugins.registration.grandcentral.ApiUserProfileResponse;
import com.google.common.collect.MapMaker;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.RequestBuilder;
import hudson.Extension;
import hudson.Util;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.plugins.asynchttpclient.AHCUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudBeesUserImpl extends AbstractCloudBeesUser implements CloudBeesUserWithPassword {

    private static final Logger LOGGER = Logger.getLogger(CloudBeesUserImpl.class.getName());

    private static final long serialVersionUID = 1L;
    private static final int REFRESH_SECONDS = 360;
    private final String name;
    private final Secret password;
    private volatile String apiKey;
    private volatile Secret apiSecret;
    private volatile List<CloudBeesAccount> accounts;
    private transient volatile long lastRefresh;
    private transient volatile Future<?> update = null;
    private transient volatile String uid = null;
    private transient volatile String displayName = null;
    private transient volatile String username = null;

    @DataBoundConstructor
    public CloudBeesUserImpl(CredentialsScope scope, String name, String password) {
        super(scope);
        this.name = name;
        this.password = Secret.fromString(password);
    }

    private synchronized void update() {
        if (tryUpdate()) {
            return;
        }
        if (apiKey == null || apiSecret == null || accounts == null || uid == null || username == null) {
            try {
                update.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore
            } catch (ExecutionException e) {
                // ignore
            } catch (TimeoutException e) {
                // ignore
            }
        }
    }

    private boolean tryUpdate() {
        long lastRefresh = this.lastRefresh;
        if (System.currentTimeMillis() < lastRefresh + TimeUnit.SECONDS.toMillis(REFRESH_SECONDS)) {
            return true;
        }
        if (update == null) {
            update = threadPoolForUpdating.submit(new DerivedFieldUpdater());
        }
        return false;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return name;
    }

    public Secret getPassword() {
        return password;
    }

    public String getAPIKey() {
        update();
        return apiKey;
    }

    public String getUsername() {
        update();
        return username;
    }

    public Secret getAPISecret() {
        update();
        return apiSecret;
    }

    public String getUID() {
        update();
        return uid;
    }

    public List<CloudBeesAccount> getAccounts() {
        update();
        return accounts;
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        public DescriptorImpl() {
            super();
        }

        @Override
        public String getDisplayName() {
            return Messages.CloudBeesUserImpl_DisplayName();
        }

        /**
         * validate the value for a remote (repository) location.
         */
        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckPassword(StaplerRequest req, @QueryParameter String name,
                                                 @QueryParameter String password) throws FormValidation {
            // syntax check first
            String userName = Util.nullify(name);
            String password1 = Util.nullify(password);
            if (userName == null || password1 == null) {
                return FormValidation.warning("You must provide an email and password registered with cloudbees.com");
            }

            userName = userName.trim();
            Secret secret = Secret.fromString(password1);
            CheckCacheEntry checkCacheEntry = CheckCacheEntry.get(userName);
            if (checkCacheEntry != null && checkCacheEntry.matches(secret)) {
                return FormValidation.ok();
            }
            RequestBuilder builder = new RequestBuilder("POST");
            addProxyServer(builder);
            AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                    .setRequestTimeoutInMs(25000)
                    .setProxyServer(AHCUtils.getProxyServer())
                    .build();
            AsyncHttpClient client = new AsyncHttpClient(config);
            try {
                ApiUserKeysUsingAuthHandler.executeRequest(client, userName, Secret.toString(secret)).get();
                CheckCacheEntry.put(userName, secret);
                return FormValidation.ok();
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                while (cause != null) {
                    if (cause instanceof FormValidation) {
                        throw (FormValidation) cause;
                    }
                    cause = cause.getCause();
                }
                throw e;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                while (cause != null) {
                    if (cause instanceof FormValidation) {
                        throw (FormValidation) cause;
                    }
                    cause = cause.getCause();
                }
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                return FormValidation.error(e, e.getMessage());
            } catch (IOException e) {
                return FormValidation.error(e, e.getMessage());
            } finally {
                client.close();
            }
        }

        private static final class CheckCacheEntry {
            private static final Map<String, CheckCacheEntry> checkCache = new MapMaker()
                    .concurrencyLevel(16)
                    .expiration(REFRESH_SECONDS, TimeUnit.SECONDS)
                    .makeMap();

            private static CheckCacheEntry get(String userName) {
                return CheckCacheEntry.checkCache.get(userName);
            }

            private static CheckCacheEntry put(String userName, Secret secret) {
                return CheckCacheEntry.checkCache.put(userName, new CheckCacheEntry(secret));
            }

            private static CheckCacheEntry clear(String userName) {
                return CheckCacheEntry.checkCache.remove(userName);
            }

            private final long timestamp;
            private final Secret secret;

            private CheckCacheEntry(Secret secret) {
                this.timestamp = System.currentTimeMillis();
                this.secret = secret;
            }

            public boolean matches(Secret secret) {
                return Secret.toString(this.secret).equals(Secret.toString(secret));
            }
        }

    }

    public static void addProxyServer(RequestBuilder builder) {
        builder.setProxyServer(AHCUtils.getProxyServer());
    }

    public static final ExecutorService threadPoolForUpdating = Executors
            .newCachedThreadPool(new ExceptionCatchingThreadFactory(new DaemonThreadFactory(new ThreadFactory() {
                private final ThreadFactory delegate = Executors.defaultThreadFactory();

                public Thread newThread(Runnable r) {
                    Thread thread = delegate.newThread(r);
                    thread.setName("CloudBeesUserImpl-" + thread.getName());
                    return thread;
                }
            })));

    private static final AtomicInteger errorCount = new AtomicInteger(0);


    private class DerivedFieldUpdater implements Runnable {

        private final long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);

        public void run() {
            AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                    .setRequestTimeoutInMs(25000)
                    .setProxyServer(AHCUtils.getProxyServer())
                    .build();
            AsyncHttpClient client = new AsyncHttpClient(config);
            try {
                List<CloudBeesAccount> accounts = null;
                String uid = CloudBeesUserImpl.this.uid;
                if (uid != null) {
                    // we already have a UID, assume it is correct until proved otherwise
                    try {
                        ApiUserProfileResponse profileResponse =
                                ApiUserProfileHandler.executeRequest(client, uid, name).get(
                                        remainingMillis(), TimeUnit.MILLISECONDS);
                        displayName = profileResponse.getFullName();
                        accounts = profileResponse.getAccounts();
                        if (profileResponse.getUsername() != null) {
                            username = profileResponse.getUsername();
                        }
                    } catch (ExecutionException e) {
                        LOGGER.log(Level.FINE, "Unexpected response from " + EndPoints.grandCentral(), e);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Could not connect to " + EndPoints.grandCentral(), e);
                    }
                    if (accounts == null) {
                        try {
                            accounts = ApiAccountNamesHandler.executeRequest(client, uid)
                                    .get(remainingMillis(), TimeUnit.MILLISECONDS);
                        } catch (ExecutionException e) {
                            LOGGER.log(Level.INFO, "Unexpected response from " + EndPoints.grandCentral(), e);
                            uid = null;
                        } catch (IOException e) {
                            LOGGER.log(Level.INFO, "Could not connect to " + EndPoints.grandCentral(), e);
                        }
                    }
                }
                if (CloudBeesUserImpl.this.apiKey == null || CloudBeesUserImpl.this.apiSecret == null || uid == null) {
                    // either no UID or the UID is invalid
                    ApiUserKeysUsingAuthResponse authResponse =
                            ApiUserKeysUsingAuthHandler.executeRequest(client, name, Secret.toString(password))
                                    .get(remainingMillis(), TimeUnit.MILLISECONDS);
                    CloudBeesUserImpl.this.uid = uid = authResponse.getUid();
                    CloudBeesUserImpl.this.apiKey = authResponse.getApiKey();
                    CloudBeesUserImpl.this.apiSecret = Secret.fromString(authResponse.getSecretKey());
                }
                if (accounts == null) {
                    try {
                        ApiUserProfileResponse profileResponse =
                                ApiUserProfileHandler.executeRequest(client, uid, name).get(
                                        remainingMillis(), TimeUnit.MILLISECONDS);
                        displayName = profileResponse.getFullName();
                        accounts = profileResponse.getAccounts();
                    } catch (ExecutionException e) {
                        LOGGER.log(Level.FINE, "Unexpected response from " + EndPoints.grandCentral(), e);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Could not connect to " + EndPoints.grandCentral(), e);
                    }
                    if (accounts == null) {
                        try {
                            accounts = ApiAccountNamesHandler.executeRequest(client, uid)
                                    .get(remainingMillis(), TimeUnit.MILLISECONDS);
                        } catch (ExecutionException e) {
                            LOGGER.log(Level.WARNING, "Unexpected response from " + EndPoints.grandCentral(), e);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Could not connect to " + EndPoints.grandCentral(), e);
                        }
                    }
                }
                if (accounts != null) {
                    CloudBeesUserImpl.this.accounts = accounts;
                    if (username == null && !accounts.isEmpty()) {
                        try {
                            username = ApiAccountServiceStatusHandler
                                    .executeRequest(client, uid, accounts.get(0).getName())
                                    .get(remainingMillis(), TimeUnit.MILLISECONDS);
                        } catch (ExecutionException e) {
                            LOGGER.log(Level.WARNING, "Unexpected response from " + EndPoints.grandCentral(), e);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Could not connect to " + EndPoints.grandCentral(), e);
                        }
                    }
                }
                errorCount.set(0);
                CloudBeesUserImpl.this.lastRefresh = System.currentTimeMillis();
            } catch (IOException e) {
                // back off if cannot connect
                int delay =
                        new Random().nextInt(1 << Math.max(0, Math.min(errorCount.getAndIncrement(), 10))) * 10 + 10;
                CloudBeesUserImpl.this.lastRefresh =
                        System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(REFRESH_SECONDS - delay);
                LOGGER.log(Level.WARNING,
                        "Could not connect to " + EndPoints.grandCentral() + " Checking again in " + delay + "s", e);
            } catch (InterruptedException e) {
                LOGGER.log(Level.INFO, "Interrupted while waiting for response from " + EndPoints.grandCentral(), e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    int delay = new Random().nextInt(1 << Math.max(0, Math.min(errorCount.getAndIncrement(), 10))) * 10
                            + 10;
                    CloudBeesUserImpl.this.lastRefresh =
                            System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(REFRESH_SECONDS - delay);
                    LOGGER.log(Level.WARNING,
                            "Could not connect to " + EndPoints.grandCentral() + " Checking again in " + delay + "s",
                            e);
                } else {
                    LOGGER.log(Level.WARNING, "Could not connect to {0}: {1}", new Object[] {EndPoints.grandCentral(), e});
                    LOGGER.log(Level.FINE, null, e);
                }
            } catch (TimeoutException e) {
                LOGGER.log(Level.WARNING, "Connection to " + EndPoints.grandCentral()+" timed out", e);
            } finally {
                CloudBeesUserImpl.this.update = null;
                client.close();
            }
        }

        private long remainingMillis() {
            return Math.max(10, endTime - System.currentTimeMillis());
        }

    }
}
