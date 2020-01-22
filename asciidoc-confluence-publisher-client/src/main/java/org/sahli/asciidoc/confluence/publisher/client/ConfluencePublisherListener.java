package org.sahli.asciidoc.confluence.publisher.client;

import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;

/**
 * @author Christian Stettler
 */
public interface ConfluencePublisherListener {

    void pageAdded(ConfluencePage addedPage);

    void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage);

    void pageDeleted(ConfluencePage deletedPage);

    void attachmentAdded(String attachmentFileName, String contentId);

    void attachmentUpdated(String attachmentFileName, String contentId);

    void attachmentDeleted(String attachmentFileName, String contentId);

    void publishCompleted();

}
