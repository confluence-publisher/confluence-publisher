/*
 * Copyright 2016-2017 the original author or authors.
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.PROXY_AUTHORIZATION;
import static org.apache.http.client.config.CookieSpecs.STANDARD;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluenceRestClient implements ConfluenceClient {

    private final CloseableHttpClient httpClient;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestFactory httpRequestFactory;

    public ConfluenceRestClient(String rootConfluenceUrl, boolean disableSslVerification, String username, String password) {
        this(rootConfluenceUrl, null, disableSslVerification, username, password);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, ProxyConfiguration proxyConfiguration, boolean disableSslVerification, String username, String password) {
        this(rootConfluenceUrl, defaultHttpClient(proxyConfiguration, disableSslVerification), username, password);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, CloseableHttpClient httpClient, String username, String password) {
        assertMandatoryParameter(httpClient != null, "httpClient");

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
    public String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content, String versionMessage) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content, versionMessage);

        return sendRequestAndFailIfNot20x(addPageUnderSpaceRequest, (response) -> {
            String contentId = extractIdFromJsonNode(parseJsonResponse(response));

            return contentId;
        });
    }

    @Override
    public void updatePage(String contentId, String ancestorId, String title, String content, int newVersion, String versionMessage) {
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, ancestorId, title, content, newVersion, versionMessage);
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
        httpRequest.addHeader(AUTHORIZATION, basicAuthorizationHeaderValue(this.username, this.password));

        try (CloseableHttpResponse response = this.httpClient.execute(httpRequest)) {
            return responseHandler.apply(response);
        } catch (IOException e) {
            throw new RequestFailedException(httpRequest, null, e);
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
    public void setPropertyByKey(String contentId, String key, String value) {
        HttpPost setPropertyByKeyRequest = this.httpRequestFactory.setPropertyByKeyRequest(contentId, key, value);
        sendRequestAndFailIfNot20x(setPropertyByKeyRequest);
    }

    @Override
    public String getPropertyByKey(String contentId, String key) {
        HttpGet propertyByKeyRequest = this.httpRequestFactory.getPropertyByKeyRequest(contentId, key);

        return sendRequest(propertyByKeyRequest, (response) -> {
            if (response.getStatusLine().getStatusCode() == 200) {
                return extractPropertyValueFromJsonNode(parseJsonResponse(response));
            } else {
                return null;
            }
        });
    }

    @Override
    public void deletePropertyByKey(String contentId, String key) {
        HttpDelete deletePropertyByKeyRequest = this.httpRequestFactory.deletePropertyByKeyRequest(contentId, key);
        sendRequest(deletePropertyByKeyRequest, (ignored) -> null);
    }

    @Override
    public List<String> getLabels(String contentId) {
        HttpGet getLabelsRequest = this.httpRequestFactory.getLabelsRequest(contentId);
        return sendRequest(getLabelsRequest, response -> {
            List<String> labels = new ArrayList<>();

            JsonNode jsonNode = parseJsonResponse(response);
            jsonNode.withArray("results").elements().forEachRemaining(n -> labels.add(n.get("name").asText()));

            return labels;
        });
    }

    @Override
    public void addLabels(String contentId, List<String> labels) {
        HttpPost addLabelRequest = this.httpRequestFactory.addLabelsRequest(contentId, labels);
        sendRequestAndFailIfNot20x(addLabelRequest);
    }

    @Override
    public void deleteLabel(String contentId, String label) {
        HttpDelete deleteLabelRequest = this.httpRequestFactory.deleteLabelRequest(contentId, label);
        sendRequestAndFailIfNot20x(deleteLabelRequest);
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
        } catch (IOException ignored) {
        }
    }

    private static CloseableHttpClient defaultHttpClient(ProxyConfiguration proxyConfiguration, boolean disableSslVerification) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(20 * 1000)
                .setConnectTimeout(20 * 1000)
                .setCookieSpec(STANDARD)
                .build();

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig);

        if (proxyConfiguration != null) {
            if (proxyConfiguration.proxyHost() != null) {
                String proxyScheme = proxyConfiguration.proxyScheme() != null ? proxyConfiguration.proxyScheme() : "http";
                String proxyHost = proxyConfiguration.proxyHost();
                int proxyPort = proxyConfiguration.proxyPort() != null ? proxyConfiguration.proxyPort() : 80;

                builder.setProxy(new HttpHost(proxyHost, proxyPort, proxyScheme));

                if (proxyConfiguration.proxyUsername() != null) {
                    String proxyUsername = proxyConfiguration.proxyUsername();
                    String proxyPassword = proxyConfiguration.proxyPassword();

                    builder.setDefaultHeaders(singletonList(new BasicHeader(PROXY_AUTHORIZATION, basicAuthorizationHeaderValue(proxyUsername, proxyPassword))));
                }
            }
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

    private static String basicAuthorizationHeaderValue(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8));
    }


    public static class ProxyConfiguration {

        private final String proxyScheme;
        private final String proxyHost;
        private final Integer proxyPort;
        private final String proxyUsername;
        private final String proxyPassword;

        public ProxyConfiguration(String proxyScheme, String proxyHost, Integer proxyPort, String proxyUsername, String proxyPassword) {
            this.proxyScheme = proxyScheme;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUsername = proxyUsername;
            this.proxyPassword = proxyPassword;
        }

        public String proxyScheme() {
            return this.proxyScheme;
        }

        public String proxyHost() {
            return this.proxyHost;
        }

        public Integer proxyPort() {
            return this.proxyPort;
        }

        public String proxyUsername() {
            return this.proxyUsername;
        }

        public String proxyPassword() {
            return this.proxyPassword;
        }

    }

}
