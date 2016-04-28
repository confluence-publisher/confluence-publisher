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

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.fileContent;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.inputStreamAsString;
import static org.sahli.confluence.publisher.utils.SameJsonAsMatcher.isSameJsonAs;

public class HttpRequestFactoryTest {

    private static final String CLASS_LOCATION = Paths.get("src", "test", "resources", "org", "sahli", "confluence", "publisher", "http").toString();
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";
    private static final String ROOT_CONFLUENCE_URL = "http://confluence.com";
    private static final String CONFLUENCE_REST_API_ENDPOINT = ROOT_CONFLUENCE_URL + "/rest/api";

    @Rule
    public ExpectedException expectedException = none();
    private HttpRequestFactory httpRequestFactory;

    @Before
    public void setUp() throws Exception {
        this.httpRequestFactory = new HttpRequestFactory(ROOT_CONFLUENCE_URL);
    }

    @Test
    public void instantiation_withEmptyConfluenceRestApiEndpoint_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("rootConfluenceUrl must be set");

        // arrange + act
        new HttpRequestFactory("");
    }

    @Test
    public void checkAuthenticationHeaderAlwaysSetWhenProvided() throws Exception {
        // arrange
        HttpRequestFactory httpRequestFactory = new HttpRequestFactory(CONFLUENCE_REST_API_ENDPOINT, "username", "password");

        // act + assert
        HttpPost addPageUnderSpaceRequest = httpRequestFactory.addPageUnderSpaceRequest("~personaleSpace", "title", "content");
        assertAuthenticationHeader(addPageUnderSpaceRequest);

        HttpPost addPageUnderAncestorRequest = httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "123", "title", "content");
        assertAuthenticationHeader(addPageUnderAncestorRequest);

        HttpPut updatePageRequest = httpRequestFactory.updatePageRequest("23", "title", "content", 2);
        assertAuthenticationHeader(updatePageRequest);

        HttpDelete deletePageRequest = httpRequestFactory.deletePageRequest("34");
        assertAuthenticationHeader(deletePageRequest);

        HttpPost addAttachment = httpRequestFactory.addAttachmentRequest("234", "file.txt", new ByteArrayInputStream("hello".getBytes()));
        assertAuthenticationHeader(addAttachment);

        HttpPost updateAttachmentContent = httpRequestFactory.updateAttachmentContentRequest("234", "34", new ByteArrayInputStream("hello".getBytes()));
        assertAuthenticationHeader(updateAttachmentContent);

        HttpDelete deleteAttachment = httpRequestFactory.deleteAttachmentRequest("234");
        assertAuthenticationHeader(deleteAttachment);

        HttpGet getPageByTitleRequest = httpRequestFactory.getPageByTitleRequest("~personalSpace", "title");
        assertAuthenticationHeader(getPageByTitleRequest);

        HttpGet getAttachmentByFileNameRequest = httpRequestFactory.getAttachmentByFileNameRequest("234", "file.txt");
        assertAuthenticationHeader(getAttachmentByFileNameRequest);
    }

    private static void assertAuthenticationHeader(HttpRequestBase httpRequest) {
        assertThat(httpRequest.getFirstHeader("Authorization").getValue(), is("Basic dXNlcm5hbWU6cGFzc3dvcmQ="));
    }

    @Test
    public void addPageUnderSpaceRequest_withValidParameters_returnsValidHttpPostWithSpaceKeyWithoutAncestorId() throws Exception {
        // arrange
        String spaceKey = "~personalSpace";
        String title = "title";
        String content = "content";

        // act
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderSpaceRequest(spaceKey, title, content);

        // assert
        assertThat(addPageUnderSpaceRequest.getMethod(), is("POST"));
        assertThat(addPageUnderSpaceRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content"));
        assertThat(addPageUnderSpaceRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(addPageUnderSpaceRequest.getEntity().getContent());
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "add-page-request-space-key.json").toString());
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void addPageUnderSpaceRequest_withBlankTitle_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderSpaceRequest("~personalSpace", "", "content");
    }

    @Test
    public void addPageUnderSpaceRequest_withoutSpaceKey_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("spaceKey must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderSpaceRequest("", "title", "content");
    }

    @Test
    public void addPageUnderAncestorRequest_withAncestorId_returnsValidHttpPostWithAncestorIdWithoutSpaceKey() throws Exception {
        // arrange
        String spaceKey = "~personalSpace";
        String ancestorId = "1234";
        String title = "title";
        String content = "content";

        // act
        HttpPost addPageUnderAncestorRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content);

        // assert
        assertThat(addPageUnderAncestorRequest.getMethod(), is("POST"));
        assertThat(addPageUnderAncestorRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content"));
        assertThat(addPageUnderAncestorRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(addPageUnderAncestorRequest.getEntity().getContent());
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "add-page-request-ancestor-id.json").toString());
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void addPageUnderAncestorRequest_withBlankTitle_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "1234", "", "content");
    }

    @Test
    public void addPageUnderAncestorRequest_withoutAncestorId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("ancestorId must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "", "title", "content");
    }

    @Test
    public void updatePageRequest_withValidParameters_returnsValidHttpPutRequest() throws Exception {
        // arrange
        String contentId = "1234";
        String title = "title";
        String content = "content";
        Integer version = 2;

        // act
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, title, content, version);

        // assert
        assertThat(updatePageRequest.getMethod(), is("PUT"));
        assertThat(updatePageRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId));
        assertThat(updatePageRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(updatePageRequest.getEntity().getContent());
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "update-page-request.json").toString());
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void updatePageRequest_withEmptyContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.updatePageRequest("", "title", "content", 2);
    }

    @Test
    public void updatePageRequest_withEmptyTitle_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // arrange + act
        this.httpRequestFactory.updatePageRequest("1234", "", "content", 2);
    }

    @Test
    public void deletePageRequest_withValidParameters_returnsValidHttpDeleteRequest() throws Exception {
        // arrange
        String contentId = "1234";

        // act
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);

        // assert
        assertThat(deletePageRequest.getMethod(), is("DELETE"));
        assertThat(deletePageRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId));
    }

    @Test
    public void deletePageRequest_withEmptyContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.deletePageRequest("");
    }

    @Test
    public void addAttachmentRequest_withValidParameters_returnsValidHttpPostWithMultipartEntity() throws Exception {
        // arrange
        String contentId = "1234";
        String attachmentFileName = "attachment.txt";
        InputStream attachmentContent = new FileInputStream(Paths.get(CLASS_LOCATION, "attachment.txt").toString());

        // act
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);

        // assert
        assertThat(addAttachmentRequest.getMethod(), is("POST"));
        assertThat(addAttachmentRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment"));
        assertThat(addAttachmentRequest.getFirstHeader("X-Atlassian-Token").getValue(), is("no-check"));
        // TODO find a way to assert entity content
    }

    @Test
    public void addAttachmentRequest_withEmptyContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.addAttachmentRequest("", "file.txt", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void addAttachmentRequest_withEmptyAttachmentFileName_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentFileName");

        // arrange + act
        this.httpRequestFactory.addAttachmentRequest("1234", "", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void addAttachmentRequest_withNullAttachmentContent_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentContent");

        // arrange + act
        this.httpRequestFactory.addAttachmentRequest("1234", "file.txt", null);
    }

    @Test
    public void updateAttachmentContentRequest_withValidParameters_returnsHttpPutRequestWithMultipartEntity() throws Exception {
        // arrange
        String contentId = "1234";
        String attachmentId = "45";
        InputStream attachmentContent = new ByteArrayInputStream("hello".getBytes());

        // act
        HttpPost updateAttachmentContentRequest = this.httpRequestFactory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent);

        // assert
        assertThat(updateAttachmentContentRequest.getMethod(), is("POST"));
        assertThat(updateAttachmentContentRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment/" + attachmentId + "/data"));
        assertThat(updateAttachmentContentRequest.getFirstHeader("X-Atlassian-Token").getValue(), is("no-check"));
        // TODO find a way to assert entity content
    }

    @Test
    public void updateAttachmentContentRequest_withEmptyContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("", "45", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void updateAttachmentContentRequest_withEmptyAttachmentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentId must be set");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("1234", "", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void updateAttachmentContentRequest_withNullAttachmentContent_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentContent");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("1234", "45", null);
    }

    @Test
    public void deleteAttachmentRequest_withValidParameters_returnsValidHttpDelete() throws Exception {
        // arrange
        String attachmentId = "att1234";

        // act
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);

        // assert
        assertThat(deleteAttachmentRequest.getMethod(), is("DELETE"));
        assertThat(deleteAttachmentRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + attachmentId));
    }

    @Test
    public void deleteAttachmentRequest_withEmptyAttachmentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentId must be set");

        // arrange + act
        this.httpRequestFactory.deleteAttachmentRequest("");
    }

    @Test
    public void getPageByTitleRequest_withValidParameters_returnsValidHttpGet() throws Exception {
        // arrange
        String spaceKey = "~personalSpace";
        String title = "Some page";

        // act
        HttpGet getPageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);

        // assert
        assertThat(getPageByTitleRequest.getMethod(), is("GET"));
        assertThat(getPageByTitleRequest.getURI().toString(),
                is(CONFLUENCE_REST_API_ENDPOINT + "/content?spaceKey=" + spaceKey + "&title=" + "Some+page"));
    }

    @Test
    public void getPageByTitleRequest_withEmptySpaceKey_throwsIllegalArgumentException() throws Exception {
        // arrange
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("spaceKey must be set");

        // act
        this.httpRequestFactory.getPageByTitleRequest("", "Some page");
    }

    @Test
    public void getPageByTitleRequest_withEmptyTitle_throwsIllegalArgumentException() throws Exception {
        // arrange
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // act
        this.httpRequestFactory.getPageByTitleRequest("~personalSpace", "");
    }

    @Test
    public void getAttachmentByFileNameRequest_withValidParameters_returnsValidHttpGet() throws Exception {
        // arrange
        String contentId = "1234";
        String attachmentFileName = "file.txt";

        // act
        HttpGet getAttachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);

        // assert
        assertThat(getAttachmentByFileNameRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?filename=" + attachmentFileName));
    }

    @Test
    public void getAttachmentByFileNameRequest_withEmptyContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // act
        this.httpRequestFactory.getAttachmentByFileNameRequest("", "file.txt");
    }

    @Test
    public void getAttachmentByFileNameRequest_withEmptyAttachmentFileName_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentFileName must be set");

        // act
        this.httpRequestFactory.getAttachmentByFileNameRequest("1234", "");
    }

    @Test
    public void getPageByIdRequest_withValidParameter_returnsValidHttpGet() throws Exception {
        // arrange
        String contentId = "1234";

        // act
        HttpGet getPageByIdRequest = this.httpRequestFactory.getPageByIdRequest(contentId, "body.storage,version");

        // assert
        assertThat(getPageByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "?expand=body.storage,version"));
    }

    @Test
    public void getChildPagesByIdRequest_withMinimalParameters_returnsValidHttpGet() throws Exception {
        // arrange
        String parentContentId = "1234";

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, null, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page"));
    }

    @Test
    public void getChildPagesByIdRequest_withBlankParentContentId_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("parentContentId must be set");

        // arrange + act
        this.httpRequestFactory.getChildPagesByIdRequest("", null, null);
    }

    @Test
    public void getChildPagesByIdRequest_withLimit_returnsHttpGetWithLimit() throws Exception {
        // arrange
        String parentContentId = "1234";
        int limit = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, limit, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page?limit=" + limit));
    }

    @Test
    public void getChildPagesByIdRequest_withPageNumber_returnsHttpGetWithLimit() throws Exception {
        // arrange
        String parentContentId = "1234";
        int start = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, null, start);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page?start=" + start));
    }

    @Test
    public void getChildPagesByIdRequest_withLimitAndPageNumber_returnsHttpGetWithPageNumberAndLimit() throws Exception {
        // arrange
        String parentContentId = "1234";
        int limit = 10;
        int start = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, limit, start);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), containsString("limit=" + limit));
        assertThat(getChildPagesByIdRequest.getURI().toString(), containsString("start=" + start));
    }

    @Test
    public void getAttachmentsRequest_withMinimalParameters_returnsValidHttpGetRequest() throws Exception {
        // arrange
        String contentId = "1234";

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, null, null, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment"));
    }

    @Test
    public void getAttachmentsRequest_withLimit_returnsHttpGetWithLimit() throws Exception {
        // arrange
        String contentId = "1234";
        int limit = 5;

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, limit, null, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?limit=" + limit));
    }

    @Test
    public void getAttachmentsRequest_withPageNumber_returnsHttpGetWithPage() throws Exception {
        // arrange
        String contentId = "1234";
        int start = 1;

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, null, start, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?start=" + start));
    }

    @Test
    public void getAttachmentsRequest_withLimitAndPageNumberAndExpandOptions_returnsHttpGetWithLimitAndPage() throws Exception {
        // arrange
        String contentId = "1234";
        int limit = 5;
        int start = 1;
        String expandOptions = "version";

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, limit, start, expandOptions);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), containsString("limit=" + limit));
        assertThat(getAttachmentsRequest.getURI().toString(), containsString("start=" + start));
        assertThat(getAttachmentsRequest.getURI().toString(), containsString("expand=" + expandOptions));
    }

}