/*
 * Copyright 2016-2017 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client.http;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.DateUtils;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.http.HttpHeaders.RETRY_AFTER;

/**
 * Decides whether and when a request answered with "429 Too Many Requests" is sent again. The delay requested by the
 * server via the Retry-After header is honoured, with exponential backoff as fallback.
 */
class TooManyRequestsRetry {

    static final String MAX_RETRIES_SYSTEM_PROPERTY = "asciidoc-confluence-publisher.maxRetriesOnTooManyRequests";

    private static final Logger LOGGER = Logger.getLogger(TooManyRequestsRetry.class.getName());

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int DEFAULT_MAX_RETRIES = 6;
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 15 * 1000L;
    private static final long DEFAULT_MAX_DELAY_MILLIS = 15 * 60 * 1000L;

    private final int maxRetries;
    private final long initialBackoffMillis;
    private final long maxDelayMillis;
    private final Sleeper sleeper;

    TooManyRequestsRetry(int maxRetries, long initialBackoffMillis, long maxDelayMillis, Sleeper sleeper) {
        this.maxRetries = maxRetries;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.sleeper = sleeper;
    }

    static TooManyRequestsRetry defaultTooManyRequestsRetry() {
        return new TooManyRequestsRetry(Integer.getInteger(MAX_RETRIES_SYSTEM_PROPERTY, DEFAULT_MAX_RETRIES), DEFAULT_INITIAL_BACKOFF_MILLIS, DEFAULT_MAX_DELAY_MILLIS, (millis) -> Thread.sleep(millis));
    }

    boolean shouldRetry(HttpRequestBase request, HttpResponse response, int retriesSoFar) {
        return retriesSoFar < this.maxRetries
                && response != null
                && response.getStatusLine() != null
                && response.getStatusLine().getStatusCode() == TOO_MANY_REQUESTS
                && canBeSentAgain(request);
    }

    long delayMillis(HttpResponse response, int retriesSoFar) {
        Long retryAfterMillis = retryAfterMillis(response);
        long delayMillis = retryAfterMillis != null ? retryAfterMillis : this.initialBackoffMillis << Math.min(retriesSoFar, 20);

        return Math.max(0, Math.min(delayMillis, this.maxDelayMillis));
    }

    void pause(HttpRequestBase request, long delayMillis, int retriesSoFar) {
        LOGGER.warning("Confluence answered 429 Too Many Requests for " + request.getMethod() + " " + request.getURI()
                + ", waiting " + (delayMillis / 1000) + " s before retry " + (retriesSoFar + 1) + " of " + this.maxRetries);

        try {
            this.sleeper.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry request after 429 Too Many Requests", e);
        }
    }

    // enable level FINE for this class to get one line per request sent (e.g. for counting requests of a publish run)
    static void logRequest(HttpRequestBase request, HttpResponse response) {
        if (LOGGER.isLoggable(Level.FINE) && response != null && response.getStatusLine() != null) {
            LOGGER.fine(request.getMethod() + " " + request.getURI() + " -> " + response.getStatusLine().getStatusCode());
        }
    }

    private static boolean canBeSentAgain(HttpRequestBase request) {
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();

            return entity == null || entity.isRepeatable();
        }

        return true;
    }

    private static Long retryAfterMillis(HttpResponse response) {
        Header retryAfterHeader = response.getFirstHeader(RETRY_AFTER);
        if (retryAfterHeader == null || retryAfterHeader.getValue() == null) {
            return null;
        }

        String retryAfter = retryAfterHeader.getValue().trim();
        try {
            return Long.parseLong(retryAfter) * 1000L;
        } catch (NumberFormatException ignored) {
        }

        Date retryAt = DateUtils.parseDate(retryAfter);

        return retryAt != null ? retryAt.getTime() - System.currentTimeMillis() : null;
    }


    interface Sleeper {

        void sleep(long millis) throws InterruptedException;

    }

}
