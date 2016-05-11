/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.resource.transport.http;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import org.apache.http.ssl.SSLInitializationException;
import org.gradle.internal.SystemProperties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class DefaultSslContextFactory implements SslContextFactory {
    private static final char[] EMPTY_PASSWORD = "".toCharArray();
    private static final Set<String> SSL_SYSTEM_PROPERTIES = ImmutableSet.of(
        "ssl.TrustManagerFactory.algorithm",
        "javax.net.ssl.trustStoreType",
        "javax.net.ssl.trustStore",
        "javax.net.ssl.trustStoreProvider",
        "javax.net.ssl.trustStorePassword",
        "ssl.KeyManagerFactory.algorithm",
        "javax.net.ssl.keyStoreType",
        "javax.net.ssl.keyStore",
        "javax.net.ssl.keyStoreProvider",
        "javax.net.ssl.keyStorePassword"
    );

    private LoadingCache<Map<String, String>, SSLContext> cache = CacheBuilder.newBuilder().softValues().build(new SslContextCacheLoader());

    @Override
    public SSLContext createSslContext() {
        return cache.getUnchecked(getCurrentProperties());
    }

    private Map<String, String> getCurrentProperties() {
        Map<String, String> currentProperties = new TreeMap<String, String>();
        for (String prop : SSL_SYSTEM_PROPERTIES) {
            currentProperties.put(prop, System.getProperty(prop));
        }
        currentProperties.put("java.home", SystemProperties.getInstance().getJavaHomeDir().getPath());
        return currentProperties;
    }

    private static class SslContextCacheLoader extends CacheLoader<Map<String, String>, SSLContext> {
        @Override
        public SSLContext load(Map<String, String> props) {
            try {
                SSLContext sslcontext = SSLContext.getDefault();

                return sslcontext;
            } catch (GeneralSecurityException e) {
                throw new SSLInitializationException(e.getMessage(), e);
            }
        }
    }
}
