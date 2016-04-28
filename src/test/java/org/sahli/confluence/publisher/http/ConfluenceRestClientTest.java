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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.fileContent;

public class ConfluenceRestClientTest {

    private static final String CONFLUENCE_ROOT_URL = "http://confluence.com";
    @Rule
    public ExpectedException expectedException = none();

    @Test
    public void instantiation_withEmptyRootConfluenceUrl_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("rootConfluenceUrl must be set");

        // arrange + act
        new ConfluenceRestClient("", anyHttpClient());
    }

    @Test
    public void instantiation_withNullHttpClient_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("httpClient must be set");

        // arrange + act
        new ConfluenceRestClient(CONFLUENCE_ROOT_URL, null);
    }

    @Test
    public void instantiation_withUsernameAndPassword_addsAuthenticationHeadersToRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"id\":\"12345\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, "username", "password");
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);

        // act
        confluenceRestClient.addPageUnderAncestor("~personalSpace", "1234", "title", "content");

        // assert
        verify(httpClientMock, times(1)).execute(httpPostArgumentCaptor.capture());
        assertThat(httpPostArgumentCaptor.getValue().getFirstHeader("Authorization").getValue(), is(notNullValue()));
    }

    @Test
    public void addPageUnderSpace_withValidParameters_returnsCreatedPageContentId() throws Exception {
        // arrange
        String expectedContentId = "1234";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"id\":\"" + expectedContentId + "\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        String contentId = confluenceRestClient.addPageUnderSpace("~personalSpace", "Hello", "Content");

        // assert
        assertThat(contentId, is(expectedContentId));
    }

    @Test
    public void addPageUnderAncestor_withValidParameters_returnsCreatedPageContentId() throws Exception {
        // arrange
        String expectedContentId = "1234";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"id\":\"" + expectedContentId + "\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        String contentId = confluenceRestClient.addPageUnderAncestor("~personalSpace", "123", "Hello", "Content");

        // assert
        assertThat(contentId, is(expectedContentId));
    }

    @Test
    public void updatePage_withValidParameters_sendUpdateRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"id\":\"1234\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.updatePage("123", "Hello", "Content", 2);

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPut.class));
    }

    @Test
    public void deletePage_withValidParameters_sendsDeleteRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("", 204);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.deletePage("1234");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void getPageByTitle_withValidParameters_sendsGetRequestAndReturnsFirstResultId() throws Exception {
        // arrange
        String expectedContentId = "1234";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"results\": [{\"id\":\"" + expectedContentId + "\"}], \"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        String contentId = confluenceRestClient.getPageByTitle("~personalSpace", "Some title");

        // assert
        assertThat(contentId, is(expectedContentId));
    }

    @Test(expected = NotFoundException.class)
    public void getPageByTitle_withEmptyResult_throwsPageNotFoundException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act + assert
        confluenceRestClient.getPageByTitle("~personalSpace", "Some title");
    }

    @Test(expected = MultipleResultsException.class)
    public void getPageByTitle_withMultipleResults_throwsMultipleResultsException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act + assert
        confluenceRestClient.getPageByTitle("~personalSpace", "Some title");
    }

    @Test
    public void addAttachment_withValidParameters_sendsMultipartHttpPostRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.addAttachment("1234", "file.txt", new ByteArrayInputStream("file content".getBytes()));

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void updateAttachmentContent_withValidParameters_sendsMultipartHttPostRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.updateAttachmentContent("1234", "att12", new ByteArrayInputStream("file content".getBytes()));

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void deleteAttachment_withValidParameters_sendsHttpDeleteRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.deleteAttachment("att12");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void getAttachmentByFileName_withValidParameters_sendsHttpGetRequest() throws Exception {
        // arrange
        String expectedAttachmentId = "att12";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"results\": [{\"id\":\"" + expectedAttachmentId + "\"}], \"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        String attachmentId = confluenceRestClient.getAttachmentByFileName("1234", "file.txt");

        // assert
        assertThat(attachmentId, is(expectedAttachmentId));
    }

    @Test(expected = NotFoundException.class)
    public void getAttachmentByFileName_withEmptyResult_throwsNotFoundException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.getAttachmentByFileName("1234", "file.txt");
    }

    @Test(expected = MultipleResultsException.class)
    public void getAttachmentByFileName_withMultipleResults_throwsMultipleResultsException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        confluenceRestClient.getAttachmentByFileName("4321", "another-file.txt");
    }

    @Test
    public void getPageById_withExistingContentId_returnsPageContent() throws Exception {
        // arrange
        String responseFilePath = "src/test/resources/org/sahli/confluence/publisher/http/page-content.json";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse(fileContent(responseFilePath), 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        ConfluencePage confluencePage = confluenceRestClient.getPageWithContentAndVersionById("1234");

        // assert
        assertThat(confluencePage.getContentId(), is("1234"));
        assertThat(confluencePage.getTitle(), is("Some title"));
        assertThat(confluencePage.getContent(), is("Some content"));
        assertThat(confluencePage.getVersion(), is(1));
    }


    @Test
    public void pageExistsByTitle_withExistingPageTitleParameter_returnsTrue() throws Exception {
        // arrange
        String title = "Some title";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        boolean pageExistsByTitle = confluenceRestClient.pageExistsByTitle("~personalSpace", title);

        // assert
        assertThat(pageExistsByTitle, is(true));
    }

    @Test
    public void pageExistsByTitle_withNonExistingPageTitleParameter_returnsTrue() throws Exception {
        // arrange
        String title = "Some title";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        boolean pageExistsByTitle = confluenceRestClient.pageExistsByTitle("~personalSpace", title);

        // assert
        assertThat(pageExistsByTitle, is(false));
    }

    @Test
    public void attachmentExistsByFileName_withExistingAttachment_returnsTrue() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        boolean attachmentExistsByFileName = confluenceRestClient.attachmentExistsByFileName("1234", "file.txt");

        // assert
        assertThat(attachmentExistsByFileName, is(true));
    }

    @Test
    public void attachmentExistsByFileName_withNonExistingAttachment_returnsTrue() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        boolean attachmentExistsByFileName = confluenceRestClient.attachmentExistsByFileName("1234", "file.txt");

        // assert
        assertThat(attachmentExistsByFileName, is(false));
    }

    @Test
    public void attachmentExistsByFileName_withNonExistingContentId_returnsFalse() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleJsonAndStatusCodeResponse("", 404);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock);

        // act
        boolean attachmentExistsByFileName = confluenceRestClient.attachmentExistsByFileName("abc", "file.txt");

        // assert
        assertThat(attachmentExistsByFileName, is(false));
    }

    private static CloseableHttpClient recordHttpClientForSingleJsonAndStatusCodeResponse(String jsonResponse, int statusCode) throws IOException {
        CloseableHttpResponse httpResponseMock = mock(CloseableHttpResponse.class);
        HttpEntity httpEntityMock = recordHttpEntityForContent(jsonResponse);

        when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
        recordStatusLine(httpResponseMock, statusCode);

        CloseableHttpClient httpClientMock = anyHttpClient();
        when(httpClientMock.execute(any(HttpPost.class))).thenReturn(httpResponseMock);

        return httpClientMock;
    }

    private static HttpEntity recordHttpEntityForContent(String jsonResponse) throws IOException {
        HttpEntity httpEntityMock = mock(HttpEntity.class);
        when(httpEntityMock.getContent()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes()));

        return httpEntityMock;
    }

    private static CloseableHttpClient anyHttpClient() {
        return mock(CloseableHttpClient.class);
    }

    private static void recordStatusLine(HttpResponse httpResponseMock, int statusCode) {
        StatusLine statusLineMock = mock(StatusLine.class);
        when(statusLineMock.getStatusCode()).thenReturn(statusCode);
        when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    }
}