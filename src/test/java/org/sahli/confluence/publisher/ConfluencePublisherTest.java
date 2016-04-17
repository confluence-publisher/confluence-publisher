package org.sahli.confluence.publisher;

import org.junit.Test;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ConfluencePublisherTest {

    @Test
    public void metadata_withOnePageAndParentContentId_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-parent-content-id");

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getParentContentId(), is("72189173"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
        assertThat(confluencePage.getSha256ContentHash(), is("e2c6c0ea7c7900c31f953e48d30d5e839801ab90630d751e7c8426ed5859da47"));
    }

    @Test
    public void metadata_withOnePageSpaceKey_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key");

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getSpaceKey(), is("~personalSpace"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
        assertThat(confluencePage.getSha256ContentHash(), is("e2c6c0ea7c7900c31f953e48d30d5e839801ab90630d751e7c8426ed5859da47"));
    }

    @Test(expected = IllegalStateException.class)
    public void metadata_withSpaceKeyAndParentContentId_throwsIllegalStateException() throws Exception {
        // arrange + act + assert
        confluencePublisher("space-key-and-parent-content-id");
    }

    private static ConfluencePublisher confluencePublisher(final String qualifier) {
        return new ConfluencePublisher("src/test/resources/org/sahli/confluence/publisher/metadata-" + qualifier + ".json");
    }

}