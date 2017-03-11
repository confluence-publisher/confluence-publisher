package org.sahli.asciidoc.confluence.publisher.client;


import io.restassured.specification.RequestSpecification;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.hasItem;

public class ConfluencePublisherIntegrationTest {

    private static final String ANCESTOR_ID = "327706";

    @Test
    public void publish_singlePage_pageIsCreatedInConfluence() {
        // arrange
        String title = "Single Page - " + randomUUID().toString();

        ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
        confluencePageMetadata.setTitle(title);
        confluencePageMetadata.setContentFilePath("single-page.xhtml");

        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();
        confluencePublisherMetadata.setSpaceKey("CPI");
        confluencePublisherMetadata.setAncestorId(ANCESTOR_ID);
        confluencePublisherMetadata.setPages(singletonList(confluencePageMetadata));

        ConfluencePublisher confluencePublisher = new ConfluencePublisher(
                confluencePublisherMetadata,
                confluenceRestClient(),
                "src/it/resources/single-page"
        );

        // act
        confluencePublisher.publish();

        // assert
        givenAuthenticatedAsPublisher()
                .when().get("http://localhost:8090/rest/api/content/" + ANCESTOR_ID + "/child/page")
                .then().body("results.title", hasItem(title));
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

    private static ConfluenceRestClient confluenceRestClient() {
        return new ConfluenceRestClient("http://localhost:8090", httpClient(), "confluence-publisher-it", "1234");
    }

    private static CloseableHttpClient httpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(20 * 1000)
                .setConnectTimeout(20 * 1000)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

}
