/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.client;

import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.client.utils.HashUtils;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.REPLACE_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisher {

    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    static final int INITIAL_PAGE_VERSION = 1;

    private final ConfluencePublisherMetadata metadata;
    private final PublishingStrategy publishingStrategy;
    private final ConfluenceClient confluenceClient;
    private final ConfluenceAttachmentsHandler attachmentsHandler;
    private final ConfluencePublisherListener confluencePublisherListener;
    private final String versionMessage;

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, PublishingStrategy publishingStrategy,
                               ConfluenceAttachmentsHandler attachmentsHandler,
                               ConfluenceClient confluenceClient) {
        this(metadata, publishingStrategy, attachmentsHandler, confluenceClient, new NoOpConfluencePublisherListener(), null);
    }

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, PublishingStrategy publishingStrategy,
                               ConfluenceAttachmentsHandler attachmentsHandler,
                               ConfluenceClient confluenceClient, ConfluencePublisherListener confluencePublisherListener,
                               String versionMessage) {
        this.metadata = metadata;
        this.publishingStrategy = publishingStrategy;
        this.confluenceClient = confluenceClient;
        this.attachmentsHandler = attachmentsHandler;
        this.confluencePublisherListener = confluencePublisherListener;
        this.versionMessage = versionMessage;
    }

    public void publish() {
        assertMandatoryParameter(isNotBlank(this.metadata.getSpaceKey()), "spaceKey");
        assertMandatoryParameter(isNotBlank(this.metadata.getAncestorId()), "ancestorId");

        switch (this.publishingStrategy) {
            case APPEND_TO_ANCESTOR:
                startPublishingUnderAncestorId(this.metadata.getPages(), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                break;
            case REPLACE_ANCESTOR:
                startPublishingReplacingAncestorId(singleRootPage(this.metadata), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                break;
            default:
                throw new IllegalArgumentException("Invalid publishing strategy '" + this.publishingStrategy + "'");
        }

        this.confluencePublisherListener.publishCompleted();
    }

    private static ConfluencePageMetadata singleRootPage(ConfluencePublisherMetadata metadata) {
        List<ConfluencePageMetadata> rootPages = metadata.getPages();

        if (rootPages.size() > 1) {
            String rootPageTitles = rootPages.stream()
                    .map(page -> "'" + page.getTitle() + "'")
                    .collect(joining(", "));

            throw new IllegalArgumentException("Multiple root pages detected: " + rootPageTitles + ", but '" + REPLACE_ANCESTOR + "' publishing strategy only supports one single root page");
        }

        if (rootPages.size() == 1) {
            return rootPages.get(0);
        }

        return null;
    }

    private void startPublishingReplacingAncestorId(ConfluencePageMetadata rootPage, String spaceKey, String ancestorId) {
        if (rootPage != null) {
            updatePage(ancestorId, null, rootPage);

            attachmentsHandler.deleteAttachmentsNotPresentUnderPage(ancestorId, rootPage.getAttachments());
            attachmentsHandler.addAttachments(ancestorId, rootPage.getAttachments());

            startPublishingUnderAncestorId(rootPage.getChildren(), spaceKey, ancestorId);
        }
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        deleteConfluencePagesNotPresentUnderAncestor(pages, ancestorId);
        pages.forEach(page -> {
            String contentId = addOrUpdatePageUnderAncestor(spaceKey, ancestorId, page);

            attachmentsHandler.deleteAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
            attachmentsHandler.addAttachments(contentId, page.getAttachments());

            startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
        });
    }

    private void deleteConfluencePagesNotPresentUnderAncestor(List<ConfluencePageMetadata> pagesToKeep, String ancestorId) {
        List<ConfluencePage> childPagesOnConfluence = this.confluenceClient.getChildPages(ancestorId);

        List<ConfluencePage> childPagesOnConfluenceToDelete = childPagesOnConfluence.stream()
                .filter(childPageOnConfluence -> pagesToKeep.stream().noneMatch(page -> page.getTitle().equals(childPageOnConfluence.getTitle())))
                .collect(toList());

        childPagesOnConfluenceToDelete.forEach(pageToDelete -> {
            List<ConfluencePage> pageScheduledForDeletionChildPagesOnConfluence = this.confluenceClient.getChildPages(pageToDelete.getContentId());
            pageScheduledForDeletionChildPagesOnConfluence.forEach(parentPageToDelete -> this.deleteConfluencePagesNotPresentUnderAncestor(emptyList(), pageToDelete.getContentId()));
            this.confluenceClient.deletePage(pageToDelete.getContentId());
            this.confluencePublisherListener.pageDeleted(pageToDelete);
        });
    }


    private String addOrUpdatePageUnderAncestor(String spaceKey, String ancestorId, ConfluencePageMetadata page) {
        String contentId;

        try {
            contentId = this.confluenceClient.getPageByTitle(spaceKey, page.getTitle());
            updatePage(contentId, ancestorId, page);
        } catch (NotFoundException e) {
            String content = fileContent(page.getContentFilePath(), UTF_8);
            contentId = this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content, this.versionMessage);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, HashUtils.contentHash(content));
            this.confluencePublisherListener.pageAdded(new ConfluencePage(contentId, page.getTitle(), content, INITIAL_PAGE_VERSION));
        }

        return contentId;
    }

    private void updatePage(String contentId, String ancestorId, ConfluencePageMetadata page) {
        String content = fileContent(page.getContentFilePath(), UTF_8);
        ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = this.confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String newContentHash = HashUtils.contentHash(content);

        if (HashUtils.notSameHash(existingContentHash, newContentHash) || !existingPage.getTitle().equals(page.getTitle())) {
            this.confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            int newPageVersion = existingPage.getVersion() + 1;
            this.confluenceClient.updatePage(contentId, ancestorId, page.getTitle(), content, newPageVersion, this.versionMessage);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, newContentHash);
            this.confluencePublisherListener.pageUpdated(existingPage, new ConfluencePage(contentId, page.getTitle(), content, newPageVersion));
        }
    }

    private static class NoOpConfluencePublisherListener implements ConfluencePublisherListener {

        @Override
        public void pageAdded(ConfluencePage addedPage) {
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
        }

        @Override
        public void publishCompleted() {
        }

    }

}
