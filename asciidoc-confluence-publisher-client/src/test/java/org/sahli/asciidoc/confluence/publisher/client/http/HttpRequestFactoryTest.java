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

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;
import static org.sahli.asciidoc.confluence.publisher.client.utils.SameJsonAsMatcher.isSameJsonAs;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class HttpRequestFactoryTest {

    private static final String CLASS_LOCATION = Paths.get("src", "test", "resources", "org", "sahli", "asciidoc", "confluence", "publisher", "client", "http").toString();
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";
    private static final String ROOT_CONFLUENCE_URL = "http://confluence.com";
    private static final String CONFLUENCE_REST_API_ENDPOINT = ROOT_CONFLUENCE_URL + "/rest/api";

    @Rule
    public final ExpectedException expectedException = none();

    private HttpRequestFactory httpRequestFactory;

    @Before
    public void setUp() {
        this.httpRequestFactory = new HttpRequestFactory(ROOT_CONFLUENCE_URL);
    }

    @Test
    public void instantiation_withEmptyConfluenceRestApiEndpoint_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("rootConfluenceUrl must be set");

        // arrange + act
        new HttpRequestFactory("");
    }

    @Test
    public void addPageUnderAncestorRequest_withAncestorId_returnsValidHttpPostWithAncestorIdWithoutSpaceKey() throws Exception {
        // arrange
        String spaceKey = "~personalSpace";
        String ancestorId = "1234";
        String title = "title";
        String content = "content";
        String versionMessage = "version message";

        // act
        HttpPost addPageUnderAncestorRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content, versionMessage);

        // assert
        assertThat(addPageUnderAncestorRequest.getMethod(), is("POST"));
        assertThat(addPageUnderAncestorRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content"));
        assertThat(addPageUnderAncestorRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(addPageUnderAncestorRequest.getEntity().getContent(), UTF_8);
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "add-page-request-ancestor-id.json").toString(), UTF_8);
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void addPageUnderAncestorRequest_withoutVersionMessage_returnsValidHttpPost() throws Exception {
        // arrange
        String spaceKey = "~personalSpace";
        String ancestorId = "1234";
        String title = "title";
        String content = "content";
        String versionMessage = null;

        // act
        HttpPost addPageUnderAncestorRequest = this.httpRequestFactory.addPageUnderAncestorRequest(spaceKey, ancestorId, title, content, versionMessage);

        // assert
        assertThat(addPageUnderAncestorRequest.getMethod(), is("POST"));
        assertThat(addPageUnderAncestorRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content"));
        assertThat(addPageUnderAncestorRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(addPageUnderAncestorRequest.getEntity().getContent(), UTF_8);
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "add-page-request-without-version-message.json").toString(), UTF_8);
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void addPageUnderAncestorRequest_withBlankTitle_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "1234", "", "content", "version message");
    }

    @Test
    public void addPageUnderAncestorRequest_withoutAncestorId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("ancestorId must be set");

        // arrange + act
        this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "", "title", "content", "version message");
    }

    @Test
    public void updatePageRequest_withValidParametersWithAncestorId_returnsValidHttpPutRequest() throws Exception {
        // arrange
        String contentId = "1234";
        String ancestorId = "1";
        String title = "title";
        String content = "content";
        Integer version = 2;
        String versionMessage = "version message";

        // act
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, ancestorId, title, content, version, versionMessage);

        // assert
        assertThat(updatePageRequest.getMethod(), is("PUT"));
        assertThat(updatePageRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId));
        assertThat(updatePageRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(updatePageRequest.getEntity().getContent(), UTF_8);
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "update-page-request-with-ancestor-id.json").toString(), UTF_8);
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void updatePageRequest_withValidParametersWithoutAncestorId_returnsValidHttpPutRequest() throws Exception {
        // arrange
        String contentId = "1234";
        String ancestorId = null;
        String title = "title";
        String content = "content";
        Integer version = 2;
        String versionMessage = null;

        // act
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, ancestorId, title, content, version, versionMessage);

        // assert
        assertThat(updatePageRequest.getMethod(), is("PUT"));
        assertThat(updatePageRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId));
        assertThat(updatePageRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(updatePageRequest.getEntity().getContent(), UTF_8);
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "update-page-request-without-ancestor-id.json").toString(), UTF_8);
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void updatePageRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.updatePageRequest("", "1", "title", "content", 2, "test message");
    }

    @Test
    public void updatePageRequest_withEmptyTitle_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // arrange + act
        this.httpRequestFactory.updatePageRequest("1234", "1", "", "content", 2, "test message");
    }

    @Test
    public void deletePageRequest_withValidParameters_returnsValidHttpDeleteRequest() {
        // arrange
        String contentId = "1234";

        // act
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);

        // assert
        assertThat(deletePageRequest.getMethod(), is("DELETE"));
        assertThat(deletePageRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId));
    }

    @Test
    public void deletePageRequest_withEmptyContentId_throwsIllegalArgumentException() {
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
        InputStream attachmentContent = new ByteArrayInputStream("Some text".getBytes());

        // act
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);

        // assert
        assertThat(addAttachmentRequest.getMethod(), is("POST"));
        assertThat(addAttachmentRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment"));
        assertThat(addAttachmentRequest.getFirstHeader("X-Atlassian-Token").getValue(), is("no-check"));

        ByteArrayOutputStream entityContent = new ByteArrayOutputStream();
        addAttachmentRequest.getEntity().writeTo(entityContent);
        String multiPartPayload = entityContent.toString("UTF-8");
        assertThat(multiPartPayload, containsString("attachment.txt"));
        assertThat(multiPartPayload, containsString("Some text"));
    }

    @Test
    public void addAttachmentRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.addAttachmentRequest("", "file.txt", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void addAttachmentRequest_withEmptyAttachmentFileName_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentFileName");

        // arrange + act
        this.httpRequestFactory.addAttachmentRequest("1234", "", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void addAttachmentRequest_withNullAttachmentContent_throwsIllegalArgumentException() {
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

        ByteArrayOutputStream entityContent = new ByteArrayOutputStream();
        updateAttachmentContentRequest.getEntity().writeTo(entityContent);
        String multiPartPayload = entityContent.toString("UTF-8");
        assertThat(multiPartPayload, containsString("hello"));
    }

    @Test
    public void updateAttachmentContentRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("", "45", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void updateAttachmentContentRequest_withEmptyAttachmentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentId must be set");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("1234", "", new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    public void updateAttachmentContentRequest_withNullAttachmentContent_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentContent");

        // arrange + act
        this.httpRequestFactory.updateAttachmentContentRequest("1234", "45", null);
    }

    @Test
    public void deleteAttachmentRequest_withValidParameters_returnsValidHttpDelete() {
        // arrange
        String attachmentId = "att1234";

        // act
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);

        // assert
        assertThat(deleteAttachmentRequest.getMethod(), is("DELETE"));
        assertThat(deleteAttachmentRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + attachmentId));
    }

    @Test
    public void deleteAttachmentRequest_withEmptyAttachmentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentId must be set");

        // arrange + act
        this.httpRequestFactory.deleteAttachmentRequest("");
    }

    @Test
    public void getPageByTitleRequest_withValidParameters_returnsValidHttpGet() {
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
    public void getPageByTitleRequest_withEmptySpaceKey_throwsIllegalArgumentException() {
        // arrange
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("spaceKey must be set");

        // act
        this.httpRequestFactory.getPageByTitleRequest("", "Some page");
    }

    @Test
    public void getPageByTitleRequest_withEmptyTitle_throwsIllegalArgumentException() {
        // arrange
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("title must be set");

        // act
        this.httpRequestFactory.getPageByTitleRequest("~personalSpace", "");
    }

    @Test
    public void getAttachmentByFileNameRequest_withMinimalParameters_returnsValidHttpGet() {
        // arrange
        String contentId = "1234";
        String attachmentFileName = "file.txt";

        // act
        HttpGet getAttachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName, null);

        // assert
        assertThat(getAttachmentByFileNameRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?filename=" + attachmentFileName));
    }

    @Test
    public void getAttachmentByFileNameRequest_withExpandOptions_returnsValidHttpGetWithExpandOptions() {
        // arrange
        String contentId = "1234";
        String attachmentFileName = "file.txt";
        String expandOptions = "version";

        // act
        HttpGet getAttachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName, expandOptions);

        // assert
        assertThat(getAttachmentByFileNameRequest.getURI().toString(), containsString("filename=" + attachmentFileName));
        assertThat(getAttachmentByFileNameRequest.getURI().toString(), containsString("expand=" + expandOptions));
    }

    @Test
    public void getAttachmentByFileNameRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("contentId must be set");

        // act
        this.httpRequestFactory.getAttachmentByFileNameRequest("", "file.txt", null);
    }

    @Test
    public void getAttachmentByFileNameRequest_withEmptyAttachmentFileName_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("attachmentFileName must be set");

        // act
        this.httpRequestFactory.getAttachmentByFileNameRequest("1234", "", null);
    }

    @Test
    public void getPageByIdRequest_withValidParameter_returnsValidHttpGet() {
        // arrange
        String contentId = "1234";

        // act
        HttpGet getPageByIdRequest = this.httpRequestFactory.getPageByIdRequest(contentId, "body.storage,version");

        // assert
        assertThat(getPageByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "?expand=body.storage,version"));
    }

    @Test
    public void getChildPagesByIdRequest_withMinimalParameters_returnsValidHttpGet() {
        // arrange
        String parentContentId = "1234";

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, null, null, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page"));
    }

    @Test
    public void getChildPagesByIdRequest_withBlankParentContentId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("parentContentId must be set");

        // arrange + act
        this.httpRequestFactory.getChildPagesByIdRequest("", null, null, null);
    }

    @Test
    public void getChildPagesByIdRequest_withLimit_returnsHttpGetWithLimit() {
        // arrange
        String parentContentId = "1234";
        int limit = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, limit, null, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page?limit=" + limit));
    }

    @Test
    public void getChildPagesByIdRequest_withPageNumber_returnsHttpGetWithLimit() {
        // arrange
        String parentContentId = "1234";
        int start = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, null, start, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page?start=" + start));
    }

    @Test
    public void getChildPagesByIdRequest_withExpandOptions_returnsHttpGetWithExpandOption() {
        // arrange
        String parentContentId = "1234";
        String expandOptions = "version";

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, null, null, expandOptions);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + parentContentId + "/child/page?expand=" + expandOptions));
    }

    @Test
    public void getChildPagesByIdRequest_withLimitAndPageNumber_returnsHttpGetWithPageNumberAndLimit() {
        // arrange
        String parentContentId = "1234";
        int limit = 10;
        int start = 5;

        // act
        HttpGet getChildPagesByIdRequest = this.httpRequestFactory.getChildPagesByIdRequest(parentContentId, limit, start, null);

        // assert
        assertThat(getChildPagesByIdRequest.getURI().toString(), containsString("limit=" + limit));
        assertThat(getChildPagesByIdRequest.getURI().toString(), containsString("start=" + start));
    }

    @Test
    public void getAttachmentsRequest_withMinimalParameters_returnsValidHttpGetRequest() {
        // arrange
        String contentId = "1234";

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, null, null, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment"));
    }

    @Test
    public void getAttachmentsRequest_withLimit_returnsHttpGetWithLimit() {
        // arrange
        String contentId = "1234";
        int limit = 5;

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, limit, null, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?limit=" + limit));
    }

    @Test
    public void getAttachmentsRequest_withPageNumber_returnsHttpGetWithPage() {
        // arrange
        String contentId = "1234";
        int start = 1;

        // act
        HttpGet getAttachmentsRequest = this.httpRequestFactory.getAttachmentsRequest(contentId, null, start, null);

        // assert
        assertThat(getAttachmentsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/child/attachment?start=" + start));
    }

    @Test
    public void getAttachmentsRequest_withLimitAndPageNumberAndExpandOptions_returnsHttpGetWithLimitAndPage() {
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

    @Test
    public void getAttachmentContentRequest_withValidParameters_returnsHttpGetRequest() {
        // arrange
        String relativeDownloadLink = "/download/attachment.txt";

        // act
        HttpGet getAttachmentContentRequest = this.httpRequestFactory.getAttachmentContentRequest(relativeDownloadLink);

        // assert
        assertThat(getAttachmentContentRequest.getURI().toString(), is(ROOT_CONFLUENCE_URL + relativeDownloadLink));
    }

    @Test
    public void getAttachmentContentRequest_withBlankRelativeDownloadLink_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("relativeDownloadLink must be set");

        // arrange + act
        this.httpRequestFactory.getAttachmentContentRequest("");
    }

    @Test
    public void setPropertyByKeyRequest_withValidParameters_returnsHttpPostRequest() throws Exception {
        // arrange
        String contentId = "1234";
        String key = "content-hash";
        String value = "38495fsj98wgh";

        // act
        HttpPost setPropertyByKeyRequest = this.httpRequestFactory.setPropertyByKeyRequest(contentId, key, value);

        // assert
        assertThat(setPropertyByKeyRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/property"));
        assertThat(setPropertyByKeyRequest.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));

        String jsonPayload = inputStreamAsString(setPropertyByKeyRequest.getEntity().getContent(), UTF_8);
        String expectedJsonPayload = fileContent(Paths.get(CLASS_LOCATION, "set-property-by-key-request-payload.json").toString(), UTF_8);
        assertThat(jsonPayload, isSameJsonAs(expectedJsonPayload));
    }

    @Test
    public void getPropertyByKeyRequest_withValidParameters_returnsHttpGetRequest() {
        // arrange
        String contentId = "1234";
        String key = "content-hash";

        // act
        HttpGet getPropertyByKeyRequest = this.httpRequestFactory.getPropertyByKeyRequest(contentId, key);

        // assert
        assertThat(getPropertyByKeyRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/property/" + key + "?expand=value"));
    }

    @Test
    public void deletePropertyByKeyRequest_withValidParameters_returnsHttpDeleteRequest() {
        // arrange
        String contentId = "1234";
        String key = "content-hash";

        // act
        HttpDelete deletePropertyByKeyRequest = this.httpRequestFactory.deletePropertyByKeyRequest(contentId, key);

        // assert
        assertThat(deletePropertyByKeyRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/property/" + key));
    }

    @Test
    public void getLabels_withValidParameters_returnsHttpGetRequest() {
        // arrange
        String contentId = "1234";

        // act
        HttpGet getLabelsRequest = this.httpRequestFactory.getLabelsRequest(contentId);

        // assert
        assertThat(getLabelsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/label"));
    }

    @Test
    public void addLabels_withValidParameters_returnsHttpPostRequest() {
        // arrange
        String contentId = "1234";
        List<String> labels = singletonList("foo");

        // act
        HttpPost addLabelsRequest = this.httpRequestFactory.addLabelsRequest(contentId, labels);

        // assert
        assertThat(addLabelsRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/label"));
    }

    @Test
    public void deleteLabel_withValidParameters_returnsHttpDeleteRequest() {
        // arrange
        String contentId = "1234";
        String label = "foo";

        // act
        HttpDelete deleteLabelRequest = this.httpRequestFactory.deleteLabelRequest(contentId, label);

        // assert
        assertThat(deleteLabelRequest.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/" + contentId + "/label?name=" + label));
    }
}
