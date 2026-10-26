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

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TooManyRequestsRetryTest {

    private static final long INITIAL_BACKOFF_MILLIS = 1000;
    private static final long MAX_DELAY_MILLIS = 60 * 1000;
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private final List<Long> pauses = new ArrayList<>();

    @AfterEach
    public void clearInterruptedFlag() {
        Thread.interrupted();
    }

    @Test
    public void shouldRetry_withTooManyRequestsAndRetriesLeft_returnsTrue() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(new HttpGet("http://confluence.com"), response(429), 1);

        // assert
        assertThat(shouldRetry, is(true));
    }

    @Test
    public void shouldRetry_withMaxRetriesReached_returnsFalse() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(new HttpGet("http://confluence.com"), response(429), 2);

        // assert
        assertThat(shouldRetry, is(false));
    }

    @Test
    public void shouldRetry_withZeroMaxRetries_returnsFalse() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(0);

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(new HttpGet("http://confluence.com"), response(429), 0);

        // assert
        assertThat(shouldRetry, is(false));
    }

    @Test
    public void shouldRetry_withStatusOtherThanTooManyRequests_returnsFalse() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(new HttpGet("http://confluence.com"), response(503), 0);

        // assert
        assertThat(shouldRetry, is(false));
    }

    @Test
    public void shouldRetry_withNullResponse_returnsFalse() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(new HttpGet("http://confluence.com"), null, 0);

        // assert
        assertThat(shouldRetry, is(false));
    }

    @Test
    public void shouldRetry_withRepeatableEntity_returnsTrue() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);
        HttpPost request = new HttpPost("http://confluence.com");
        request.setEntity(new ByteArrayEntity("{}".getBytes()));

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(request, response(429), 0);

        // assert
        assertThat(shouldRetry, is(true));
    }

    @Test
    public void shouldRetry_withNonRepeatableEntity_returnsFalse() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);
        HttpPost request = new HttpPost("http://confluence.com");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream("{}".getBytes())));

        // act
        boolean shouldRetry = tooManyRequestsRetry.shouldRetry(request, response(429), 0);

        // assert
        assertThat(shouldRetry, is(false));
    }

    @Test
    public void delayMillis_withRetryAfterInSeconds_returnsRetryAfter() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429, "5"), 0);

        // assert
        assertThat(delayMillis, is(5000L));
    }

    @Test
    public void delayMillis_withRetryAfterAsHttpDate_returnsTimeUntilDate() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);
        String retryAt = DateUtils.formatDate(Date.from(NOW.plusSeconds(30)));

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429, retryAt), 0);

        // assert
        assertThat(delayMillis, is(30000L));
    }

    @Test
    public void delayMillis_withRetryAfterAsHttpDateInThePast_returnsZero() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);
        String retryAt = DateUtils.formatDate(Date.from(NOW.minusSeconds(30)));

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429, retryAt), 0);

        // assert
        assertThat(delayMillis, is(0L));
    }

    @Test
    public void delayMillis_withRetryAfterAboveMaxDelay_returnsMaxDelay() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429, "3600"), 0);

        // assert
        assertThat(delayMillis, is(MAX_DELAY_MILLIS));
    }

    @Test
    public void delayMillis_withoutRetryAfter_returnsExponentialBackoff() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(6);

        // act
        long firstDelayMillis = tooManyRequestsRetry.delayMillis(response(429), 0);
        long secondDelayMillis = tooManyRequestsRetry.delayMillis(response(429), 1);
        long thirdDelayMillis = tooManyRequestsRetry.delayMillis(response(429), 2);

        // assert
        assertThat(firstDelayMillis, is(1000L));
        assertThat(secondDelayMillis, is(2000L));
        assertThat(thirdDelayMillis, is(4000L));
    }

    @Test
    public void delayMillis_withoutRetryAfterAndManyRetries_returnsMaxDelay() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(100);

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429), 99);

        // assert
        assertThat(delayMillis, is(MAX_DELAY_MILLIS));
    }

    @Test
    public void delayMillis_withMalformedRetryAfter_returnsExponentialBackoff() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        long delayMillis = tooManyRequestsRetry.delayMillis(response(429, "soon"), 1);

        // assert
        assertThat(delayMillis, is(2000L));
    }

    @Test
    public void pause_withDelay_sleepsForDelay() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = tooManyRequestsRetry(2);

        // act
        tooManyRequestsRetry.pause(new HttpGet("http://confluence.com"), 1500, 0);

        // assert
        assertThat(this.pauses, contains(1500L));
    }

    @Test
    public void pause_whenInterrupted_throwsRuntimeExceptionAndKeepsInterruptedFlag() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = new TooManyRequestsRetry(2, INITIAL_BACKOFF_MILLIS, MAX_DELAY_MILLIS, (millis) -> {
            throw new InterruptedException();
        }, Clock.fixed(NOW, UTC));

        // act
        assertThrows(RuntimeException.class, () -> tooManyRequestsRetry.pause(new HttpGet("http://confluence.com"), 1500, 0));

        // assert
        assertThat(Thread.currentThread().isInterrupted(), is(true));
    }

    @Test
    public void tooManyRequestsRetry_withoutMaxRetries_retriesSixTimes() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = TooManyRequestsRetry.tooManyRequestsRetry(null);
        HttpRequestBase request = new HttpGet("http://confluence.com");

        // act + assert
        assertThat(tooManyRequestsRetry.shouldRetry(request, response(429), 5), is(true));
        assertThat(tooManyRequestsRetry.shouldRetry(request, response(429), 6), is(false));
    }

    @Test
    public void tooManyRequestsRetry_withMaxRetries_retriesGivenNumberOfTimes() {
        // arrange
        TooManyRequestsRetry tooManyRequestsRetry = TooManyRequestsRetry.tooManyRequestsRetry(1);
        HttpRequestBase request = new HttpGet("http://confluence.com");

        // act + assert
        assertThat(tooManyRequestsRetry.shouldRetry(request, response(429), 0), is(true));
        assertThat(tooManyRequestsRetry.shouldRetry(request, response(429), 1), is(false));
    }

    private TooManyRequestsRetry tooManyRequestsRetry(int maxRetries) {
        return new TooManyRequestsRetry(maxRetries, INITIAL_BACKOFF_MILLIS, MAX_DELAY_MILLIS, (millis) -> this.pauses.add(millis), Clock.fixed(NOW, UTC));
    }

    private static HttpResponse response(int statusCode) {
        return new BasicHttpResponse(HttpVersion.HTTP_1_1, statusCode, null);
    }

    private static HttpResponse response(int statusCode, String retryAfter) {
        HttpResponse response = response(statusCode);
        response.addHeader("Retry-After", retryAfter);

        return response;
    }

}
