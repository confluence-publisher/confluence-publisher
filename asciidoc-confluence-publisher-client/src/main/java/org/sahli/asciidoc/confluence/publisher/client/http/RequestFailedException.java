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
        super("request failed (" +
                "request: " + requestLog(request) + ", " +
                "response: " + responseLog(response) +
                (reason != null ? ", reason: '" + reason.getMessage() + "'" : "") +
                ")", reason);
    }

    private static String requestLog(HttpRequest request) {
        String requestLine = request.getRequestLine().getMethod() + " " + request.getRequestLine().getUri();
        String requestBody = requestBody(request);

        return requestLine + " " + requestBody;
    }

    private static String responseLog(HttpResponse response) {
        if (response == null) {
            return "<none>";
        }

        String statusLine = response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase();
        String responseBody = responseBody(response);

        return statusLine + " " + responseBody;
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
