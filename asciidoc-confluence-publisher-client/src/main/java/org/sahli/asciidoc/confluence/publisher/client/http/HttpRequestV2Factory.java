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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Christian Stettler
 */
class HttpRequestV2Factory implements HttpRequestFactory {

    private final static Header APPLICATION_JSON_UTF8_HEADER = new BasicHeader("Content-Type", "application/json;charset=utf-8");
    private static final String API_V2_CONTEXT = "/api/v2";
    private static final String API_V1_CONTEXT = "/rest/api";
    private final String rootConfluenceUrl;
    private final String confluenceServerUrl;
    private final String confluenceApiV2Endpoint;
    private final String confluenceApiV1Endpoint;
    private final ObjectMapper objectMapper = new ObjectMapper();

    HttpRequestV2Factory(String rootConfluenceUrl) {
        assertMandatoryParameter(isNotBlank(rootConfluenceUrl), "rootConfluenceUrl");

        this.rootConfluenceUrl = rootConfluenceUrl;
        this.confluenceServerUrl = confluenceServerUrl(this.rootConfluenceUrl);
        this.confluenceApiV2Endpoint = rootConfluenceUrl + API_V2_CONTEXT;
        this.confluenceApiV1Endpoint = rootConfluenceUrl + API_V1_CONTEXT;

        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public HttpGet lookupSpaceIdRequest(String spaceKey) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");

        HttpGet lookupSpaceIdRequest = new HttpGet(this.confluenceApiV2Endpoint + "/spaces?keys=" + urlEncode(spaceKey));
        lookupSpaceIdRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);
        return lookupSpaceIdRequest;
    }

    @Override
    public HttpPost addPageUnderAncestorRequest(String spaceKey, String ancestorId, String title, String content, String versionMessage) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(title), "title");

        // Create V2 page payload
        ObjectNode pagePayload = objectMapper.createObjectNode();
        pagePayload.put("spaceId", spaceKey); // V2 uses spaceId instead of spaceKey
        pagePayload.put("status", "current");
        pagePayload.put("title", title);
        
        // Handle ancestor if provided
        if (isNotBlank(ancestorId)) {
            pagePayload.put("parentId", ancestorId);
        }
        
        // Add content
        ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("representation", "storage");
        bodyNode.put("value", content);
        pagePayload.set("body", bodyNode);
        
        // Add version message if provided
        if (versionMessage != null) {
            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("message", versionMessage);
            pagePayload.set("version", versionNode);
        }

        HttpPost pagePostRequest = new HttpPost(this.confluenceApiV2Endpoint + "/pages");
        pagePostRequest.setEntity(httpEntityWithJsonPayload(pagePayload));
        pagePostRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return pagePostRequest;
    }

    @Override
    public HttpPut updatePageRequest(String contentId, String ancestorId, String title, String content, int newVersion, String versionMessage, boolean notifyWatchers) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(title), "title");

        // Create V2 page update payload
        ObjectNode pagePayload = objectMapper.createObjectNode();
        pagePayload.put("id", contentId);
        pagePayload.put("status", "current");
        pagePayload.put("title", title);
        
        // Handle ancestor if provided
        if (isNotBlank(ancestorId)) {
            pagePayload.put("parentId", ancestorId);
        }
        
        // Add content
        ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("representation", "storage");
        bodyNode.put("value", content);
        pagePayload.set("body", bodyNode);
        
        // Add version info
        ObjectNode versionNode = objectMapper.createObjectNode();
        versionNode.put("number", newVersion);
        if (versionMessage != null) {
            versionNode.put("message", versionMessage);
        }
        if (!notifyWatchers) {
            // In V2, the equivalent is handled differently
            pagePayload.put("minorEdit", true);
        }
        pagePayload.set("version", versionNode);

        HttpPut updatePageRequest = new HttpPut(this.confluenceApiV2Endpoint + "/pages/" + contentId);
        updatePageRequest.setEntity(httpEntityWithJsonPayload(pagePayload));
        updatePageRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return updatePageRequest;
    }

    @Override
    public HttpDelete deletePageRequest(String contentId) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        return new HttpDelete(this.confluenceApiV2Endpoint + "/pages/" + contentId);
    }

    @Override
    public HttpPost addAttachmentRequest(String contentId, String attachmentFileName, InputStream attachmentContent) {
        // V2 API doesn't support adding attachments, fallback to V1
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceApiV1Endpoint + "/content/" + contentId + "/child/attachment");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(attachmentFileName, attachmentContent, false);
        attachmentPostRequest.setEntity(multipartEntity);

        return attachmentPostRequest;
    }

    @Override
    public HttpPost updateAttachmentContentRequest(String contentId, String attachmentId, InputStream attachmentContent, boolean notifyWatchers) {
        // V2 API doesn't support updating attachments, fallback to V1
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceApiV1Endpoint + "/content/" + contentId + "/child/attachment/" + attachmentId + "/data");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(null, attachmentContent, notifyWatchers);
        attachmentPostRequest.setEntity(multipartEntity);

        return attachmentPostRequest;
    }

    @Override
    public HttpDelete deleteAttachmentRequest(String attachmentId) {
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");

        return new HttpDelete(this.confluenceApiV2Endpoint + "/attachments/" + attachmentId);
    }

    @Override
    public HttpGet getPageByTitleRequest(String spaceId, String title) {
        assertMandatoryParameter(isNotBlank(spaceId), "spaceId");
        assertMandatoryParameter(isNotBlank(title), "title");

        return new HttpGet(this.confluenceApiV2Endpoint + "/spaces/" + spaceId + "/pages?title=" + urlEncode(title));
    }

    @Override
    public HttpGet getAttachmentByFileNameRequest(String contentId, String attachmentFileName, String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");

        URIBuilder uriBuilder = createUriBuilder(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/attachments");
        uriBuilder.addParameter("filename", attachmentFileName);
        uriBuilder.addParameter("limit", "1");

        try {
            return new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }
    }

    @Override
    public HttpGet getPageByIdRequest(String contentId, String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + contentId + "?include-version=true");
    }

    @Override
    public HttpGet getChildPagesByIdRequest(String parentContentId, Integer limit, Integer start, String expandOptions) {
        assertMandatoryParameter(isNotBlank(parentContentId), "parentContentId");

        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + parentContentId + "/direct-children?limit=" + limit);
    }

    public HttpGet getNextChildPagesByIdRequest(String nextLink) {
        assertMandatoryParameter(isNotBlank(nextLink), "nextLink");

        return new HttpGet(confluenceServerUrl(this.rootConfluenceUrl) + nextLink);
    }

    @Override
    public HttpGet getAttachmentsRequest(String contentId, Integer limit, Integer start, String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        
        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/attachments?limit=" + limit);
    }

    public HttpGet getNextAttachmentsRequest(String nextLink) {
        assertMandatoryParameter(isNotBlank(nextLink), "nextLink");

        return new HttpGet(confluenceServerUrl + nextLink);
    }

    @Override
    public HttpGet getAttachmentContentRequest(String relativeDownloadLink) {
        assertMandatoryParameter(isNotBlank(relativeDownloadLink), "relativeDownloadLink");

        // For V2, attachment content is available at a standard endpoint
        // However, we'll continue to use the provided relative download link for backward compatibility
        return new HttpGet(this.rootConfluenceUrl + relativeDownloadLink);
    }

    @Override
    public HttpGet getPropertyByKeyRequest(String contentId, String key) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");

        // V2 API uses different structure for content properties
        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/properties?key=" + urlEncode(key));
    }

    @Override
    public HttpDelete deletePropertyByKeyRequest(String contentId, String key) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");

        // V2 API uses different structure for content properties
        return new HttpDelete(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/properties/" + urlEncode(key));
    }

    public HttpGet lookupPropertyIdByKey(String contentId, String key) {
        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/properties?key=" + urlEncode(key));
    }

    @Override
    public HttpPost setPropertyByKeyRequest(String contentId, String key, String value) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");
        assertMandatoryParameter(isNotBlank(value), "value");

        // V2 API uses different payload structure for properties
        ObjectNode propertyPayload = objectMapper.createObjectNode();
        propertyPayload.put("key", key);
        propertyPayload.put("value", value);

        HttpPost postRequest = new HttpPost(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/properties");
        postRequest.setEntity(httpEntityWithJsonPayload(propertyPayload));
        postRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return postRequest;
    }

    @Override
    public HttpGet getLabelsRequest(String contentId) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        // V2 API uses a different endpoint for labels
        return new HttpGet(this.confluenceApiV2Endpoint + "/pages/" + contentId + "/labels");
    }

    @Override
    public HttpPost addLabelsRequest(String contentId, List<String> labels) {
        // V2 API doesn't support adding labels, fallback to V1
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(!labels.isEmpty(), "labels");

        // Create V1 label payload
        ObjectNode[] labelNodes = labels.stream()
                .map(label -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("name", label);
                    return node;
                })
                .toArray(ObjectNode[]::new);

        HttpPost addLabelsRequest = new HttpPost(this.confluenceApiV1Endpoint + "/content/" + contentId + "/label");
        addLabelsRequest.setEntity(httpEntityWithJsonPayload(labelNodes));
        addLabelsRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return addLabelsRequest;
    }

    @Override
    public HttpDelete deleteLabelRequest(String contentId, String label) {
        // V2 API doesn't support deleting labels, fallback to V1
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(label), "label");

        return new HttpDelete(this.confluenceApiV1Endpoint + "/content/" + contentId + "/label?name=" + urlEncode(label));
    }

    private static String confluenceServerUrl(String rootConfluenceUrl) {
        URIBuilder uriBuilder = createUriBuilder(rootConfluenceUrl);

        if (uriBuilder.getPort() != -1) {
            return uriBuilder.getScheme() + "://" + uriBuilder.getHost() + ":" + uriBuilder.getPort();
        } else {
            return uriBuilder.getScheme() + "://" + uriBuilder.getHost();
        }
    }

    private static URIBuilder createUriBuilder(String path) {
        try {
            return new URIBuilder(path);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to parse path as URI: " + path, e);
        }
    }

    private BasicHttpEntity httpEntityWithJsonPayload(Object payload) {
        String jsonPayload = toJsonString(payload);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes(UTF_8)));

        return entity;
    }

    private String toJsonString(Object objectToConvert) {
        try {
            return this.objectMapper.writeValueAsString(objectToConvert);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while converting object to JSON", e);
        }
    }

    private static HttpEntity multipartEntity(String attachmentFileName, InputStream attachmentContent, boolean notifyWatchers) {
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        multipartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        multipartEntityBuilder.setCharset(UTF_8);

        InputStreamBody inputStreamBody;
        if (isNotBlank(attachmentFileName)) {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM, attachmentFileName);
        } else {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM);
        }

        multipartEntityBuilder.addPart("file", inputStreamBody);

        if (!notifyWatchers) {
            multipartEntityBuilder.addPart("minorEdit", new StringBody("true", ContentType.DEFAULT_TEXT));
        }

        return multipartEntityBuilder.build();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not url-encode value '" + value + "'", e);
        }
    }
}