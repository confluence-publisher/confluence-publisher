package org.sahli.confluence.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;
import org.sahli.confluence.publisher.payloads.*;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisher {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String confluenceRestApiEndpoint;
    private final String username;
    private final String password;
    private final CloseableHttpClient httpClient;
    private final File contentRoot;
    private final ConfluencePublisherMetadata metadata;

    public ConfluencePublisher(String confluenceRestApiEndpoint, String username, String password, CloseableHttpClient httpClient, String metadataFilePath) {
        this.confluenceRestApiEndpoint = confluenceRestApiEndpoint;
        this.username = username;
        this.password = password;
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
        this.publishPageTree(this.metadata.getPages(), this.metadata.getParentContentId());
    }

    private void publishPageTree(List<ConfluencePage> pages, String parentContentId) {
        pages.forEach(page -> {
            HttpPost postRequest = newPageRequest(page, parentContentId);
            CloseableHttpResponse response = sendRequest(postRequest);
            JsonNode jsonNode = toJsonNode(response);
            JsonNode id = jsonNode.get("id");
            this.publishPageTree(page.getChildren(), id.textValue());
        });
    }

    private JsonNode toJsonNode(CloseableHttpResponse response) {
        try {
            return this.objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JSON response", e);
        }
    }

    private HttpPost newPageRequest(ConfluencePage page, String parentContentId) {
        String pageContent = fileContent(new File(this.contentRoot, page.getContentFilePath()));
        NewPage newPage = newPage(page, pageContent, this.metadata.getSpaceKey(), parentContentId);

        String jsonPayload = toJsonString(newPage);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes()));

        HttpPost postRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content");
        postRequest.setEntity(entity);
        postRequest.addHeader("Content-Type", "application/json");

        return postRequest;
    }

    private CloseableHttpResponse sendRequest(HttpRequestBase httpRequest) {
        try {
            Optional<Header> authenticationHeader = authenticationHeaderIfAvailable();
            authenticationHeader.ifPresent(httpRequest::addHeader);

            return this.httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new RuntimeException("Request could not be sent" + httpRequest, e);
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
