package org.sahli.confluence.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;
import org.sahli.confluence.publisher.payloads.*;

import java.io.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisher {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String confluenceRestApiEndpoint;
    private final CloseableHttpClient httpClient;
    private final File contentRoot;
    private final ConfluencePublisherMetadata metadata;

    public ConfluencePublisher(String confluenceRestApiEndpoint, String metadataFilePath, CloseableHttpClient httpClient) {
        this.confluenceRestApiEndpoint = confluenceRestApiEndpoint;
        this.httpClient = httpClient;
        this.metadata = readConfig(metadataFilePath);
        this.contentRoot = new File(metadataFilePath).getParentFile();

        configureObjectMapper();
    }

    private ConfluencePublisherMetadata readConfig(String configPath) {
        try {
            return this.objectMapper.readValue(new File(configPath), ConfluencePublisherMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read metadata", e);
        }
    }

    private void configureObjectMapper() {
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public void publish() {
        this.metadata.getPages().forEach(page -> {
            HttpPost postRequest = newPageRequest(page);
            sendRequest(postRequest);
        });
    }

    private HttpPost newPageRequest(ConfluencePage page) {
        String pageContent = fileContent(new File(this.contentRoot, page.getContentFilePath()));
        NewPage newPage = newPage(page, pageContent, this.metadata.getSpaceKey(), this.metadata.getParentContentId());

        String jsonPayload = toJsonString(newPage);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes()));

        HttpPost postRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content");
        postRequest.setEntity(entity);
        postRequest.addHeader("Content-Type", "application/json");

        return postRequest;
    }

    private void sendRequest(HttpRequestBase httpRequest) {
        try {
            this.httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
        }
    }

    private String toJsonString(Object objectToConvert) {
        try {
            return this.objectMapper.writeValueAsString(objectToConvert);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while converting object to JSON", e);
        }
    }

    public ConfluencePublisherMetadata getMetadata() {
        return this.metadata;
    }

    private static String fileContent(File file) {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file " + file.getAbsolutePath(), e);
        }
    }

    private static NewPage newPage(ConfluencePage page, String pageContent, String spaceKey, String parentContentId) {
        Storage storage = new Storage();
        storage.setRepresentation("storage");
        storage.setValue(pageContent);

        Body body = new Body();
        body.setStorage(storage);

        Space space = new Space();
        space.setKey(spaceKey);

        NewPage newPage = new NewPage();
        newPage.setBody(body);
        newPage.setSpace(space);
        newPage.setTitle(page.getTitle());
        newPage.setType("page");

        if (isNotBlank(parentContentId)) {
            Ancestor ancestor = new Ancestor();
            ancestor.setId(parentContentId);
            newPage.addAncestor(ancestor);
        }

        return newPage;
    }

}
