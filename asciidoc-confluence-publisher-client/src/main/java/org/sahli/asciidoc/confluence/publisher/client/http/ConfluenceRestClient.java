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

package org.sahli.asciidoc.confluence.publisher.client.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluenceRestClient implements ConfluenceClient {

    private final String rootConfluenceUrl;
    private final CloseableHttpClient httpClient;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestFactory httpRequestFactory;

    public ConfluenceRestClient(String rootConfluenceUrl, CloseableHttpClient httpClient) {
        this(rootConfluenceUrl, httpClient, null, null);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, CloseableHttpClient httpClient, String username, String password) {
        AssertUtils.assertMandatoryParameter(httpClient != null, "httpClient");

        this.rootConfluenceUrl = rootConfluenceUrl;
        this.httpClient = httpClient;
        this.username = username;
        this.password = password;

        this.httpRequestFactory = new HttpRequestFactory(rootConfluenceUrl);
        configureObjectMapper();
    }

    private void configureObjectMapper() {
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Override
    public String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content);

        return sendRequestAndFailIfNot20x(addPageUnderSpaceRequest, (response) -> {
            String contentId = extractIdFromJsonNode(parseJsonResponse(response));

            return contentId;
        });
    }

    @Override
    public void updatePage(String contentId, String ancestorId, String title, String content, int newVersion) {
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, ancestorId, title, content, newVersion);
        sendRequestAndFailIfNot20x(updatePageRequest);
    }

    @Override
    public void deletePage(String contentId) {
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);
        sendRequestAndFailIfNot20x(deletePageRequest);
    }

    @Override
    public String getPageByTitle(String spaceKey, String title) throws NotFoundException, MultipleResultsException {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);

        return sendRequestAndFailIfNot20x(pageByTitleRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);

            int numberOfResults = jsonNode.get("size").asInt();
            if (numberOfResults == 0) {
                throw new NotFoundException();
            }

            if (numberOfResults > 1) {
                throw new MultipleResultsException();
            }

            String contentId = extractIdFromJsonNode(jsonNode.withArray("results").elements().next());

            return contentId;
        });
    }

    @Override
    public void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent) {
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);
        sendRequestAndFailIfNot20x(addAttachmentRequest, (response) -> {
            closeInputStream(attachmentContent);

            return null;
        });
    }

    @Override
    public void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent) {
        HttpPost updateAttachmentContentRequest = this.httpRequestFactory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent);
        sendRequestAndFailIfNot20x(updateAttachmentContentRequest, (response) -> {
            closeInputStream(attachmentContent);

            return null;
        });
    }

    @Override
    public void deleteAttachment(String attachmentId) {
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);
        sendRequestAndFailIfNot20x(deleteAttachmentRequest);
    }

    @Override
    public ConfluenceAttachment getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName, "version");

        return sendRequestAndFailIfNot20x(attachmentByFileNameRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);

            int numberOfResults = jsonNode.get("size").asInt();
            if (numberOfResults == 0) {
                throw new NotFoundException();
            }

            if (numberOfResults > 1) {
                throw new MultipleResultsException();
            }

            ConfluenceAttachment attachmentId = extractConfluenceAttachment(jsonNode.withArray("results").elements().next());

            return attachmentId;
        });
    }

    @Override
    public ConfluencePage getPageWithContentAndVersionById(String contentId) {
        HttpGet pageByIdRequest = this.httpRequestFactory.getPageByIdRequest(contentId, "body.storage,version");

        return sendRequestAndFailIfNot20x(pageByIdRequest, (response) -> {
            ConfluencePage confluencePage = extractConfluencePageWithContent(parseJsonResponse(response));

            return confluencePage;
        });
    }

    @Override
    public boolean pageExistsByTitle(String spaceKey, String title) {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);

        return sendRequestAndFailIfNot20x(pageByTitleRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);
            boolean pageExists = jsonNode.get("size").asInt() == 1;

            return pageExists;
        });
    }

    @Override
    public boolean attachmentExistsByFileName(String contentId, String attachmentFileName) {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName, null);

        return sendRequest(attachmentByFileNameRequest, (response) -> {
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

            return attachmentExists;
        });
    }

    @Override
    public InputStream getAttachmentContent(String relativeDownloadLink) {
        HttpGet getAttachmentContentRequest = this.httpRequestFactory.getAttachmentContentRequest(relativeDownloadLink);

        return sendRequestAndFailIfNot20x(getAttachmentContentRequest, (response) -> {
            try {
                return response.getEntity().getContent();
            } catch (IOException e) {
                throw new RuntimeException("Could not read attachment content", e);
            }
        });
    }

    private JsonNode parseJsonResponse(HttpResponse response) {
        try {
            return this.objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JSON response", e);
        }
    }

    private void sendRequestAndFailIfNot20x(HttpRequestBase httpRequest) {
        sendRequestAndFailIfNot20x(httpRequest, (response) -> null);
    }

    private <T> T sendRequestAndFailIfNot20x(HttpRequestBase httpRequest, Function<HttpResponse, T> responseHandler) {
        return sendRequest(httpRequest, (response) -> {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() > 206) {
                throw new RuntimeException(""
                        + statusLine.getStatusCode()
                        + " "
                        + statusLine.getReasonPhrase()
                        + " "
                        + httpRequest.getMethod()
                        + " "
                        + httpRequest.getURI().toString()
                        + " "
                        + failedResponseContent(response)
                );
            }

            return responseHandler.apply(response);
        });
    }

    <T> T sendRequest(HttpRequestBase httpRequest, Function<HttpResponse, T> responseHandler) {
        CloseableHttpResponse response = null;

        try {
            response = this.httpClient.execute(httpRequest, httpContext());

            return responseHandler.apply(response);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static String failedResponseContent(HttpResponse response) {
        try {
            return inputStreamAsString(response.getEntity().getContent());
        } catch (Exception ignored) {
            return "";
        }
    }

    private HttpContext httpContext() {
        BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
        if (isNotBlank(this.username) && this.password != null) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);
            HttpHost httpHost = HttpHost.create(this.rootConfluenceUrl);
            AuthScope authScope = new AuthScope(httpHost);
            basicCredentialsProvider.setCredentials(authScope, credentials);

            BasicAuthCache basicAuthCache = new BasicAuthCache();
            basicAuthCache.put(httpHost, new BasicScheme());

            HttpClientContext httpClientContext = HttpClientContext.create();
            httpClientContext.setCredentialsProvider(basicCredentialsProvider);
            httpClientContext.setAuthCache(basicAuthCache);

            return httpClientContext;
        } else {
            return null;
        }
    }

    @Override
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

    @Override
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
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(contentId, limit, start, "version");

        return sendRequestAndFailIfNot20x(getChildPagesByIdRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);
            jsonNode.withArray("results").forEach((page) -> pages.add(extractConfluencePageWithoutContent(page)));

            return pages;
        });
    }

    private List<ConfluenceAttachment> getNextAttachments(String contentId, int limit, int start) {
        List<ConfluenceAttachment> attachments = new ArrayList<>(limit);
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, limit, start, "version");

        return sendRequestAndFailIfNot20x(getAttachmentsRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);
            jsonNode.withArray("results").forEach(attachment -> attachments.add(extractConfluenceAttachment(attachment)));

            return attachments;
        });
    }

    @Override
    public String getSpaceContentId(String spaceKey) {
        HttpGet getSpaceContentIdRequest = this.httpRequestFactory.getSpaceContentIdRequest(spaceKey);

        return sendRequestAndFailIfNot20x(getSpaceContentIdRequest, (response) -> {
            String spaceContentId = extractIdFromJsonNode(parseJsonResponse(response));

            return spaceContentId;
        });
    }

    @Override
    public void setPropertyByKey(String contentId, String key, String value) {
        HttpPost setPropertyByKeyRequest = this.httpRequestFactory.setPropertyByKeyRequest(contentId, key, value);
        sendRequestAndFailIfNot20x(setPropertyByKeyRequest);
    }

    @Override
    public String getPropertyByKey(String contentId, String key) {
        HttpGet propertyByKeyRequest = this.httpRequestFactory.getPropertyByKeyRequest(contentId, key);

        return sendRequestAndFailIfNot20x(propertyByKeyRequest, (response) -> {
            String propertyValue = extractPropertyValueFromJsonNode(parseJsonResponse(response));

            return propertyValue;
        });
    }

    @Override
    public void deletePropertyByKey(String contentId, String key) {
        HttpDelete deletePropertyByKeyRequest = this.httpRequestFactory.deletePropertyByKeyRequest(contentId, key);
        sendRequestAndFailIfNot20x(deletePropertyByKeyRequest);
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

    private static String extractPropertyValueFromJsonNode(JsonNode jsonNode) {
        return jsonNode.path("value").asText();
    }

    private static void closeInputStream(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException("Exception while closing input stream", e);
        }
    }

}
