/*
 * Copyright 2026 the original author or authors.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Christian Stettler
 */
public class HttpRequestV2FactoryTest {

    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";

    private static final String ROOT_CONFLUENCE_URL = "http://confluence.com";
    private static final String CONFLUENCE_API_V2_ENDPOINT = ROOT_CONFLUENCE_URL + "/api/v2";
    private static final String CONFLUENCE_REST_API_ENDPOINT = ROOT_CONFLUENCE_URL + "/rest/api";

    private static final String ATLASSIAN_API_GATEWAY_CLOUD_ID = "abc123def456";
    private static final String ATLASSIAN_API_GATEWAY_URL = "https://api.atlassian.com/ex/confluence/" + ATLASSIAN_API_GATEWAY_CLOUD_ID;
    private static final String ATLASSIAN_API_V2_ENDPOINT = ATLASSIAN_API_GATEWAY_URL + "/api/v2";
    private static final String ATLASSIAN_REST_API_ENDPOINT = ATLASSIAN_API_GATEWAY_URL + "/rest/api";
    private static final String ATLASSIAN_SERVER_URL = "https://api.atlassian.com";

    private HttpRequestV2Factory httpRequestFactory;

    @BeforeEach
    public void setUp() {
        this.httpRequestFactory = new HttpRequestV2Factory(ROOT_CONFLUENCE_URL);
    }

    @Test
    public void instantiation_withEmptyConfluenceRestApiEndpoint_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            new HttpRequestV2Factory("");
        });
    }

    @Test
    public void addPageUnderAncestorRequest_withValidParameters_returnsHttpPostToApiV2Endpoint() {
        // act
        HttpPost request = this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "1234", "title", "content", "version message");

        // assert
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_API_V2_ENDPOINT + "/pages"));
        assertThat(request.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));
    }

    @Test
    public void addPageUnderAncestorRequest_withBlankTitle_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.addPageUnderAncestorRequest("~personalSpace", "1234", "", "content", "version message");
        });
    }

    @Test
    public void updatePageRequest_withValidParameters_returnsHttpPutToApiV2Endpoint() {
        // act
        HttpPut request = this.httpRequestFactory.updatePageRequest("1234", "1", "title", "content", 2, "version message", false);

        // assert
        assertThat(request.getMethod(), is("PUT"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234"));
        assertThat(request.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));
    }

    @Test
    public void updatePageRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.updatePageRequest("", "1", "title", "content", 2, "version message", true);
        });
    }

    @Test
    public void deletePageRequest_withValidParameters_returnsHttpDeleteToApiV2Endpoint() {
        // act
        HttpDelete request = this.httpRequestFactory.deletePageRequest("1234");

        // assert
        assertThat(request.getMethod(), is("DELETE"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234"));
    }

    @Test
    public void deletePageRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.deletePageRequest("");
        });
    }

    @Test
    public void addAttachmentRequest_withValidParameters_fallsBackToV1RestApiEndpoint() throws Exception {
        // arrange
        InputStream content = new ByteArrayInputStream("Some text".getBytes());

        // act
        HttpPost request = this.httpRequestFactory.addAttachmentRequest("1234", "attachment.txt", content);

        // assert
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/1234/child/attachment"));
        assertThat(request.getFirstHeader("X-Atlassian-Token").getValue(), is("no-check"));

        ByteArrayOutputStream entityContent = new ByteArrayOutputStream();
        request.getEntity().writeTo(entityContent);
        assertThat(entityContent.toString(UTF_8), containsString("attachment.txt"));
        assertThat(entityContent.toString(UTF_8), containsString("Some text"));
    }

    @Test
    public void addAttachmentRequest_withEmptyContentId_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.addAttachmentRequest("", "file.txt", new ByteArrayInputStream("hello".getBytes()));
        });
    }

    @Test
    public void updateAttachmentContentRequest_withValidParametersAndNotifyWatchersOff_fallsBackToV1RestApiEndpointWithMinorEdit() throws Exception {
        // act
        HttpPost request = this.httpRequestFactory.updateAttachmentContentRequest("1234", "45", new ByteArrayInputStream("hello".getBytes()), false);

        // assert
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/1234/child/attachment/45/data"));
        assertThat(request.getFirstHeader("X-Atlassian-Token").getValue(), is("no-check"));

        ByteArrayOutputStream entityContent = new ByteArrayOutputStream();
        request.getEntity().writeTo(entityContent);
        assertThat(entityContent.toString(UTF_8), containsString("Content-Disposition: form-data; name=\"file\"\r\n\r\nhello"));
        assertThat(entityContent.toString(UTF_8), containsString("Content-Disposition: form-data; name=\"minorEdit\"\r\n\r\ntrue"));
    }

    @Test
    public void updateAttachmentContentRequest_withNotifyWatchersOn_doesNotIncludeMinorEdit() throws Exception {
        // act
        HttpPost request = this.httpRequestFactory.updateAttachmentContentRequest("1234", "45", new ByteArrayInputStream("hello".getBytes()), true);

        // assert
        ByteArrayOutputStream entityContent = new ByteArrayOutputStream();
        request.getEntity().writeTo(entityContent);
        assertThat(entityContent.toString(UTF_8), not(containsString("minorEdit")));
    }

    @Test
    public void deleteAttachmentRequest_withValidParameters_returnsHttpDeleteToApiV2Endpoint() {
        // act
        HttpDelete request = this.httpRequestFactory.deleteAttachmentRequest("att1234");

        // assert
        assertThat(request.getMethod(), is("DELETE"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_API_V2_ENDPOINT + "/attachments/att1234"));
    }

    @Test
    public void getPageByTitleRequest_withValidParameters_returnsHttpGetWithTitleParameter() {
        // act
        HttpGet request = this.httpRequestFactory.getPageByTitleRequest("spaceId", "Some page");

        // assert
        assertThat(request.getMethod(), is("GET"));
        assertThat(request.getURI().toString(), containsString(CONFLUENCE_API_V2_ENDPOINT + "/spaces/spaceId/pages"));
        assertThat(request.getURI().toString(), containsString("title=Some+page"));
    }

    @Test
    public void getAttachmentByFileNameRequest_withValidParameters_returnsHttpGetWithFilenameParameter() {
        // act
        HttpGet request = this.httpRequestFactory.getAttachmentByFileNameRequest("1234", "file.txt", null);

        // assert
        assertThat(request.getMethod(), is("GET"));
        assertThat(request.getURI().toString(), containsString(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/attachments"));
        assertThat(request.getURI().toString(), containsString("filename=file.txt"));
    }

    @Test
    public void getPageByIdRequest_withValidParameters_returnsHttpGetToApiV2Endpoint() {
        // act
        HttpGet request = this.httpRequestFactory.getPageByIdRequest("1234", null);

        // assert
        assertThat(request.getMethod(), is("GET"));
        assertThat(request.getURI().toString(), startsWith(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234"));
    }

    @Test
    public void getChildPagesByIdRequest_withValidParameters_returnsHttpGetToApiV2Endpoint() {
        // act
        HttpGet request = this.httpRequestFactory.getChildPagesByIdRequest("1234", 25, 0, null);

        // assert
        assertThat(request.getMethod(), is("GET"));
        assertThat(request.getURI().toString(), startsWith(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/direct-children"));
    }

    @Test
    public void getNextChildPagesByIdRequest_withRelativeNextLink_prependsConfluenceServerUrl() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ROOT_CONFLUENCE_URL);
        String nextLink = "/api/v2/pages?cursor=xxx&limit=25";

        // act
        HttpGet request = factory.getNextChildPagesByIdRequest(nextLink);

        // assert
        assertThat(request.getURI().toString(), is(ROOT_CONFLUENCE_URL + nextLink));
    }

    @Test
    public void getNextChildPagesByIdRequest_withEmptyNextLink_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.getNextChildPagesByIdRequest("");
        });
    }

    @Test
    public void getAttachmentsRequest_withValidParameters_returnsHttpGetToApiV2Endpoint() {
        // act
        HttpGet request = this.httpRequestFactory.getAttachmentsRequest("1234", 25, 0, null);

        // assert
        assertThat(request.getMethod(), is("GET"));
        assertThat(request.getURI().toString(), startsWith(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/attachments"));
    }

    @Test
    public void getNextAttachmentsRequest_withRelativeNextLink_prependsConfluenceServerUrl() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ROOT_CONFLUENCE_URL);
        String nextLink = "/api/v2/pages/1234/attachments?cursor=yyy&limit=25";

        // act
        HttpGet request = factory.getNextAttachmentsRequest(nextLink);

        // assert
        assertThat(request.getURI().toString(), is(ROOT_CONFLUENCE_URL + nextLink));
    }

    @Test
    public void getNextAttachmentsRequest_withEmptyNextLink_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.getNextAttachmentsRequest("");
        });
    }

    @Test
    public void getAttachmentContentRequest_withRelativeDownloadLink_returnsUrlWithServerUrlPrefix() {
        // arrange
        String relativeDownloadLink = "/download/attachment.txt";

        // act
        HttpGet request = this.httpRequestFactory.getAttachmentContentRequest(relativeDownloadLink);

        // assert
        assertThat(request.getURI().toString(), is(ROOT_CONFLUENCE_URL + relativeDownloadLink));
    }

    @Test
    public void getAttachmentContentRequest_withBlankRelativeDownloadLink_throwsIllegalArgumentException() {
        // assert
        assertThrows(IllegalArgumentException.class, () -> {
            // arrange + act
            this.httpRequestFactory.getAttachmentContentRequest("");
        });
    }

    @Test
    public void getPropertyByKeyRequest_withValidParameters_returnsHttpGetToApiV2Endpoint() {
        // act
        HttpGet request = this.httpRequestFactory.getPropertyByKeyRequest("1234", "content-hash");

        // assert
        assertThat(request.getURI().toString(), containsString(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/properties"));
        assertThat(request.getURI().toString(), containsString("key=content-hash"));
    }

    @Test
    public void setPropertyByKeyRequest_withValidParameters_returnsHttpPostToApiV2Endpoint() {
        // act
        HttpPost request = this.httpRequestFactory.setPropertyByKeyRequest("1234", "content-hash", "abc123");

        // assert
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/properties"));
        assertThat(request.getFirstHeader("Content-Type").getValue(), is(APPLICATION_JSON_UTF8));
    }

    @Test
    public void deletePropertyByKeyRequest_withValidParameters_returnsHttpDeleteToApiV2Endpoint() {
        // act
        HttpDelete request = this.httpRequestFactory.deletePropertyByKeyRequest("1234", "content-hash");

        // assert
        assertThat(request.getMethod(), is("DELETE"));
        assertThat(request.getURI().toString(), containsString(CONFLUENCE_API_V2_ENDPOINT + "/pages/1234/properties"));
    }

    @Test
    public void addLabelsRequest_withValidParameters_fallsBackToV1RestApiEndpoint() {
        // arrange
        List<String> labels = singletonList("foo");

        // act
        HttpPost request = this.httpRequestFactory.addLabelsRequest("1234", labels);

        // assert
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/1234/label"));
    }

    @Test
    public void deleteLabelRequest_withValidParameters_fallsBackToV1RestApiEndpoint() {
        // act
        HttpDelete request = this.httpRequestFactory.deleteLabelRequest("1234", "foo");

        // assert
        assertThat(request.getMethod(), is("DELETE"));
        assertThat(request.getURI().toString(), is(CONFLUENCE_REST_API_ENDPOINT + "/content/1234/label?name=foo"));
    }

    // --- Atlassian API Gateway URL (scoped token) tests ---

    @Test
    public void addPageUnderAncestorRequest_withAtlassianApiGatewayUrl_usesApiGatewayV2Endpoint() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);

        // act
        HttpPost request = factory.addPageUnderAncestorRequest("spaceId", "1234", "title", "content", "message");

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_API_V2_ENDPOINT + "/pages"));
    }

    @Test
    public void addAttachmentRequest_withAtlassianApiGatewayUrl_fallsBackToApiGatewayV1RestEndpoint() throws Exception {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);

        // act
        HttpPost request = factory.addAttachmentRequest("1234", "file.txt", new ByteArrayInputStream("content".getBytes()));

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_REST_API_ENDPOINT + "/content/1234/child/attachment"));
    }

    @Test
    public void addLabelsRequest_withAtlassianApiGatewayUrl_fallsBackToApiGatewayV1RestEndpoint() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);

        // act
        HttpPost request = factory.addLabelsRequest("1234", singletonList("foo"));

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_REST_API_ENDPOINT + "/content/1234/label"));
    }

    @Test
    public void getNextChildPagesByIdRequest_withAtlassianApiGatewayUrl_prependsServerHostWithoutPath() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);
        String nextLink = "/ex/confluence/" + ATLASSIAN_API_GATEWAY_CLOUD_ID + "/api/v2/pages?cursor=xxx&limit=25";

        // act
        HttpGet request = factory.getNextChildPagesByIdRequest(nextLink);

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_SERVER_URL + nextLink));
    }

    @Test
    public void getNextAttachmentsRequest_withAtlassianApiGatewayUrl_prependsServerHostWithoutPath() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);
        String nextLink = "/ex/confluence/" + ATLASSIAN_API_GATEWAY_CLOUD_ID + "/api/v2/pages/1234/attachments?cursor=yyy";

        // act
        HttpGet request = factory.getNextAttachmentsRequest(nextLink);

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_SERVER_URL + nextLink));
    }

    @Test
    public void getAttachmentContentRequest_withAtlassianApiGatewayUrl_usesServerHostWithoutPathPrefix() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory(ATLASSIAN_API_GATEWAY_URL);
        String relativeDownloadLink = "/ex/confluence/" + ATLASSIAN_API_GATEWAY_CLOUD_ID + "/download/attachments/123/file.png";

        // act
        HttpGet request = factory.getAttachmentContentRequest(relativeDownloadLink);

        // assert
        assertThat(request.getURI().toString(), is(ATLASSIAN_SERVER_URL + relativeDownloadLink));
    }

    @Test
    public void getAttachmentContentRequest_withStandardConfluenceCloudUrl_usesServerHostWithoutPathPrefix() {
        // arrange
        HttpRequestV2Factory factory = new HttpRequestV2Factory("https://mysite.atlassian.net/wiki");
        String relativeDownloadLink = "/wiki/download/attachments/123/file.png";

        // act
        HttpGet request = factory.getAttachmentContentRequest(relativeDownloadLink);

        // assert
        assertThat(request.getURI().toString(), is("https://mysite.atlassian.net" + relativeDownloadLink));
    }
}
