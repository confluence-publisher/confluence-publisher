package org.sahli.confluence.publisher.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.io.InputStream;

import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluenceRestClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRequestFactory httpRequestFactory;

    public ConfluenceRestClient(String rootConfluenceUrl, HttpClient httpClient) {
        this(rootConfluenceUrl, httpClient, null, null);
    }

    public ConfluenceRestClient(String rootConfluenceUrl, HttpClient httpClient, String username, String password) {
        assertMandatoryParameter(httpClient != null, "httpClient");

        this.httpRequestFactory = new HttpRequestFactory(rootConfluenceUrl, username, password);
        this.httpClient = httpClient;

        configureObjectMapper();
    }

    private void configureObjectMapper() {
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public String addPageUnderSpace(String spaceKey, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderSpaceRequest(spaceKey, title, content);
        HttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        return extractIdFromJsonNode(parseJsonResponse(response));
    }

    public String addPageUnderAncestor(String ancestorId, String title, String content) {
        HttpPost addPageUnderSpaceRequest = this.httpRequestFactory.addPageUnderAncestorRequest(ancestorId, title, content);
        HttpResponse response = sendRequestAndFailIfNot20x(addPageUnderSpaceRequest);

        return extractIdFromJsonNode(parseJsonResponse(response));
    }

    public void updatePage(String contentId, String title, String content, int newVersion) {
        HttpPut updatePageRequest = this.httpRequestFactory.updatePageRequest(contentId, title, content, newVersion);
        sendRequestAndFailIfNot20x(updatePageRequest);
    }

    public void deletePage(String contentId) {
        HttpDelete deletePageRequest = this.httpRequestFactory.deletePageRequest(contentId);
        sendRequestAndFailIfNot20x(deletePageRequest);
    }

    public String getPageByTitle(String spaceKey, String title) throws NotFoundException, MultipleResultsException {
        HttpGet pageByTitleRequest = this.httpRequestFactory.getPageByTitleRequest(spaceKey, title);
        HttpResponse response = sendRequestAndFailIfNot20x(pageByTitleRequest);
        JsonNode jsonNode = parseJsonResponse(response);

        JsonNode result = jsonNode.withArray("results");
        int numberOfResults = result.size();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        return extractIdFromJsonNode(result.elements().next());
    }

    public void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent) {
        HttpPost addAttachmentRequest = this.httpRequestFactory.addAttachmentRequest(contentId, attachmentFileName, attachmentContent);
        sendRequestAndFailIfNot20x(addAttachmentRequest);
    }

    public void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent) {
        HttpPost updateAttachmentContentRequest = this.httpRequestFactory.updateAttachmentContentRequest(contentId, attachmentId, attachmentContent);
        sendRequestAndFailIfNot20x(updateAttachmentContentRequest);
    }

    public void deleteAttachment(String attachmentId) {
        HttpDelete deleteAttachmentRequest = this.httpRequestFactory.deleteAttachmentRequest(attachmentId);
        sendRequestAndFailIfNot20x(deleteAttachmentRequest);
    }

    public String getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException {
        HttpGet attachmentByFileNameRequest = this.httpRequestFactory.getAttachmentByFileNameRequest(contentId, attachmentFileName);
        HttpResponse response = sendRequestAndFailIfNot20x(attachmentByFileNameRequest);

        JsonNode jsonNode = parseJsonResponse(response);

        JsonNode result = jsonNode.withArray("results");
        int numberOfResults = result.size();
        if (numberOfResults == 0) {
            throw new NotFoundException();
        }

        if (numberOfResults > 1) {
            throw new MultipleResultsException();
        }

        return extractIdFromJsonNode(result.elements().next());
    }

    private static String extractIdFromJsonNode(JsonNode jsonNode) {
        return jsonNode.get("id").asText();
    }

    private JsonNode parseJsonResponse(HttpResponse response) {
        try {
            return this.objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JSON response", e);
        }
    }

    private HttpResponse sendRequestAndFailIfNot20x(HttpRequestBase httpRequest) {
        HttpResponse response;
        try {
            response = this.httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
        }

        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() > 206) {
            throw new RuntimeException("Response had not expected status code (between 200 and 206) -> "
                    + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        }

        return response;
    }

}
