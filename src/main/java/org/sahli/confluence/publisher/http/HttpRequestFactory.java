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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.message.BasicHeader;
import org.sahli.confluence.publisher.http.payloads.Ancestor;
import org.sahli.confluence.publisher.http.payloads.Body;
import org.sahli.confluence.publisher.http.payloads.PagePayload;
import org.sahli.confluence.publisher.http.payloads.Space;
import org.sahli.confluence.publisher.http.payloads.Storage;
import org.sahli.confluence.publisher.http.payloads.Version;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM;
import static org.sahli.confluence.publisher.http.HttpRequestFactory.PagePayloadBuilder.pagePayloadBuilder;
import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 * @since 1.0
 */
class HttpRequestFactory {

    private final static Header APPLICATION_JSON_UTF8_HEADER = new BasicHeader("Content-Type", "application/json;charset=utf-8");
    private static final String REST_API_CONTEXT = "/rest/api";
    private final String confluenceRestApiEndpoint;
    private final String username;
    private final String password;

    HttpRequestFactory(String confluenceRestApiEndpoint) {
        this(confluenceRestApiEndpoint, null, null);
    }

    HttpRequestFactory(String rootConfluenceUrl, String username, String password) {
        assertMandatoryParameter(isNotBlank(rootConfluenceUrl), "rootConfluenceUrl");

        this.confluenceRestApiEndpoint = rootConfluenceUrl + REST_API_CONTEXT;
        this.username = username;
        this.password = password;
    }

    HttpPost addPageUnderSpaceRequest(String spaceKey, String title, String content) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(title), "title");

        PagePayload pagePayload = pagePayloadBuilder()
                .spaceKey(spaceKey)
                .title(title)
                .content(content)
                .build();

        HttpPost addPageHttpPost = addPageHttpPost(this.confluenceRestApiEndpoint, pagePayload);
        this.authenticationHeaderIfAvailable().ifPresent(addPageHttpPost::addHeader);

        return addPageHttpPost;
    }

    HttpPost addPageUnderAncestorRequest(String spaceKey, String ancestorId, String title, String content) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(ancestorId), "ancestorId");
        assertMandatoryParameter(isNotBlank(title), "title");

        PagePayload pagePayload = pagePayloadBuilder()
                .spaceKey(spaceKey)
                .ancestorId(ancestorId)
                .title(title)
                .content(content)
                .build();

        HttpPost addPageHttpPost = addPageHttpPost(this.confluenceRestApiEndpoint, pagePayload);
        this.authenticationHeaderIfAvailable().ifPresent(addPageHttpPost::addHeader);

        return addPageHttpPost;
    }

    HttpPut updatePageRequest(String contentId, String title, String content, int newVersion) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(title), "title");

        PagePayload pagePayload = pagePayloadBuilder()
                .title(title)
                .content(content)
                .version(newVersion)
                .build();

        String jsonPayload = toJsonString(pagePayload);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes()));

        HttpPut updatePageRequest = new HttpPut(this.confluenceRestApiEndpoint + "/content/" + contentId);
        updatePageRequest.setEntity(entity);
        updatePageRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        this.authenticationHeaderIfAvailable().ifPresent(updatePageRequest::addHeader);

        return updatePageRequest;
    }

    HttpDelete deletePageRequest(String contentId) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        HttpDelete deletePageRequest = new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + contentId);
        this.authenticationHeaderIfAvailable().ifPresent(deletePageRequest::addHeader);

        return deletePageRequest;
    }

    HttpPost addAttachmentRequest(String contentId, String attachmentFileName, InputStream attachmentContent) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(attachmentFileName, attachmentContent);
        attachmentPostRequest.setEntity(multipartEntity);

        this.authenticationHeaderIfAvailable().ifPresent(attachmentPostRequest::addHeader);

        return attachmentPostRequest;
    }

    HttpPost updateAttachmentContentRequest(String contentId, String attachmentId, InputStream attachmentContent) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment/" + attachmentId + "/data");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(null, attachmentContent);
        attachmentPostRequest.setEntity(multipartEntity);

        this.authenticationHeaderIfAvailable().ifPresent(attachmentPostRequest::addHeader);

        return attachmentPostRequest;
    }

    HttpDelete deleteAttachmentRequest(String attachmentId) {
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");

        HttpDelete deleteAttachmentRequest = new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + attachmentId);

        this.authenticationHeaderIfAvailable().ifPresent(deleteAttachmentRequest::addHeader);

        return deleteAttachmentRequest;
    }

    HttpGet getPageByTitleRequest(String spaceKey, String title) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(title), "title");

        String encodedTitle;
        try {
            encodedTitle = URLEncoder.encode(title, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode title", e);
        }

        String searchQuery = this.confluenceRestApiEndpoint + "/content?spaceKey=" + spaceKey + "&title=" + encodedTitle;

        HttpGet getPageByTitleRequest = new HttpGet(searchQuery);

        this.authenticationHeaderIfAvailable().ifPresent(getPageByTitleRequest::addHeader);

        return getPageByTitleRequest;
    }

    HttpGet getAttachmentByFileNameRequest(String contentId, String attachmentFileName) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");

        HttpGet getAttachmentByFileNameRequest = new HttpGet(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment?filename=" + attachmentFileName);

        this.authenticationHeaderIfAvailable().ifPresent(getAttachmentByFileNameRequest::addHeader);

        return getAttachmentByFileNameRequest;
    }

    HttpGet getPageByIdRequest(String contentId, final String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        return new HttpGet(this.confluenceRestApiEndpoint + "/content/" + contentId + "?expand=" + expandOptions);
    }

    HttpGet getChildPagesByIdRequest(String parentContentId, Integer limit, Integer pageNumber) {
        assertMandatoryParameter(isNotBlank(parentContentId), "parentContentId");
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setPath(this.confluenceRestApiEndpoint + "/content/" + parentContentId + "/child/page");

        if (limit != null) {
            uriBuilder.addParameter("limit", limit.toString());
        }
        if (pageNumber != null) {
            uriBuilder.addParameter("page", pageNumber.toString());
        }

        try {
            return new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }
    }

    public HttpGet getAttachmentsRequest(String contentId, Integer limit, Integer pageNumber) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setPath(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment");

        if (limit != null) {
            uriBuilder.addParameter("limit", limit.toString());
        }
        if (pageNumber != null) {
            uriBuilder.addParameter("page", pageNumber.toString());
        }

        try {
            return new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }
    }

    private Optional<Header> authenticationHeaderIfAvailable() {
        if (isNotBlank(this.username) && isNotBlank(this.password)) {
            String base64EncodedUsernameAndPassword = encodeAuthenticationHeader(this.username, this.password);
            return Optional.of(new BasicHeader("Authorization", "Basic " + base64EncodedUsernameAndPassword));
        } else {
            return Optional.empty();
        }
    }

    private static HttpPost addPageHttpPost(String confluenceRestApiEndpoint, PagePayload pagePayload) {
        String jsonPayload = toJsonString(pagePayload);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes()));

        HttpPost postRequest = new HttpPost(confluenceRestApiEndpoint + "/content");
        postRequest.setEntity(entity);
        postRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return postRequest;
    }

    private static String toJsonString(Object objectToConvert) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            return objectMapper.writeValueAsString(objectToConvert);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while converting object to JSON", e);
        }
    }

    private static HttpEntity multipartEntity(String attachmentFileName, InputStream attachmentContent) {
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        multipartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        multipartEntityBuilder.setCharset(Charset.forName("UTF-8"));

        InputStreamBody inputStreamBody;
        if (isNotBlank(attachmentFileName)) {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM, attachmentFileName);
        } else {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM);
        }

        multipartEntityBuilder.addPart("file", inputStreamBody);

        return multipartEntityBuilder.build();
    }

    private static String encodeAuthenticationHeader(String username, String password) {
        String usernameAndPassword = username + ":" + password;
        String base64EncodedUsernameAndPassword;
        try {
            base64EncodedUsernameAndPassword = new String(Base64.getEncoder().encode(usernameAndPassword.getBytes()), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode authentication header in base64", e);
        }
        return base64EncodedUsernameAndPassword;
    }


    static class PagePayloadBuilder {

        private String title;
        private String content;
        private String spaceKey;
        private String ancestorId;
        private Integer version;

        public PagePayloadBuilder title(String title) {
            this.title = title;

            return this;
        }

        public PagePayloadBuilder content(String content) {
            this.content = content;

            return this;
        }

        public PagePayloadBuilder spaceKey(String spaceKey) {
            this.spaceKey = spaceKey;

            return this;
        }

        public PagePayloadBuilder ancestorId(String ancestorId) {
            this.ancestorId = ancestorId;

            return this;
        }

        public PagePayloadBuilder version(Integer version) {
            this.version = version;

            return this;
        }

        private PagePayload build() {
            Storage storage = new Storage();
            storage.setValue(this.content);

            Body body = new Body();
            body.setStorage(storage);

            PagePayload pagePayload = new PagePayload();
            pagePayload.setBody(body);
            pagePayload.setTitle(this.title);

            if (isNotBlank(this.spaceKey)) {
                Space space = new Space();
                space.setKey(this.spaceKey);
                pagePayload.setSpace(space);
            }

            if (isNotBlank(this.ancestorId)) {
                Ancestor ancestor = new Ancestor();
                ancestor.setId(this.ancestorId);
                pagePayload.addAncestor(ancestor);
            }

            if (this.version != null) {
                Version versionContainer = new Version();
                versionContainer.setNumber(this.version);
                pagePayload.setVersion(versionContainer);
            }

            return pagePayload;
        }

        static PagePayloadBuilder pagePayloadBuilder() {
            return new PagePayloadBuilder();
        }

    }

}
