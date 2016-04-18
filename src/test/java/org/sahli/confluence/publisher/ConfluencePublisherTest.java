package org.sahli.confluence.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sahli.confluence.publisher.ConfluencePublisherTest.SameJsonAsMatcher.isSameJsonAs;

public class ConfluencePublisherTest {

    public static final String API_ENDPOINT = "http//www.confluence.com/rest/api";
    private static final String TEST_RESOURCES = "src/test/resources/org/sahli/confluence/publisher";

    @Test
    public void metadata_withOnePageAndParentContentId_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", null);

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
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", null);

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
        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", httpClientMock);

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
        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id", httpClientMock);

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

    private static ConfluencePublisher confluencePublisher(final String qualifier, CloseableHttpClient httpClient) {
        if (httpClient == null) {
            CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);
            return new ConfluencePublisher(API_ENDPOINT, TEST_RESOURCES + "/metadata-" + qualifier + ".json", httpClientMock);
        } else {
            return new ConfluencePublisher(API_ENDPOINT, TEST_RESOURCES + "/metadata-" + qualifier + ".json", httpClient);
        }
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