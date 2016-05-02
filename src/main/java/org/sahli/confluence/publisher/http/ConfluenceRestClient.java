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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluenceRestClient {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestFactory httpRequestFactory;

    public ConfluenceRestClient(String rootConfluenceUrl, CloseableHttpClient httpClient) {
        this(rootConfluenceUrl, httpClient, null, null);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, CloseableHttpClient httpClient, String username, String password) {
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
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        String contentId = extractIdFromJsonNode(parseJsonResponse(response));
        closeResponse(response);

        return contentId;
    }

    public String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        String contentId = extractIdFromJsonNode(parseJsonResponse(response));
        closeResponse(response);

        return contentId;
    }

    public void updatePage(String contentId, String title, String content, int newVersion) {
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, title, content, newVersion);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(updatePageRequest);
        closeResponse(response);
    }

    public void deletePage(String contentId) {
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(deletePageRequest);
        closeResponse(response);
    }

    public String getPageByTitle(String spaceKey, String title) throws NotFoundException, MultipleResultsException {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(pageByTitleRequest);
        JsonNode jsonNode = parseJsonResponse(response);

        int numberOfResults = jsonNode.get("size").asInt();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        String contentId = extractIdFromJsonNode(jsonNode.withArray("results").elements().next());
        closeResponse(response);

        return contentId;
    }

    public void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent) {
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(addAttachmentRequest);
        closeResponse(response);
    }

    public void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent) {
        HttpPost updateAttachmentContentRequest = this.httpRequestFactory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(updateAttachmentContentRequest);
        closeResponse(response);
    }

    public void deleteAttachment(String attachmentId) {
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(deleteAttachmentRequest);
        closeResponse(response);
    }

    public ConfluenceAttachment getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(attachmentByFileNameRequest);

        JsonNode jsonNode = parseJsonResponse(response);

        int numberOfResults = jsonNode.get("size").asInt();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        ConfluenceAttachment attachmentId = extractConfluenceAttachment(jsonNode.withArray("results").elements().next());
        closeResponse(response);

        return attachmentId;
    }

    public ConfluencePage getPageWithContentAndVersionById(String contentId) {
        HttpGet pageByIdRequest = this.httpRequestFactory.getPageByIdRequest(contentId, "body.storage,version");
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(pageByIdRequest);

        ConfluencePage confluencePage = extractConfluencePageWithContent(parseJsonResponse(response));
        closeResponse(response);

        return confluencePage;
    }

    public boolean pageExistsByTitle(String spaceKey, String title) {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(pageByTitleRequest);

        JsonNode jsonNode = parseJsonResponse(response);
        boolean pageExists = jsonNode.get("size").asInt() == 1;
        closeResponse(response);

        return pageExists;
    }

    public boolean attachmentExistsByFileName(String contentId, String attachmentFileName) {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);

        CloseableHttpResponse response = sendRequest(attachmentByFileNameRequest);
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
        boolean attachmentExists = jsonNode.get("size").asInt() == 1;
        closeResponse(response);

        return attachmentExists;
    }

    public InputStream getAttachmentContent(String relativeDownloadLink) {
        HttpGet getAttachmentContentRequest = this.httpRequestFactory.getAttachmentContentRequest(relativeDownloadLink);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(getAttachmentContentRequest);

        try {
            return response.getEntity().getContent();
        } catch (IOException e) {
            throw new RuntimeException("Could not read attachment content", e);
        }
    }

    private JsonNode parseJsonResponse(HttpResponse response) {
        try {
            return this.objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JSON response", e);
        }
    }

    private CloseableHttpResponse sendRequestAndFailIfNot20x(HttpRequestBase httpRequest) {
        CloseableHttpResponse response = sendRequest(httpRequest);

        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() > 206) {
            throw new RuntimeException("Response had not expected status code (between 200 and 206) -> "
                    + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        }

        return response;
    }

    private CloseableHttpResponse sendRequest(HttpRequestBase httpRequest) {
        CloseableHttpResponse response;
        try {
            response = this.httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
        }

        return response;
    }

    public List<ConfluencePage> getChildPages(String contentId) {
        int start = 0;
        int limit = 25;

        ArrayList<ConfluencePage> childPages = new ArrayList<>();
        boolean fetchMore = true;
        while (fetchMore) {
            List<ConfluencePage> nextChildPages = getNextChildPages(contentId, limit, start);
            childPages.addAll(nextChildPages);

            start++;
            fetchMore = nextChildPages.size() == limit;
        }

        return childPages;
    }

    public List<ConfluenceAttachment> getAttachments(String contentId) {
        int start = 0;
        int limit = 25;

        ArrayList<ConfluenceAttachment> attachments = new ArrayList<>();
        boolean fetchMore = true;
        while (fetchMore) {
            List<ConfluenceAttachment> nextAttachments = getNextAttachments(contentId, limit, start);
            attachments.addAll(nextAttachments);

            start++;
            fetchMore = nextAttachments.size() == limit;
        }

        return attachments;
    }

    private List<ConfluencePage> getNextChildPages(String contentId, int limit, int start) {
        List<ConfluencePage> pages = new ArrayList<>(limit);
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(contentId, limit, start);
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(getChildPagesByIdRequest);

        JsonNode jsonNode = parseJsonResponse(response);
        jsonNode.withArray("results").forEach((page) -> pages.add(extractConfluencePageWithoutContent(page)));
        closeResponse(response);

        return pages;
    }

    private List<ConfluenceAttachment> getNextAttachments(String contentId, int limit, int start) {
        List<ConfluenceAttachment> attachments = new ArrayList<>(limit);
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, limit, start, "version");
        CloseableHttpResponse response = sendRequestAndFailIfNot20x(getAttachmentsRequest);

        JsonNode jsonNode = parseJsonResponse(response);
        jsonNode.withArray("results").forEach(attachment -> attachments.add(extractConfluenceAttachment(attachment)));
        closeResponse(response);

        return attachments;
    }

    private static void closeResponse(CloseableHttpResponse response) {
        try {
            response.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not close response", e);
        }
    }

    private static ConfluencePage extractConfluencePageWithContent(JsonNode jsonNode) {
        String id = extractIdFromJsonNode(jsonNode);
        String title = extractTitleFromJsonNode(jsonNode);
        String content = jsonNode.path("body").path("storage").get("value").asText();
        int version = extractVersionFromJsonNode(jsonNode);

        return new ConfluencePage(id, title, content, version);
    }

    private static ConfluencePage extractConfluencePageWithoutContent(JsonNode jsonNode) {
        String id = extractIdFromJsonNode(jsonNode);
        String title = extractTitleFromJsonNode(jsonNode);
        int version = extractVersionFromJsonNode(jsonNode);

        return new ConfluencePage(id, title, version);
    }

    private static ConfluenceAttachment extractConfluenceAttachment(JsonNode jsonNode) {
        String id = extractIdFromJsonNode(jsonNode);
        String title = extractTitleFromJsonNode(jsonNode);
        int version = extractVersionFromJsonNode(jsonNode);
        String relativeDownloadLink = jsonNode.path("_links").get("download").asText();

        return new ConfluenceAttachment(id, title, relativeDownloadLink, version);
    }

    private static String extractIdFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("id").asText();
    }

    private static String extractTitleFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("title").asText();
    }

    private static int extractVersionFromJsonNode(JsonNode jsonNode) {
        return jsonNode.path("version").get("number").asInt();
    }

}
