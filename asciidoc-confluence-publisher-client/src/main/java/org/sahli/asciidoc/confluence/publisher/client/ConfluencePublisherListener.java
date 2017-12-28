package org.sahli.asciidoc.confluence.publisher.client;

import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;

/**
 * @author Christian Stettler
 */
public interface ConfluencePublisherListener {

    void pageAdded(ConfluencePage addedPage);

    void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage);

    void pageDeleted(ConfluencePage deletedPage);

    void publishCompleted();

}
