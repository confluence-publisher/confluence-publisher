/*
 * Copyright 2016 the original author or authors.
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

package org.sahli.confluence.publisher.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.io.InputStream;

import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluenceRestClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestFactory httpRequestFactory;

    public ConfluenceRestClient(String rootConfluenceUrl, HttpClient httpClient) {
        this(rootConfluenceUrl, httpClient, null, null);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, HttpClient httpClient, String username, String password) {
        assertMandatoryParameter(httpClient != null, "httpClient");

        this.httpRequestFactory = new HttpRequestFactory(rootConfluenceUrl, username, password);
        this.httpClient = httpClient;

        configureObjectMapper();
    }

    private void configureObjectMapper() {
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public String addPageUnderSpace(String spaceKey, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderSpaceRequest(spaceKey, title, content);
        HttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        return extractIdFromJsonNode(parseJsonResponse(response));
    }

    public String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content);
        HttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        return extractIdFromJsonNode(parseJsonResponse(response));
    }

    public void updatePage(String contentId, String title, String content, int newVersion) {
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, title, content, newVersion);
        sendRequestAndFailIfNot20x(updatePageRequest);
    }

    public void deletePage(String contentId) {
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);
        sendRequestAndFailIfNot20x(deletePageRequest);
    }

    public String getPageByTitle(String spaceKey, String title) throws NotFoundException, MultipleResultsException {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);
        HttpResponse response = sendRequestAndFailIfNot20x(pageByTitleRequest);
        JsonNode jsonNode = parseJsonResponse(response);

        int numberOfResults = jsonNode.get("size").asInt();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        return extractIdFromJsonNode(jsonNode.withArray("results").elements().next());
    }

    public void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent) {
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);
        sendRequestAndFailIfNot20x(addAttachmentRequest);
    }

    public void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent) {
        HttpPost updateAttachmentContentRequest = this.httpRequestFactory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent);
        sendRequestAndFailIfNot20x(updateAttachmentContentRequest);
    }

    public void deleteAttachment(String attachmentId) {
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);
        sendRequestAndFailIfNot20x(deleteAttachmentRequest);
    }

    public String getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);
        HttpResponse response = sendRequestAndFailIfNot20x(attachmentByFileNameRequest);

        JsonNode jsonNode = parseJsonResponse(response);

        int numberOfResults = jsonNode.get("size").asInt();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        return extractIdFromJsonNode(jsonNode.withArray("results").elements().next());
    }

    public ConfluencePage getPageWithContentAndVersionById(String contentId) {
        HttpGet pageByIdRequest = this.httpRequestFactory.getPageByIdRequest(contentId, "body.storage,version");
        HttpResponse response = sendRequestAndFailIfNot20x(pageByIdRequest);

        return extractConfluencePageWithContent(parseJsonResponse(response));
    }

    public boolean pageExistsByTitle(String spaceKey, String title) {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);
        HttpResponse response = sendRequestAndFailIfNot20x(pageByTitleRequest);
        JsonNode jsonNode = parseJsonResponse(response);

        return jsonNode.get("size").asInt() == 1;
    }

    public boolean attachmentExistsByFileName(String contentId, String attachmentFileName) {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);

        HttpResponse response = sendRequest(attachmentByFileNameRequest);
        StatusLine statusLine = response.getStatusLine();

        int statusCode = statusLine.getStatusCode();
        if (statusCode == 404) {
            return false;
        }

        if (statusCode != 200) {
            throw new RuntimeException("Response had not expected status code (200 or 404) -> "
                    + statusCode + " " + statusLine.getReasonPhrase());
        }

        JsonNode jsonNode = parseJsonResponse(response);

        return jsonNode.get("size").asInt() == 1;
    }

    private JsonNode parseJsonResponse(HttpResponse response) {
        try {
            return this.objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JSON response", e);
        }
    }

    private HttpResponse sendRequestAndFailIfNot20x(HttpRequestBase httpRequest) {
        HttpResponse response = sendRequest(httpRequest);

        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() > 206) {
            throw new RuntimeException("Response had not expected status code (between 200 and 206) -> "
                    + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        }

        return response;
    }

    private HttpResponse sendRequest(HttpRequestBase httpRequest) {
        HttpResponse response;
        try {
            response = this.httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
        }

        return response;
    }

    private static ConfluencePage extractConfluencePageWithContent(JsonNode jsonNode) {
        String id = jsonNode.get("id").asText();
        String title = jsonNode.get("title").asText();
        String content = jsonNode.path("body").path("storage").get("value").asText();
        int version = jsonNode.path("version").get("number").asInt();

        return new ConfluencePage(id, title, content, version);
    }

    private static String extractIdFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("id").asText();
    }

}
