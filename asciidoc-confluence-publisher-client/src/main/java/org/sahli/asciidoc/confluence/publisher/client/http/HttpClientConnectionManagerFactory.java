package org.sahli.asciidoc.confluence.publisher.client.http;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.TextUtils;

/**
 * Factory for {@link HttpClientConnectionManager}.
 * <p>
 * NOTE: The code below has been copied and simplified from {@link HttpClientBuilder#build()} to make config options the {@link PoolingHttpClientConnectionManager} accessible, e.g. {@link PoolingHttpClientConnectionManager#setValidateAfterInactivity(int)}
 */
public final class HttpClientConnectionManagerFactory {

    private HttpClientConnectionManagerFactory() {
    }

    static HttpClientConnectionManager getHttpClientConnectionManager(boolean disableSslVerification, boolean enableHttpClientSystemProperties) {
        LayeredConnectionSocketFactory sslSocketFactory = getSocketFactory(disableSslVerification, enableHttpClientSystemProperties);
        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslSocketFactory)
                        .build(), null, null, null, -1, TimeUnit.MILLISECONDS);
        if (enableHttpClientSystemProperties) {
            String s = System.getProperty("http.keepAlive", "true");
            if ("true".equalsIgnoreCase(s)) {
                s = System.getProperty("http.maxConnections", "5");
                final int max = Integer.parseInt(s);
                connectionManager.setDefaultMaxPerRoute(max);
                connectionManager.setMaxTotal(2 * max);
            }
        }
        // Validate inactive connections after a short time period of 100ms to avoid errors due to stale connections (e.g. closed on server side),
        // see https://issues.apache.org/jira/browse/HTTPCLIENT-1610?focusedCommentId=15748601&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-15748601
        connectionManager.setValidateAfterInactivity(100);
        return connectionManager;
    }

    private static LayeredConnectionSocketFactory getSocketFactory(boolean disableSslVerification, boolean enableHttpClientSystemProperties) {
        final String[] supportedProtocols = enableHttpClientSystemProperties ? split(System.getProperty("https.protocols")) : null;
        final String[] supportedCipherSuites = enableHttpClientSystemProperties ? split(System.getProperty("https.cipherSuites")) : null;
        if (disableSslVerification) {
            return new SSLConnectionSocketFactory(trustAllSslContext(), supportedProtocols, supportedCipherSuites, new NoopHostnameVerifier());
        } else {
            HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier(PublicSuffixMatcherLoader.getDefault());
            if (enableHttpClientSystemProperties) {
                return new SSLConnectionSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault(), supportedProtocols, supportedCipherSuites,
                        hostnameVerifier);
            } else {
                return new SSLConnectionSocketFactory(SSLContexts.createDefault(), hostnameVerifier);
            }
        }
    }

    private static SSLContext trustAllSslContext() {
        try {
            return new SSLContextBuilder().loadTrustMaterial((chain, authType) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Could not create trust-all SSL context", e);
        }
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }
}
