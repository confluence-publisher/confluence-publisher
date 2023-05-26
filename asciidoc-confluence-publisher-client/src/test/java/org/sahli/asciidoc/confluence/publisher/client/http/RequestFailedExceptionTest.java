/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.client.http;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestFailedExceptionTest {

    @Test
    public void getMessage_exceptionWithGetRequestAndResponse_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = mockResponse(400, "bad request", null);

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: 400 bad request <empty body>, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithPostRequestAndResponse_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("POST", "http://localhost/test", "request body");
        HttpResponse response = mockResponse(400, "bad request", null);

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: POST http://localhost/test request body, " +
                "response: 400 bad request <empty body>, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithResponseWithJsonBodyWithMessage_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = mockResponse(400, "bad request", "{\"message\": \"reason in response\"}");

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("reason in response (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: 400 bad request {\"message\": \"reason in response\"}, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithResponseWithJsonBodyWithoutMessage_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = mockResponse(400, "bad request", "{\"some\": \"json\"}");

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: 400 bad request {\"some\": \"json\"}, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithResponseWithNonJsonBody_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = mockResponse(400, "bad request", "response body");

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: 400 bad request response body, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithNullResponse_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = null;

        RequestFailedException requestFailedException = new RequestFailedException(request, response, null);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: <none>, " +
                "reason: <none>" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithReason_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = null;
        Exception reason = new Exception("reason from exception");

        RequestFailedException requestFailedException = new RequestFailedException(request, response, reason);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: <none>, " +
                "reason: reason from exception" +
                ")")));
    }

    @Test
    public void getMessage_exceptionWithoutReason_returnsCorrectMessage() {
        // arrange
        HttpRequest request = mockRequest("GET", "http://localhost/test", null);
        HttpResponse response = null;
        Exception reason = null;

        RequestFailedException requestFailedException = new RequestFailedException(request, response, reason);

        // act
        String message = requestFailedException.getMessage();

        // assert
        assertThat(message, is(equalTo("request failed (" +
                "request: GET http://localhost/test <empty body>, " +
                "response: <none>, " +
                "reason: <none>" +
                ")")));
    }

    private static HttpRequest mockRequest(String method, String uri, String requestBody) {
        if (requestBody != null) {
            RequestLine requestLine = mockRequestLine(method, uri);
            HttpEntity httpEntity = mockHttpEntity(requestBody);

            HttpEntityEnclosingRequest request = mock(HttpEntityEnclosingRequest.class);
            when(request.getRequestLine()).thenReturn(requestLine);
            when(request.getEntity()).thenReturn(httpEntity);

            return request;
        } else {
            RequestLine requestLine = mockRequestLine(method, uri);

            HttpRequest request = mock(HttpRequest.class);
            when(request.getRequestLine()).thenReturn(requestLine);

            return request;
        }
    }

    private static HttpResponse mockResponse(int statusCode, String statusReason, String responseBody) {
        StatusLine statusLine = mockStatusLine(statusCode, statusReason);
        HttpEntity httpEntity = responseBody != null ? mockHttpEntity(responseBody) : null;

        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(httpEntity);

        return response;
    }

    private static RequestLine mockRequestLine(String method, String uri) {
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn(method);
        when(requestLine.getUri()).thenReturn(uri);

        return requestLine;
    }

    private static StatusLine mockStatusLine(int statusCode, String statusReason) {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(statusLine.getReasonPhrase()).thenReturn(statusReason);

        return statusLine;
    }

    private static HttpEntity mockHttpEntity(String body) {
        try {
            HttpEntity httpEntity = mock(HttpEntity.class);
            when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));

            return httpEntity;
        } catch (IOException e) {
            throw new IllegalStateException("unable to mock http entity", e);
        }
    }
}