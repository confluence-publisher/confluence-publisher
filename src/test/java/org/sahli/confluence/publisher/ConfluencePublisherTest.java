package org.sahli.confluence.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sahli.confluence.publisher.ConfluencePublisherTest.SameJsonAsMatcher.isSameJsonAs;

public class ConfluencePublisherTest {

    public static final String API_ENDPOINT = "http//www.confluence.com/rest/api";
    private static final String TEST_RESOURCES = "src/test/resources/org/sahli/confluence/publisher";

    @Rule
    public ExpectedException expectedException = none();

    @Test
    public void metadata_withOnePageAndParentContentId_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", null, "", "");

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getParentContentId(), is("72189173"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
        assertThat(confluencePage.getSha256ContentHash(), is("e83c661e9dc28773432ba324c7ca1551b9f5b859dcd23771de1b14272eedda06"));
    }

    @Test
    public void metadata_withOnePageSpaceKey_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", null, "", "");

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getSpaceKey(), is("~personalSpace"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
        assertThat(confluencePage.getSha256ContentHash(), is("e83c661e9dc28773432ba324c7ca1551b9f5b859dcd23771de1b14272eedda06"));
    }

    @Test
    public void publish_oneNewPageWithSpaceKey_callsHttpClientToCreateNewPageUnderSpaceKey() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", httpClientMock, "", "");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock).execute(httpPostArgumentCaptor.capture());
        HttpPost httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getMethod(), is("POST"));
        assertThat(httpPost.getFirstHeader("Content-Type").getValue(), is("application/json"));
        assertThat(httpPost.getURI().toString(), is(API_ENDPOINT + "/content"));
        String expectedJson = fileContent(TEST_RESOURCES + "/some-confluence-content-space-key-only.json");
        assertThat(inputStreamAsString(httpPost.getEntity().getContent()), isSameJsonAs(expectedJson));
    }

    @Test
    public void publish_oneNewPageWithParentContentId_callsHttpClientToCreateNewPageUnderParentContentId() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", httpClientMock, "", "");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock).execute(httpPostArgumentCaptor.capture());
        HttpPost httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getMethod(), is("POST"));
        assertThat(httpPost.getFirstHeader("Content-Type").getValue(), is("application/json"));
        assertThat(httpPost.getURI().toString(), is(API_ENDPOINT + "/content"));
        String expectedJson = fileContent(TEST_RESOURCES + "/some-confluence-content-parent-content-id.json");
        assertThat(inputStreamAsString(httpPost.getEntity().getContent()), isSameJsonAs(expectedJson));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithSpaceKey_callsHttpClientMultipleTimesAndCreatePagesFromTopToBottom() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("multiple-pages-space-key", httpClientMock, "", "");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock, times(2)).execute(httpPostArgumentCaptor.capture());
        List<HttpPost> httpPostRequests = httpPostArgumentCaptor.getAllValues();
        assertThat(httpPostRequests, hasSize(2));
        HttpPost childHttpPost = httpPostRequests.get(1);
        assertThat(childHttpPost.getMethod(), is("POST"));
        assertThat(childHttpPost.getFirstHeader("Content-Type").getValue(), is("application/json"));
        assertThat(childHttpPost.getURI().toString(), is(API_ENDPOINT + "/content"));
        String expectedJson = fileContent(TEST_RESOURCES + "/some-child-content-parent-content-id.json");
        assertThat(inputStreamAsString(childHttpPost.getEntity().getContent()), isSameJsonAs(expectedJson));
    }

    @Test
    public void publish_withNoAuthentication_doesNotAddAuthenticationHeader() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", httpClientMock, "", "");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock).execute(httpPostArgumentCaptor.capture());
        HttpPost httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getFirstHeader("Authentication"), is(nullValue()));
    }

    @Test
    public void publish_withAuthenticationProvided_sendsRequestWithAuthenticationHeader() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", httpClientMock, "user", "password");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock).execute(httpPostArgumentCaptor.capture());
        HttpPost httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getFirstHeader("Authorization"), is(notNullValue()));
        assertThat(httpPost.getFirstHeader("Authorization").getValue(), is("Basic dXNlcjpwYXNzd29yZA=="));
    }

    @Test
    public void publish_metadataWithAttachments_attachesAttachmentToContent() throws Exception {
        // arrange
        ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
        ConfluencePublisher confluencePublisher = confluencePublisher("page-with-attachments", httpClientMock, "", "");

        // act
        confluencePublisher.publish();

        // assert
        verify(httpClientMock, times(3)).execute(httpPostArgumentCaptor.capture());
        List<HttpPost> httpPostRequests = httpPostArgumentCaptor.getAllValues();
        HttpPost firstAttachmentHttpPostRequest = httpPostRequests.get(1);
        assertThat(firstAttachmentHttpPostRequest.getMethod(), is("POST"));
        assertThat(firstAttachmentHttpPostRequest.getURI().toString(), is(API_ENDPOINT + "/content/123/child/attachment"));

        String partOneContent = inputStreamAsString(firstAttachmentHttpPostRequest.getEntity().getContent());
        assertThat(partOneContent, containsString("attachmentOne.txt"));
        assertThat(partOneContent, containsString("attachment1"));

        HttpPost secondAttachmentHttpPostRequest = httpPostRequests.get(2);
        assertThat(secondAttachmentHttpPostRequest.getMethod(), is("POST"));
        assertThat(secondAttachmentHttpPostRequest.getURI().toString(), is(API_ENDPOINT + "/content/123/child/attachment"));

        String partTwoContent = inputStreamAsString(secondAttachmentHttpPostRequest.getEntity().getContent());
        assertThat(partTwoContent, containsString("attachmentTwo.txt"));
        assertThat(partTwoContent, containsString("attachment2"));
    }

    @Test
    public void publish_withRequestReturn400StatusCodeWhenNewPageRequestIsSent_ThrowsRuntimeException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForErrorOnEveryRequest();
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", httpClientMock, "", "");

        // assert
        this.expectedException.expect(RuntimeException.class);
        this.expectedException.expectMessage("400");

        // act
        confluencePublisher.publish();
    }

    @Test
    public void publish_withRequestReturn400StatusCodeWhenAttachmentIsUploaded_ThrowsRuntimeException() throws Exception {
        // arrange
        CloseableHttpClient httpClientMock = recordHttpClientForErrorBeginningAtSecondRequest();
        ConfluencePublisher confluencePublisher = confluencePublisher("page-with-attachments", httpClientMock, "", "");

        // assert
        this.expectedException.expect(RuntimeException.class);
        this.expectedException.expectMessage("400");

        // act
        confluencePublisher.publish();
    }

    private static ConfluencePublisher confluencePublisher(final String qualifier, CloseableHttpClient httpClient, String username, String password) throws IOException {
        if (httpClient == null) {
            CloseableHttpClient httpClientMock = recordHttpClientForSuccess();
            return new ConfluencePublisher(API_ENDPOINT, username, password, httpClientMock, TEST_RESOURCES + "/metadata-" + qualifier + ".json");
        } else {
            return new ConfluencePublisher(API_ENDPOINT, username, password, httpClient, TEST_RESOURCES + "/metadata-" + qualifier + ".json");
        }
    }

    private static CloseableHttpClient recordHttpClientForSuccess() throws IOException {
        HttpEntity httpEntityMock = mock(HttpEntity.class);
        when(httpEntityMock.getContent()).thenAnswer(i -> new ByteArrayInputStream("{\"id\": \"123\"}".getBytes()));

        CloseableHttpResponse closeableHttpResponseMock = mock(CloseableHttpResponse.class);
        when(closeableHttpResponseMock.getEntity()).thenReturn(httpEntityMock);
        BasicStatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "");
        when(closeableHttpResponseMock.getStatusLine()).thenReturn(statusLine);

        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
        when(httpClientMock.execute(Mockito.any(HttpRequestBase.class))).thenReturn(closeableHttpResponseMock);

        return httpClientMock;
    }

    private static CloseableHttpClient recordHttpClientForErrorOnEveryRequest() throws IOException {
        CloseableHttpResponse closeableHttpResponseMock = mock(CloseableHttpResponse.class);
        BasicStatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "Bad request");
        when(closeableHttpResponseMock.getStatusLine()).thenReturn(statusLine);

        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
        when(httpClientMock.execute(Mockito.any(HttpRequestBase.class))).thenReturn(closeableHttpResponseMock);

        return httpClientMock;
    }

    private static CloseableHttpClient recordHttpClientForErrorBeginningAtSecondRequest() throws IOException {
        HttpEntity httpEntityMock = mock(HttpEntity.class);
        when(httpEntityMock.getContent()).thenAnswer(i -> new ByteArrayInputStream("{\"id\": \"123\"}".getBytes()));

        CloseableHttpResponse okResponse = mock(CloseableHttpResponse.class);
        BasicStatusLine okStatusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "");
        when(okResponse.getStatusLine()).thenReturn(okStatusLine);
        when(okResponse.getEntity()).thenReturn(httpEntityMock);

        CloseableHttpResponse badRequestResponse = mock(CloseableHttpResponse.class);
        BasicStatusLine badRequestStatusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "Bad request");
        when(badRequestResponse.getStatusLine()).thenReturn(badRequestStatusLine);

        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
        when(httpClientMock.execute(Mockito.any(HttpRequestBase.class)))
                .thenReturn(okResponse)
                .thenReturn(badRequestResponse);

        return httpClientMock;
    }

    private static String inputStreamAsString(InputStream is) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String fileContent(String filePath) throws IOException {
        return inputStreamAsString(new FileInputStream(new File(filePath)));
    }

    static class SameJsonAsMatcher extends BaseMatcher<String> {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private JsonNode expectedJson;

        private SameJsonAsMatcher(String expectedJson) {
            try {
                this.expectedJson = this.objectMapper.readTree(expectedJson);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean matches(Object actualValue) {
            if (actualValue instanceof String) {
                String actualStringValue = (String) actualValue;
                try {
                    JsonNode actualJson = this.objectMapper.readTree(actualStringValue);
                    return this.expectedJson.equals(actualJson);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(this.expectedJson);
        }

        static SameJsonAsMatcher isSameJsonAs(String expectedJson) {
            return new SameJsonAsMatcher(expectedJson);
        }

    }

}