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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the client against a local http server, as resending a request (including its body) can only be
 * verified with real requests.
 */
public class ConfluenceRestV2ClientTest {

    private HttpServer server;
    private CloseableHttpClient httpClient;
    private final Deque<StubbedResponse> stubbedResponses = new ArrayDeque<>();
    private final List<RecordedRequest> recordedRequests = new ArrayList<>();
    private final List<Long> pauses = new ArrayList<>();

    @BeforeEach
    public void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/", (exchange) -> handle(exchange));
        this.server.start();
        this.httpClient = HttpClients.createDefault();
    }

    @AfterEach
    public void stopServer() throws IOException {
        this.httpClient.close();
        this.server.stop(0);
    }

    @Test
    public void sendRequest_withTooManyRequestsResponseAndRetryAfterHeader_waitsAsRequestedAndSendsSameRequestAgain() {
        // arrange
        respondWith(429, "", "7");
        respondWith(200, "{}", null);

        // act
        confluenceRestV2Client(3).addAttachment("1234", "attachment.txt", new ByteArrayInputStream("attachment content".getBytes(UTF_8)));

        // assert
        assertThat(this.pauses, contains(7000L));
        assertThat(this.recordedRequests, hasSize(2));
        assertThat(this.recordedRequests.get(0).body, containsString("attachment content"));
        assertThat(this.recordedRequests.get(1).body, containsString("attachment content"));
        assertThat(this.recordedRequests.get(1).authorizationHeaders, hasSize(1));
    }

    @Test
    public void sendRequest_withTooManyRequestsResponseAndJsonPayload_sendsSamePayloadAgain() {
        // arrange
        respondWith(429, "", "1");
        respondWith(200, "{}", null);

        // act
        confluenceRestV2Client(3).updatePage("1234", "1", "Title", "<h1>Content</h1>", 2, "message", false);

        // assert
        assertThat(this.recordedRequests, hasSize(2));
        assertThat(this.recordedRequests.get(0).body, containsString("<h1>Content</h1>"));
        assertThat(this.recordedRequests.get(1).body, is(this.recordedRequests.get(0).body));
    }

    @Test
    public void sendRequest_withTooManyRequestsResponsesWithoutRetryAfterHeader_backsOffExponentially() {
        // arrange
        respondWith(429, "", null);
        respondWith(429, "", null);
        respondWith(429, "", null);
        respondWith(204, "", null);

        // act
        confluenceRestV2Client(3).deletePage("1234");

        // assert
        assertThat(this.pauses, contains(100L, 200L, 400L));
        assertThat(this.recordedRequests, hasSize(4));
    }

    @Test
    public void sendRequest_withRetryAfterHeaderExceedingMaximumDelay_waitsForMaximumDelayOnly() {
        // arrange
        respondWith(429, "", "86400");
        respondWith(204, "", null);

        // act
        confluenceRestV2Client(3).deletePage("1234");

        // assert
        assertThat(this.pauses, contains(60000L));
    }

    @Test
    public void sendRequest_withMoreTooManyRequestsResponsesThanMaxRetries_throwsRequestFailedException() {
        // arrange
        respondWith(429, "", null);
        respondWith(429, "", null);
        respondWith(429, "rate limited", null);

        // act
        RequestFailedException exception = assertThrows(RequestFailedException.class, () -> confluenceRestV2Client(2).deletePage("1234"));

        // assert
        assertThat(exception.getMessage(), containsString("429"));
        assertThat(this.pauses, hasSize(2));
        assertThat(this.recordedRequests, hasSize(3));
    }

    @Test
    public void getPropertyByKey_withTooManyRequestsResponse_retriesInsteadOfReportingMissingProperty() {
        // arrange
        respondWith(429, "", "1");
        respondWith(200, "{\"results\": [{\"id\": \"1\", \"key\": \"content-hash\", \"value\": \"hash\"}]}", null);

        // act
        String value = confluenceRestV2Client(3).getPropertyByKey("1234", "content-hash");

        // assert
        assertThat(value, is("hash"));
    }

    @Test
    public void getAttachments_withValidParameters_requestsMaximumPageSize() {
        // arrange
        respondWith(200, "{\"results\": [], \"_links\": {}}", null);

        // act
        List<ConfluenceAttachment> attachments = confluenceRestV2Client(0).getAttachments("1234");

        // assert
        assertThat(attachments, is(empty()));
        assertThat(this.recordedRequests.get(0).uri, is("/api/v2/pages/1234/attachments?limit=250"));
    }

    @Test
    public void getPageByTitle_calledMultipleTimesForSameSpace_looksUpSpaceIdOnlyOnce() {
        // arrange
        respondWith(200, "{\"results\": [{\"id\": \"9876\"}]}", null);
        respondWith(200, "{\"results\": [{\"id\": \"1\"}]}", null);
        respondWith(200, "{\"results\": [{\"id\": \"2\"}]}", null);
        ConfluenceRestV2Client confluenceRestV2Client = confluenceRestV2Client(0);

        // act
        String firstContentId = confluenceRestV2Client.getPageByTitle("SPACE", "1234", "First");
        String secondContentId = confluenceRestV2Client.getPageByTitle("SPACE", "1234", "Second");

        // assert
        assertThat(firstContentId, is("1"));
        assertThat(secondContentId, is("2"));
        assertThat(this.recordedRequests.stream().map((request) -> request.uri).collect(toList()), contains(
                "/api/v2/spaces?keys=SPACE",
                "/api/v2/spaces/9876/pages?title=First",
                "/api/v2/spaces/9876/pages?title=Second"
        ));
    }

    @Test
    public void deletePropertyByKey_withExistingProperty_deletesPropertyById() {
        // arrange
        respondWith(200, "{\"results\": [{\"id\": \"555\", \"key\": \"content-hash\", \"value\": \"hash\"}]}", null);
        respondWith(204, "", null);

        // act
        confluenceRestV2Client(0).deletePropertyByKey("1234", "content-hash");

        // assert
        assertThat(this.recordedRequests.stream().map((request) -> request.method + " " + request.uri).collect(toList()), contains(
                "GET /api/v2/pages/1234/properties?key=content-hash",
                "DELETE /api/v2/pages/1234/properties/555"
        ));
    }

    @Test
    public void deletePropertyByKey_withMissingProperty_sendsNoDeleteRequest() {
        // arrange
        respondWith(200, "{\"results\": []}", null);

        // act
        confluenceRestV2Client(0).deletePropertyByKey("1234", "content-hash");

        // assert
        assertThat(this.recordedRequests, hasSize(1));
    }

    private ConfluenceRestV2Client confluenceRestV2Client(int maxRetries) {
        TooManyRequestsRetry tooManyRequestsRetry = new TooManyRequestsRetry(maxRetries, 100, 60000, (millis) -> this.pauses.add(millis));

        return new ConfluenceRestV2Client("http://localhost:" + this.server.getAddress().getPort(), this.httpClient, null, "user", "password", tooManyRequestsRetry);
    }

    private void respondWith(int statusCode, String body, String retryAfter) {
        this.stubbedResponses.add(new StubbedResponse(statusCode, body, retryAfter));
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        this.recordedRequests.add(new RecordedRequest(exchange));

        StubbedResponse response = this.stubbedResponses.remove();
        if (response.retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", response.retryAfter);
        }

        byte[] body = response.body.getBytes(UTF_8);
        exchange.sendResponseHeaders(response.statusCode, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }


    private static class StubbedResponse {

        private final int statusCode;
        private final String body;
        private final String retryAfter;

        private StubbedResponse(int statusCode, String body, String retryAfter) {
            this.statusCode = statusCode;
            this.body = body;
            this.retryAfter = retryAfter;
        }

    }


    private static class RecordedRequest {

        private final String method;
        private final String uri;
        private final String body;
        private final List<String> authorizationHeaders;

        private RecordedRequest(HttpExchange exchange) throws IOException {
            this.method = exchange.getRequestMethod();
            this.uri = exchange.getRequestURI().toString();
            this.body = new String(readFully(exchange.getRequestBody()), UTF_8);
            this.authorizationHeaders = exchange.getRequestHeaders().get("Authorization");
        }

        private static byte[] readFully(InputStream inputStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            return buffer.toByteArray();
        }

    }

}
