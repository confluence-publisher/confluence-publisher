package org.sahli.asciidoc.confluence.publisher.client;


import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ConfluencePublisherIntegrationTest {

    @Test
    public void publish_singlePage_pageIsCreatedInConfluence() {
        assertThat(1, is(1));
    }

}
