/*
 * Copyright 2017 the original author or authors.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import java.io.InputStream;
import java.nio.charset.Charset;

import static java.nio.charset.Charset.defaultCharset;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 * @author Laurent Verbruggen
 */
@SuppressWarnings("WeakerAccess")
public class RequestFailedException extends RuntimeException {

    RequestFailedException(HttpRequest request, HttpResponse response, Exception reason) {
        super(buildMessage(request, response, reason), reason);
    }

    private static String buildMessage(HttpRequest request, HttpResponse response, Exception reason) {
        String requestBody = requestBody(request);
        String responseBody = response != null ? responseBody(response) : "<none>";

        String requestLog = statusLine(request) + " " + requestBody;
        String responseLog = response != null ? statusLine(response) + " " + responseBody : responseBody;
        String reasonLog = reason != null ? reason.getMessage() : "<none>";

        return reasonFromResponseOrDefault(responseBody, "request failed") + " (" +
                "request: " + requestLog + ", " +
                "response: " + responseLog + ", " +
                "reason: " + reasonLog +
                ")";
    }

    private static String reasonFromResponseOrDefault(String responseBody, String defaultReason) {
        try {
            JsonNode responseNode = new ObjectMapper().readTree(responseBody);
            JsonNode messageNode = responseNode.get("message");

            if (messageNode != null && messageNode.asText() != null) {
                return messageNode.asText();
            }
        } catch (JsonProcessingException ignored) {
        }

        return defaultReason;
    }

    private static String statusLine(HttpRequest request) {
        return request.getRequestLine().getMethod() + " " + request.getRequestLine().getUri();
    }

    private static String statusLine(HttpResponse response) {
        return response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase();
    }

    private static String requestBody(HttpRequest request) {
        return request instanceof HttpEntityEnclosingRequest ? entityAsString(((HttpEntityEnclosingRequest) request).getEntity()) : "<empty body>";
    }

    private static String responseBody(HttpResponse response) {
        return entityAsString(response.getEntity());
    }

    private static String entityAsString(HttpEntity entity) {
        try {
            InputStream content = entity.getContent();
            Charset encoding = entity.getContentEncoding() == null ? defaultCharset() : Charset.forName(entity.getContentEncoding().getValue());
            String contentString = inputStreamAsString(content, encoding);

            return contentString;
        } catch (Exception ignored) {
            return "<empty body>";
        }
    }
}
