/*
 * Copyright 2025 the original author or authors.
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
import com.google.common.util.concurrent.RateLimiter;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.client.config.CookieSpecs.STANDARD;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Christian Stettler
 */
public class ConfluenceRestV2Client implements ConfluenceClient {

    private final CloseableHttpClient httpClient;
    private final String username;
    private final String passwordOrPersonalAccessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestV2Factory httpRequestV2Factory;
    private final RateLimiter rateLimiter;

    public ConfluenceRestV2Client(String rootConfluenceUrl, boolean disableSslVerification, boolean enableHttpClientSystemProperties, Double maxRequestsPerSecond, Integer connectionTTL, String username, String passwordOrPersonalAccessToken) {
        this(rootConfluenceUrl, null, disableSslVerification, enableHttpClientSystemProperties, maxRequestsPerSecond, connectionTTL, username, passwordOrPersonalAccessToken);
    }

    public ConfluenceRestV2Client(String rootConfluenceUrl, ProxyConfiguration proxyConfiguration, boolean disableSslVerification, boolean enableHttpClientSystemProperties, Double maxRequestsPerSecond, Integer connectionTTL, String username, String passwordOrPersonalAccessToken) {
        this(rootConfluenceUrl, defaultHttpClient(proxyConfiguration, disableSslVerification, enableHttpClientSystemProperties, connectionTTL), maxRequestsPerSecond, username,
                passwordOrPersonalAccessToken);
    }

    public ConfluenceRestV2Client(String rootConfluenceUrl, CloseableHttpClient httpClient, Double maxRequestsPerSecond, String username, String passwordOrPersonalAccessToken) {
        assertMandatoryParameter(httpClient != null, "httpClient");

        this.httpClient = httpClient;
        this.rateLimiter = maxRequestsPerSecond != null ? RateLimiter.create(maxRequestsPerSecond) : null;
        this.username = username;
        this.passwordOrPersonalAccessToken = passwordOrPersonalAccessToken;

        this.httpRequestV2Factory = new HttpRequestV2Factory(rootConfluenceUrl);
        configureObjectMapper();
    }

    private void configureObjectMapper() {
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Override
    public String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content, String versionMessage) {
        HttpGet lookupSpaceIdRequest = this.httpRequestV2Factory.lookupSpaceIdRequest(spaceKey);
        String spaceId = sendRequestAndFailIfNot20x(lookupSpaceIdRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);

            return extractMandatorySingleArrayElement(jsonNode, "results").get("id").asText();
        });

        HttpPost addPageUnderSpaceRequest = this.httpRequestV2Factory.addPageUnderAncestorRequest(spaceId, ancestorId, title, content, versionMessage);

        return sendRequestAndFailIfNot20x(addPageUnderSpaceRequest, (response) -> {
            String contentId = extractIdFromJsonNode(parseJsonResponse(response));

            return contentId;
        });
    }

    @Override
    public void updatePage(String contentId, String ancestorId, String title, String content, int newVersion, String versionMessage, boolean notifyWatchers) {
        HttpPut updatePageRequest = this.httpRequestV2Factory.updatePageRequest(contentId, ancestorId, title, content, newVersion, versionMessage, notifyWatchers);
        sendRequestAndFailIfNot20x(updatePageRequest);
    }

    @Override
    public void deletePage(String contentId) {
        HttpDelete deletePageRequest = this.httpRequestV2Factory.deletePageRequest(contentId);
        sendRequestAndFailIfNot20x(deletePageRequest);
    }

    @Override
    public String getPageByTitle(String spaceKey, String ancestorId, String title) throws NotFoundException, MultipleResultsException {
        HttpGet lookupSpaceIdRequest = this.httpRequestV2Factory.lookupSpaceIdRequest(spaceKey);
        String spaceId = sendRequestAndFailIfNot20x(lookupSpaceIdRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);

            return extractMandatorySingleArrayElement(jsonNode, "results").get("id").asText();
        });

        HttpGet pageByTitleRequest = this.httpRequestV2Factory.getPageByTitleRequest(spaceId, title);

        return sendRequestAndFailIfNot20x(pageByTitleRequest, (response) -> {
            JsonNode responseNode = parseJsonResponse(response);
            JsonNode pageNode = extractMandatorySingleArrayElement(responseNode, "results");
            String contentId = extractIdFromJsonNode(pageNode);

            return contentId;
        });
    }

    @Override
    public void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent) {
        HttpPost addAttachmentRequest = this.httpRequestV2Factory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);
        sendRequestAndFailIfNot20x(addAttachmentRequest, (response) -> {
            closeInputStream(attachmentContent);

            return null;
        });
    }

    @Override
    public void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent, boolean notifyWatchers) {
        HttpPost updateAttachmentContentRequest = this.httpRequestV2Factory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent, notifyWatchers);
        sendRequestAndFailIfNot20x(updateAttachmentContentRequest, (response) -> {
            closeInputStream(attachmentContent);

            return null;
        });
    }

    @Override
    public void deleteAttachment(String attachmentId) {
        HttpDelete deleteAttachmentRequest = this.httpRequestV2Factory.deleteAttachmentRequest(attachmentId);
        sendRequestAndFailIfNot20x(deleteAttachmentRequest);
    }

    @Override
    public ConfluenceAttachment getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException {
        HttpGet attachmentByFileNameRequest = this.httpRequestV2Factory.getAttachmentByFileNameRequest(contentId, attachmentFileName, null);

        return sendRequestAndFailIfNot20x(attachmentByFileNameRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);
            JsonNode resultsNode = extractMandatorySingleArrayElement(jsonNode, "results");
            ConfluenceAttachment attachment = extractConfluenceAttachment(resultsNode);
            return attachment;
        });
    }

    @Override
    public ConfluencePage getPageWithContentAndVersionById(String contentId) {
        // TODO remove content from confluence page, is not provided by response and not used
        HttpGet pageByIdRequest = this.httpRequestV2Factory.getPageByIdRequest(contentId, null);

        return sendRequestAndFailIfNot20x(pageByIdRequest, (response) -> {
            ConfluencePage confluencePage = extractConfluencePageWithContent(parseJsonResponse(response));

            return confluencePage;
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

    private <T> T sendRequestAndFailIfNot20x(HttpRequestBase request, Function<HttpResponse, T> responseHandler) {
        return sendRequest(request, (response) -> {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() > 206) {
                throw new RequestFailedException(request, response, null);
            }

            return responseHandler.apply(response);
        });
    }

    <T> T sendRequest(HttpRequestBase httpRequest, Function<HttpResponse, T> responseHandler) {
        httpRequest.addHeader(AUTHORIZATION, authorizationHeaderValue(this.username, this.passwordOrPersonalAccessToken));

        if (this.rateLimiter != null) {
            this.rateLimiter.acquire(1);
        }

        try (CloseableHttpResponse response = this.httpClient.execute(httpRequest)) {
            return responseHandler.apply(response);
        } catch (IOException e) {
            throw new RequestFailedException(httpRequest, null, e);
        }
    }

    @Override
    public List<ConfluencePage> getChildPages(String contentId) {
        int limit = 25;
        ArrayList<ConfluencePage> childPages = new ArrayList<>();

        HttpGet getChildPagesByIdRequest = this.httpRequestV2Factory.getChildPagesByIdRequest(contentId, limit, -1, null);
        String nextLink = sendRequestAndFailIfNot20x(getChildPagesByIdRequest, (response) -> extractChildPagesAndNextLink(response, childPages));

        while (nextLink != null) {
            HttpGet getNextChildPagesByIdRequest = this.httpRequestV2Factory.getNextChildPagesByIdRequest(nextLink);
            nextLink = sendRequestAndFailIfNot20x(getNextChildPagesByIdRequest, (response) -> extractChildPagesAndNextLink(response, childPages));
        }

        return childPages;
    }

    @Override
    public List<ConfluenceAttachment> getAttachments(String contentId) {
        int limit = 2;
        ArrayList<ConfluenceAttachment> attachments = new ArrayList<>();

        HttpGet getAttachmentsRequest = this.httpRequestV2Factory.getAttachmentsRequest(contentId, limit, -1, null);
        String nextLink = sendRequestAndFailIfNot20x(getAttachmentsRequest, (response) -> extractAttachmentsAndNextLink(response, attachments));

        while (nextLink != null) {
            HttpGet getNextAttachmentsRequest = this.httpRequestV2Factory.getNextAttachmentsRequest(nextLink);
            nextLink = sendRequestAndFailIfNot20x(getNextAttachmentsRequest, (response) -> extractAttachmentsAndNextLink(response, attachments));
        }

        return attachments;
    }

    @Override
    public void setPropertyByKey(String contentId, String key, String value) {
        HttpGet lookupPropertyIdByKeyRequest = this.httpRequestV2Factory.lookupPropertyIdByKey(contentId, key);
        sendRequestAndFailIfNot20x(lookupPropertyIdByKeyRequest, (response) -> {
            JsonNode jsonNode = parseJsonResponse(response);

            extractOptionalSingleArrayElement(jsonNode, "results").ifPresent((resultNode) -> {
                String propertyId = resultNode.get("id").asText();
                HttpDelete deletePropertyByKeyRequest = this.httpRequestV2Factory.deletePropertyByKeyRequest(contentId, propertyId);
                sendRequestAndFailIfNot20x(deletePropertyByKeyRequest);
            });

            return null;
        });

        HttpPost setPropertyByKeyRequest = this.httpRequestV2Factory.setPropertyByKeyRequest(contentId, key, value);
        sendRequestAndFailIfNot20x(setPropertyByKeyRequest);
    }

    @Override
    public String getPropertyByKey(String contentId, String key) {
        HttpGet propertyByKeyRequest = this.httpRequestV2Factory.getPropertyByKeyRequest(contentId, key);

        return sendRequest(propertyByKeyRequest, (response) -> {
            if (response.getStatusLine().getStatusCode() == 200) {
                return extractOptionalSingleArrayElement(parseJsonResponse(response), "results")
                        .map(ConfluenceRestV2Client::extractPropertyValueFromJsonNode)
                        .orElse(null);
            } else {
                return null;
            }
        });
    }

    @Override
    public void deletePropertyByKey(String contentId, String key) {
        HttpDelete deletePropertyByKeyRequest = this.httpRequestV2Factory.deletePropertyByKeyRequest(contentId, key);
        sendRequest(deletePropertyByKeyRequest, (ignored) -> null);
    }

    @Override
    public List<String> getLabels(String contentId) {
        HttpGet getLabelsRequest = this.httpRequestV2Factory.getLabelsRequest(contentId);
        return sendRequest(getLabelsRequest, response -> {
            List<String> labels = new ArrayList<>();

            JsonNode jsonNode = parseJsonResponse(response);
            jsonNode.withArray("results").elements().forEachRemaining(n -> labels.add(n.get("name").asText()));

            return labels;
        });
    }

    @Override
    public void addLabels(String contentId, List<String> labels) {
        HttpPost addLabelRequest = this.httpRequestV2Factory.addLabelsRequest(contentId, labels);
        sendRequestAndFailIfNot20x(addLabelRequest);
    }

    @Override
    public void deleteLabel(String contentId, String label) {
        HttpDelete deleteLabelRequest = this.httpRequestV2Factory.deleteLabelRequest(contentId, label);
        sendRequestAndFailIfNot20x(deleteLabelRequest);
    }

    private String extractChildPagesAndNextLink(HttpResponse response, List<ConfluencePage> childPages) {
        JsonNode jsonNode = parseJsonResponse(response);
        if (jsonNode.has("results")) {
            jsonNode.withArray("results").forEach((pageNode) -> childPages.add(extractConfluencePageWithoutContent(pageNode)));
        }

        return jsonNode.get("_links").has("next") ? jsonNode.get("_links").get("next").asText() : null;
    }

    private String extractAttachmentsAndNextLink(HttpResponse response, List<ConfluenceAttachment> attachments) {
        JsonNode jsonNode = parseJsonResponse(response);
        if (jsonNode.has("results")) {
            jsonNode.withArray("results").forEach((attachmentNode) -> attachments.add(extractConfluenceAttachment(attachmentNode)));
        }

        return jsonNode.get("_links").has("next") ? jsonNode.get("_links").get("next").asText() : null;
    }

    private static ConfluencePage extractConfluencePageWithContent(JsonNode jsonNode) {
        String id = extractIdFromJsonNode(jsonNode);
        String title = extractTitleFromJsonNode(jsonNode);
        String content = extractContentFromJsonNode(jsonNode);
        int version = extractVersionFromJsonNode(jsonNode);

        return new ConfluencePage(id, title, content, version);
    }

    private static String extractContentFromJsonNode(JsonNode jsonNode) {
        // V2 API stores content differently
        if (jsonNode.has("body") && jsonNode.path("body").has("storage")) {
            return jsonNode.path("body").path("storage").get("value").asText();
        } else if (jsonNode.has("body") && jsonNode.path("body").has("representation") && jsonNode.path("body").has("value")) {
            // V2 format
            return jsonNode.path("body").get("value").asText();
        }
        return "";
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
        String relativeDownloadLink = extractDownloadLinkFromJsonNode(jsonNode);

        return new ConfluenceAttachment(id, title, relativeDownloadLink, version);
    }

    private static String extractDownloadLinkFromJsonNode(JsonNode jsonNode) {
        // V2 API may have different link structure
        if (jsonNode.path("_links").has("download")) {
            return jsonNode.path("_links").get("download").asText();
        } else if (jsonNode.has("downloadLink")) {
            return jsonNode.get("downloadLink").asText();
        }
        return "";
    }

    private static String extractIdFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("id").asText();
    }

    private static String extractTitleFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("title").asText();
    }

    private static int extractVersionFromJsonNode(JsonNode jsonNode) {
        if (jsonNode.has("version") && jsonNode.path("version").has("number")) {
            return jsonNode.path("version").get("number").asInt();
        } else if (jsonNode.has("version")) {
            // V2 might use a different format
            return jsonNode.get("version").asInt();
        }
        return 1; // Default version if not found
    }

    private static String extractPropertyValueFromJsonNode(JsonNode propertyNode) {
        return propertyNode.path("value").asText();
    }

    private static JsonNode extractMandatorySingleArrayElement(JsonNode jsonNode, String arrayPropertyName) {
        if (!jsonNode.has(arrayPropertyName) || jsonNode.withArray(arrayPropertyName).size() == 0) {
            throw new NotFoundException();
        }

        if (jsonNode.withArray(arrayPropertyName).size() > 1) {
            throw new MultipleResultsException();
        }

        return jsonNode.withArray(arrayPropertyName).elements().next();
    }

    private static Optional<JsonNode> extractOptionalSingleArrayElement(JsonNode jsonNode, String arrayPropertyName) {
        if (!jsonNode.has(arrayPropertyName) || jsonNode.withArray(arrayPropertyName).size() == 0) {
            return Optional.empty();
        }

        if (jsonNode.withArray(arrayPropertyName).size() > 1) {
            throw new MultipleResultsException();
        }

        return Optional.of(jsonNode.withArray(arrayPropertyName).elements().next());
    }

    private static void closeInputStream(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private static CloseableHttpClient defaultHttpClient(ProxyConfiguration proxyConfiguration, boolean disableSslVerification, boolean enableHttpClientSystemProperties, Integer connectionTTL) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(20 * 1000)
                .setConnectTimeout(20 * 1000)
                .setCookieSpec(STANDARD)
                .build();

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig);

        if (enableHttpClientSystemProperties) {
            builder.useSystemProperties();
        }

        if (proxyConfiguration != null) {
            if (proxyConfiguration.proxyHost() != null) {
                String proxyScheme = proxyConfiguration.proxyScheme() != null ? proxyConfiguration.proxyScheme() : "http";
                String proxyHost = proxyConfiguration.proxyHost();
                int proxyPort = proxyConfiguration.proxyPort() != null ? proxyConfiguration.proxyPort() : 80;

                builder.setProxy(new HttpHost(proxyHost, proxyPort, proxyScheme));

                if (proxyConfiguration.proxyUsername() != null) {
                    String proxyUsername = proxyConfiguration.proxyUsername();
                    String proxyPassword = proxyConfiguration.proxyPassword();

                    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), new UsernamePasswordCredentials(proxyUsername, proxyPassword));
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                }
            }
        }

        if (connectionTTL != null) {
            builder.setConnectionTimeToLive(connectionTTL, MILLISECONDS);
        }

        if (disableSslVerification) {
            builder.setSSLContext(trustAllSslContext());
            builder.setSSLHostnameVerifier(new NoopHostnameVerifier());
        }

        return builder.build();
    }

    private static SSLContext trustAllSslContext() {
        try {
            return new SSLContextBuilder()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Could not create trust-all SSL context", e);
        }
    }

    private static String authorizationHeaderValue(String username, String password) {
        if (username == null || username.isEmpty()) {
            return "Bearer " + password;
        } else {
            return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8));
        }
    }
}
