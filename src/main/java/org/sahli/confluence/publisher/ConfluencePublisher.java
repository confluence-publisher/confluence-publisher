package org.sahli.confluence.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.File;
import java.io.IOException;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisher {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConfluencePublisherMetadata metadata;

    public ConfluencePublisher(String metadataFilePath) {
        try {
            this.metadata = readConfig(metadataFilePath);
            this.metadata.validate();
        } catch (IOException e) {
            // TODO add proper logging
            e.printStackTrace();
        }
    }

    private ConfluencePublisherMetadata readConfig(String configPath) throws IOException {
        return this.objectMapper.readValue(new File(configPath), ConfluencePublisherMetadata.class);
    }

    public ConfluencePublisherMetadata getMetadata() {
        return metadata;
    }
}
