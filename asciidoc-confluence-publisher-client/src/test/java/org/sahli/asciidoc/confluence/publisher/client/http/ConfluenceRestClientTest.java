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

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluenceRestClientTest {

    private static final String CONFLUENCE_ROOT_URL = "http://confluence.com";

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void instantiation_withEmptyRootConfluenceUrl_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("rootConfluenceUrl must be set");

        // arrange + act
        new ConfluenceRestClient("", anyCloseableHttpClient(), null, null);
    }

    @Test
    public void instantiation_withNullHttpClient_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("httpClient must be set");

        // arrange + act
        new ConfluenceRestClient(CONFLUENCE_ROOT_URL, null, null, null);
    }

    @Test
    public void addPageUnderAncestor_withValidParameters_returnsCreatedPageContentId() throws Exception {
        // arrange
        String expectedContentId = "1234";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"id\":\"" + expectedContentId + "\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        String contentId = confluenceRestClient.addPageUnderAncestor("~personalSpace", "123", "Hello", "Content", "Version Message");

        // assert
        assertThat(contentId, is(expectedContentId));
    }

    @Test
    public void updatePage_withValidParameters_sendUpdateRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"id\":\"1234\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.updatePage("123", "1", "Hello", "Content", 2, "Version Message");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPut.class));
    }

    @Test
    public void deletePage_withValidParameters_sendsDeleteRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 204);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.deletePage("1234");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void getPageByTitle_withValidParameters_sendsGetRequestAndReturnsFirstResultId() throws Exception {
        // arrange
        String expectedContentId = "1234";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"results\": [{\"id\":\"" + expectedContentId + "\"}], \"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        String contentId = confluenceRestClient.getPageByTitle("~personalSpace", "Some title");

        // assert
        assertThat(contentId, is(expectedContentId));
    }

    @Test(expected = NotFoundException.class)
    public void getPageByTitle_withEmptyResult_throwsPageNotFoundException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act + assert
        confluenceRestClient.getPageByTitle("~personalSpace", "Some title");
    }

    @Test(expected = MultipleResultsException.class)
    public void getPageByTitle_withMultipleResults_throwsMultipleResultsException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act + assert
        confluenceRestClient.getPageByTitle("~personalSpace", "Some title");
    }

    @Test
    public void addAttachment_withValidParameters_sendsMultipartHttpPostRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.addAttachment("1234", "file.txt", new ByteArrayInputStream("file content".getBytes()));

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void updateAttachmentContent_withValidParameters_sendsMultipartHttPostRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.updateAttachmentContent("1234", "att12", new ByteArrayInputStream("file content".getBytes()));

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void deleteAttachment_withValidParameters_sendsHttpDeleteRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.deleteAttachment("att12");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void getAttachmentByFileName_withValidParameters_sendsHttpGetRequestAndReturnsConfluenceAttachment() throws Exception {
        // arrange
        String jsonAttachment = "{\"id\": \"att12\", \"title\": \"Attachment.txt\", \"_links\": {\"download\": \"/download/Attachment.txt\"}, \"version\": {\"number\": 1}}";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"results\": [" + jsonAttachment + "], \"size\": 1}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        ConfluenceAttachment confluenceAttachment = confluenceRestClient.getAttachmentByFileName("1234", "file.txt");

        // assert
        assertThat(confluenceAttachment.getId(), is("att12"));
        assertThat(confluenceAttachment.getTitle(), is("Attachment.txt"));
        assertThat(confluenceAttachment.getRelativeDownloadLink(), is("/download/Attachment.txt"));
        assertThat(confluenceAttachment.getVersion(), is(1));
    }

    @Test(expected = NotFoundException.class)
    public void getAttachmentByFileName_withEmptyResult_throwsNotFoundException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"size\": 0}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.getAttachmentByFileName("1234", "file.txt");
    }

    @Test(expected = MultipleResultsException.class)
    public void getAttachmentByFileName_withMultipleResults_throwsMultipleResultsException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.getAttachmentByFileName("4321", "another-file.txt");
    }

    @Test
    public void getPageById_withExistingContentId_returnsPageContent() throws Exception {
        // arrange
        String responseFilePath = "src/test/resources/org/sahli/asciidoc/confluence/publisher/client/http/page-content.json";
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode(fileContent(responseFilePath, UTF_8), 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        ConfluencePage confluencePage = confluenceRestClient.getPageWithContentAndVersionById("1234");

        // assert
        assertThat(confluencePage.getContentId(), is("1234"));
        assertThat(confluencePage.getTitle(), is("Some title"));
        assertThat(confluencePage.getContent(), is("Some content"));
        assertThat(confluencePage.getVersion(), is(1));
    }

    @Test
    public void getChildPages_withValidParametersAndFirstResultSizeSmallerThanLimit_returnsListOfChildPagesWithTitleContentVersionAndId() throws Exception {
        // arrange
        String resultSet = generateJsonPageResults(2);
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"results\": [" + resultSet + "], \"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";

        // act
        List<ConfluencePage> childPages = confluenceRestClient.getChildPages(contentId);

        // assert
        ConfluencePage childOne = new ConfluencePage("1", "Page 1", 1);
        ConfluencePage childTwo = new ConfluencePage("2", "Page 2", 1);
        assertThat(childPages, contains(childOne, childTwo));
    }

    @Test
    public void getChildPages_withValidParametersAndFirstResultSizeHasSameSizeAsLimit_sendsASecondRequestToFetchNextChildPages() throws Exception {
        // arrange
        String firstResultSet = "{\"results\": [" + generateJsonPageResults(25) + "], \"size\": 25}";
        String secondResultSet = "{\"results\": [], \"size\": 0}";
        List<String> jsonResponses = asList(firstResultSet, secondResultSet);
        List<Integer> statusCodes = asList(200, 200);
        CloseableHttpClient httpClientMock = recordHttpClientForMultipleResponsesWithContentAndStatusCode(jsonResponses, statusCodes);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";
        ArgumentCaptor<HttpGet> httpGetArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);

        // act
        List<ConfluencePage> childPages = confluenceRestClient.getChildPages(contentId);

        // assert
        assertThat(childPages.size(), is(25));
        verify(httpClientMock, times(2)).execute(httpGetArgumentCaptor.capture());
        assertThat(httpGetArgumentCaptor.getAllValues().get(0).getURI().toString(), containsString("start=0"));
        assertThat(httpGetArgumentCaptor.getAllValues().get(1).getURI().toString(), containsString("start=1"));
    }

    @Test
    public void getChildPages_withValidParametersAndFirstResultSizeIsHigherThanLimit_sendsASecondRequestToFetchNextChildPages() throws Exception {
        // arrange
        String firstResultSet = "{\"results\": [" + generateJsonPageResults(25) + "], \"size\": 25}";
        String secondResultSet = "{\"results\": [" + generateJsonPageResults(24) + "], \"size\": 24}";
        List<String> jsonResponses = asList(firstResultSet, secondResultSet);
        List<Integer> statusCodes = asList(200, 200);
        CloseableHttpClient httpClientMock = recordHttpClientForMultipleResponsesWithContentAndStatusCode(jsonResponses, statusCodes);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";
        ArgumentCaptor<HttpGet> httpGetArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);

        // act
        List<ConfluencePage> childPages = confluenceRestClient.getChildPages(contentId);

        // assert
        assertThat(childPages.size(), is(49));
        verify(httpClientMock, times(2)).execute(httpGetArgumentCaptor.capture());
        assertThat(httpGetArgumentCaptor.getAllValues().get(0).getURI().toString(), containsString("start=0"));
        assertThat(httpGetArgumentCaptor.getAllValues().get(1).getURI().toString(), containsString("start=1"));
    }

    @Test
    public void getAttachments_withValidParametersAndFirstResultIsSmallerThanLimit_returnsAttachments() throws Exception {
        // arrange
        String resultSet = generateJsonAttachmentResults(2);
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"results\": [" + resultSet + "], \"size\": 2}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";

        // act
        List<ConfluenceAttachment> confluenceAttachments = confluenceRestClient.getAttachments(contentId);

        // assert
        ConfluenceAttachment attachmentOne = new ConfluenceAttachment("1", "Attachment-1.txt", "/download/Attachment-1.txt", 1);
        ConfluenceAttachment attachmentTwo = new ConfluenceAttachment("2", "Attachment-2.txt", "/download/Attachment-2.txt", 1);
        assertThat(confluenceAttachments, contains(attachmentOne, attachmentTwo));
    }

    @Test
    public void getAttachments_withValidParametersAndFirstResultSizeHasSameSizeAsLimit_sendsASecondRequestToFetchNextAttachments() throws Exception {
        // arrange
        String firstResultSet = "{\"results\": [" + generateJsonAttachmentResults(25) + "], \"size\": 25}";
        String secondResultSet = "{\"results\": [], \"size\": 0}";
        List<String> jsonResponses = asList(firstResultSet, secondResultSet);
        List<Integer> statusCodes = asList(200, 200);
        CloseableHttpClient httpClientMock = recordHttpClientForMultipleResponsesWithContentAndStatusCode(jsonResponses, statusCodes);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";
        ArgumentCaptor<HttpGet> httpGetArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);

        // act
        List<ConfluenceAttachment> confluenceAttachments = confluenceRestClient.getAttachments(contentId);

        // assert
        assertThat(confluenceAttachments.size(), is(25));
        verify(httpClientMock, times(2)).execute(httpGetArgumentCaptor.capture());
        assertThat(httpGetArgumentCaptor.getAllValues().get(0).getURI().toString(), containsString("start=0"));
        assertThat(httpGetArgumentCaptor.getAllValues().get(1).getURI().toString(), containsString("start=1"));
    }

    @Test
    public void getAttachments_withValidParametersAndFirstResultIsSmallerThanLimit_sendsASecondRequestToFetchNextAttachments() throws Exception {
        // arrange
        String firstResultSet = "{\"results\": [" + generateJsonAttachmentResults(25) + "], \"size\": 25}";
        String secondResultSet = "{\"results\": [" + generateJsonAttachmentResults(24) + "], \"size\": 0}";
        List<String> jsonResponses = asList(firstResultSet, secondResultSet);
        List<Integer> statusCodes = asList(200, 200);
        CloseableHttpClient httpClientMock = recordHttpClientForMultipleResponsesWithContentAndStatusCode(jsonResponses, statusCodes);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);
        String contentId = "1234";
        ArgumentCaptor<HttpGet> httpGetArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);

        // act
        List<ConfluenceAttachment> confluenceAttachments = confluenceRestClient.getAttachments(contentId);

        // assert
        assertThat(confluenceAttachments.size(), is(49));
        verify(httpClientMock, times(2)).execute(httpGetArgumentCaptor.capture());
        assertThat(httpGetArgumentCaptor.getAllValues().get(0).getURI().toString(), containsString("start=0"));
        assertThat(httpGetArgumentCaptor.getAllValues().get(1).getURI().toString(), containsString("start=1"));
    }

    @Test
    public void sendRequest_withProvidedUsernameAndPassword_setsCredentialsProvider() throws Exception {
        // arrange
        CloseableHttpClient closeableHttpClient = anyCloseableHttpClient();
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient("http://confluence.com", closeableHttpClient, "username", "password");
        HttpGet httpRequest = new HttpGet("http://confluence.com");
        ArgumentCaptor<HttpRequestBase> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequestBase.class);

        // act
        confluenceRestClient.sendRequest(httpRequest, (response) -> null);

        // assert
        verify(closeableHttpClient, times(1)).execute(httpRequestArgumentCaptor.capture());
        HttpRequestBase httpRequestBase = httpRequestArgumentCaptor.getValue();

        assertThat(httpRequestBase, is(httpRequest));
        assertThat(httpRequestBase.getHeaders("Authorization").length, is(1));
        assertThat(httpRequestBase.getFirstHeader("Authorization").getValue(), is("Basic dXNlcm5hbWU6cGFzc3dvcmQ="));
    }

    @Test
    public void setPropertyByKey_withValidParameters_sendsPostRequestForPropertyCreation() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.setPropertyByKey("1234", "content-hash", "hash-value");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void getPropertyByKey_withValidParameters_sendsGetRequestForPropertyRetrieval() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"value\": \"hash-value\"}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        String propertyValue = confluenceRestClient.getPropertyByKey("1234", "content-hash");

        // assert
        assertThat(propertyValue, is("hash-value"));
        verify(httpClientMock, times(1)).execute(any(HttpGet.class));
    }

    @Test
    public void getPropertyByKey_withNonExistingKeyAsParameter_returnsNull() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 404);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        String propertyValue = confluenceRestClient.getPropertyByKey("1234", "content-hash");

        // assert
        assertThat(propertyValue, is(nullValue()));
        verify(httpClientMock, times(1)).execute(any(HttpGet.class));
    }

    @Test
    public void deletePropertyByKey_withValidParameters_sendsDeleteRequestForPropertyKey() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.deletePropertyByKey("1234", "content-hash");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void deletePropertyByKey_withNonExistingKey_sendsDeleteRequestForPropertyKeyAndIgnoresError() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 403);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.deletePropertyByKey("1234", "unknown");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    @Test
    public void addPageUnderAncestor_withUnsuccessfulRequest_throwsExceptionWithRequestInformationAndRootCause() throws Exception {
        // arrange
        IOException exception = new IOException("expected");

        CloseableHttpClient httpClientMock = recordHttpClientForRequestException(exception);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // assert
        this.expectedException.expect(RequestFailedException.class);
        this.expectedException.expectCause(is(equalTo(exception)));
        this.expectedException.expectMessage("request failed (" +
                "request: POST http://confluence.com/rest/api/content " +
                "{\"title\":\"Hello\"," +
                "\"space\":{\"key\":\"~personalSpace\"}," +
                "\"body\":{\"storage\":{\"value\":\"Content\",\"representation\":\"storage\"}}," +
                "\"ancestors\":[{\"id\":\"123\"}]," +
                "\"version\":{\"number\":1,\"message\":\"Version Message\"}," +
                "\"type\":\"page\"}, " +
                "response: <none>, " +
                "reason: 'expected'" +
                ")");

        // act
        confluenceRestClient.addPageUnderAncestor("~personalSpace", "123", "Hello", "Content", "Version Message");
    }

    @Test
    public void addPageUnderAncestor_withUnsuccessfulResponse_throwsExceptionWithResponseInformation() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"some\": \"json\"}", 404, "reason");
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // assert
        this.expectedException.expect(RequestFailedException.class);
        this.expectedException.expectMessage("request failed (" +
                "request: POST http://confluence.com/rest/api/content " +
                "{\"title\":\"Hello\"," +
                "\"space\":{\"key\":\"~personalSpace\"}," +
                "\"body\":{\"storage\":{\"value\":\"Content\",\"representation\":\"storage\"}}," +
                "\"ancestors\":[{\"id\":\"123\"}]," +
                "\"version\":{\"number\":1,\"message\":\"Version Message\"}," +
                "\"type\":\"page\"}, " +
                "response: 404 reason " +
                "{\"some\": \"json\"}" +
                ")");

        // act
        confluenceRestClient.addPageUnderAncestor("~personalSpace", "123", "Hello", "Content", "Version Message");
    }

    @Test
    public void getLabels_returnsLabels() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("{\"results\": [{\"prefix\": \"global\", \"name\": \"label\"}, {\"prefix\": \"foo\", \"name\": \"bar\"}]}", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        List<String> labels = confluenceRestClient.getLabels("123456");

        // assert
        assertThat(labels.size(), is(2));
        assertThat(labels, hasItem("label"));
        assertThat(labels, hasItem("bar"));
    }

    @Test
    public void addLabels_sendsPostRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.addLabels("123456", asList("foo", "bar"));

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void deleteLabel_sendsDeleteRequest() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForSingleResponseWithContentAndStatusCode("", 200);
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(CONFLUENCE_ROOT_URL, httpClientMock, null, null);

        // act
        confluenceRestClient.deleteLabel("123456", "foo");

        // assert
        verify(httpClientMock, times(1)).execute(any(HttpDelete.class));
    }

    private String generateJsonAttachmentResults(int numberOfAttachment) {
        return IntStream.range(1, numberOfAttachment + 1)
                .boxed()
                .map(attachmentNumber -> "{\"id\": \"" + attachmentNumber + "\", \"title\": \"Attachment-" + attachmentNumber +
                        ".txt\", \"_links\": {\"download\": \"/download/Attachment-" + attachmentNumber +
                        ".txt\"}, \"version\": {\"number\": 1}}")
                .collect(Collectors.joining(",\n"));
    }

    private static CloseableHttpClient recordHttpClientForSingleResponseWithContentAndStatusCode(String contentPayload, int statusCode) throws IOException {
        return recordHttpClientForSingleResponseWithContentAndStatusCode(contentPayload, statusCode, null);
    }

    private static CloseableHttpClient recordHttpClientForRequestException(IOException exception) throws IOException {
        CloseableHttpClient httpClientMock = anyCloseableHttpClient();
        when(httpClientMock.execute(any(HttpRequestBase.class))).thenThrow(exception);

        return httpClientMock;
    }

    private static CloseableHttpClient recordHttpClientForSingleResponseWithContentAndStatusCode(String contentPayload, int statusCode, String reason) throws IOException {
        CloseableHttpResponse httpResponseMock = mock(CloseableHttpResponse.class);
        HttpEntity httpEntityMock = recordHttpEntityForContent(contentPayload);

        when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);

        StatusLine statusLineMock = recordStatusLine(statusCode, reason);
        when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);

        CloseableHttpClient httpClientMock = anyCloseableHttpClient();
        when(httpClientMock.execute(any(HttpRequestBase.class))).thenReturn(httpResponseMock);

        return httpClientMock;
    }

    private static CloseableHttpClient recordHttpClientForMultipleResponsesWithContentAndStatusCode(List<String> contentPayloads, List<Integer> statusCodes) throws IOException {
        CloseableHttpResponse httpResponseMock = mock(CloseableHttpResponse.class);

        List<HttpEntity> httpEntities = contentPayloads.stream().map(ConfluenceRestClientTest::recordHttpEntityForContent).collect(toList());
        when(httpResponseMock.getEntity())
                .thenReturn(httpEntities.get(0), httpEntities.subList(1, httpEntities.size()).toArray(new HttpEntity[httpEntities.size() - 1]));

        List<StatusLine> statusLines = statusCodes.stream().map((statusCode) -> recordStatusLine(statusCode, null)).collect(toList());
        when(httpResponseMock.getStatusLine())
                .thenReturn(statusLines.get(0), statusLines.subList(1, statusLines.size()).toArray(new StatusLine[statusLines.size() - 1]));

        CloseableHttpClient httpClientMock = anyCloseableHttpClient();
        when(httpClientMock.execute(any(HttpRequestBase.class))).thenReturn(httpResponseMock);

        return httpClientMock;
    }

    private static HttpEntity recordHttpEntityForContent(String content) {
        HttpEntity httpEntityMock = mock(HttpEntity.class);
        try {
            when(httpEntityMock.getContent()).thenReturn(new ByteArrayInputStream(content.getBytes()));
            when(httpEntityMock.getContentEncoding()).thenReturn(new BasicHeader("Content-Encoding", "UTF-8"));
        } catch (IOException e) {
            fail(e.getMessage());
        }

        return httpEntityMock;
    }

    private static CloseableHttpClient anyCloseableHttpClient() {
        return mock(CloseableHttpClient.class);
    }

    private static StatusLine recordStatusLine(int statusCode, String reason) {
        StatusLine statusLineMock = mock(StatusLine.class);
        when(statusLineMock.getStatusCode()).thenReturn(statusCode);
        when(statusLineMock.getReasonPhrase()).thenReturn(reason);

        return statusLineMock;
    }

    private static String generateJsonPageResults(int numberOfPages) {
        return IntStream.range(1, numberOfPages + 1)
                .boxed()
                .map(pageNumber -> "{" +
                        "\"id\": \"" + pageNumber + "\", " +
                        "\"title\": \"Page " + pageNumber + "\", " +
                        "\"version\": {\"number\": 1}," +
                        "\"ancestors\": [{\"id\": \"ancestor\"}]" +
                        "}")
                .collect(Collectors.joining(",\n"));
    }

}
