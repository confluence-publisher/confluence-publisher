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

import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy.REMOVE_ORPHANS;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.REPLACE_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisher {

    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    static final String ATTACHMENT_HASH_SUFFIX = "-attachment-hash";
    static final int INITIAL_PAGE_VERSION = 1;

    private final ConfluencePublisherMetadata metadata;
    private final PublishingStrategy publishingStrategy;
    private final OrphanRemovalStrategy orphanRemovalStrategy;
    private final ConfluenceClient confluenceClient;
    private final ConfluencePublisherListener confluencePublisherListener;
    private final String versionMessage;
    private final boolean notifyWatchers;

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, PublishingStrategy publishingStrategy, OrphanRemovalStrategy orphanRemovalStrategy,
                               ConfluenceClient confluenceClient, ConfluencePublisherListener confluencePublisherListener,
                               String versionMessage, boolean notifyWatchers) {
        this.metadata = metadata;
        this.publishingStrategy = publishingStrategy;
        this.orphanRemovalStrategy = orphanRemovalStrategy;
        this.confluenceClient = confluenceClient;
        this.confluencePublisherListener = confluencePublisherListener != null ? confluencePublisherListener : new NoOpConfluencePublisherListener();
        this.versionMessage = versionMessage;
        this.notifyWatchers = notifyWatchers;
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

            throw new IllegalArgumentException("Multiple root pages found (" + rootPageTitles + "), but '" + REPLACE_ANCESTOR + "' publishing strategy only supports one single root page");
        }

        if (rootPages.isEmpty()) {
            throw new IllegalArgumentException("No root page found, but '" + REPLACE_ANCESTOR + "' publishing strategy requires one single root page");
        }

        return rootPages.get(0);
    }

    private void startPublishingReplacingAncestorId(ConfluencePageMetadata rootPage, String spaceKey, String ancestorId) {
        if (rootPage != null) {
            updatePage(ancestorId, null, rootPage);

            addOrUpdateLabels(ancestorId, rootPage.getLabels());

            deleteConfluenceAttachmentsNotPresentUnderPage(ancestorId, rootPage.getAttachments());
            addAttachments(ancestorId, rootPage.getAttachments());

            startPublishingUnderAncestorId(rootPage.getChildren(), spaceKey, ancestorId);
        }
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        if (this.orphanRemovalStrategy == REMOVE_ORPHANS) {
            deleteConfluencePagesNotPresentUnderAncestor(pages, ancestorId);
        }
        pages.forEach(page -> {
            try {
                String contentId = addOrUpdatePageUnderAncestor(spaceKey, ancestorId, page);

                addOrUpdateLabels(contentId, page.getLabels());

                deleteConfluenceAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
                addAttachments(contentId, page.getAttachments());

                startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
            } catch (Exception e) {
                throw new RuntimeException("Could not publish page '" + page.getTitle() + "'", e);
            }
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

    private void deleteConfluenceAttachmentsNotPresentUnderPage(String contentId, Map<String, String> attachments) {
        List<ConfluenceAttachment> confluenceAttachments = this.confluenceClient.getAttachments(contentId);

        confluenceAttachments.stream()
                .filter(confluenceAttachment -> attachments.keySet().stream().noneMatch(attachmentFileName -> attachmentFileName.equals(confluenceAttachment.getTitle())))
                .forEach(confluenceAttachment -> {
                    this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(confluenceAttachment.getTitle()));
                    this.confluenceClient.deleteAttachment(confluenceAttachment.getId());
                    this.confluencePublisherListener.attachmentDeleted(confluenceAttachment.getTitle(), contentId);
                });
    }

    private String addOrUpdatePageUnderAncestor(String spaceKey, String ancestorId, ConfluencePageMetadata page) {
        String contentId;

        try {
            contentId = this.confluenceClient.getPageByTitle(spaceKey, ancestorId, page.getTitle());
            updatePage(contentId, ancestorId, page);
        } catch (NotFoundException e) {
            String content = fileContent(page.getContentFilePath(), UTF_8);
            contentId = this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content, this.versionMessage);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, hash(content));
            this.confluencePublisherListener.pageAdded(new ConfluencePage(contentId, page.getTitle(), content, INITIAL_PAGE_VERSION));
        }

        return contentId;
    }

    private void updatePage(String contentId, String ancestorId, ConfluencePageMetadata page) {
        String content = fileContent(page.getContentFilePath(), UTF_8);
        ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = this.confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String newContentHash = hash(content);

        if (notSameHash(existingContentHash, newContentHash) || !existingPage.getTitle().equals(page.getTitle())) {
            this.confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            int newPageVersion = existingPage.getVersion() + 1;
            this.confluenceClient.updatePage(contentId, ancestorId, page.getTitle(), content, newPageVersion, this.versionMessage, this.notifyWatchers);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, newContentHash);
            this.confluencePublisherListener.pageUpdated(existingPage, new ConfluencePage(contentId, page.getTitle(), content, newPageVersion));
        }
    }

    private void addAttachments(String contentId, Map<String, String> attachments) {
        attachments.forEach((attachmentFileName, attachmentPath) -> addOrUpdateAttachment(contentId, attachmentPath, attachmentFileName));
    }

    private void addOrUpdateAttachment(String contentId, String attachmentPath, String attachmentFileName) {
        Path absoluteAttachmentPath = absoluteAttachmentPath(attachmentPath);
        String newAttachmentHash = hash(fileInputStream(absoluteAttachmentPath));

        try {
            ConfluenceAttachment existingAttachment = this.confluenceClient.getAttachmentByFileName(contentId, attachmentFileName);
            String attachmentId = existingAttachment.getId();
            String existingAttachmentHash = this.confluenceClient.getPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));

            if (notSameHash(existingAttachmentHash, newAttachmentHash)) {
                if (existingAttachmentHash != null) {
                    this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));
                }
                this.confluenceClient.updateAttachmentContent(contentId, attachmentId, fileInputStream(absoluteAttachmentPath), this.notifyWatchers);
                this.confluenceClient.setPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName), newAttachmentHash);
                this.confluencePublisherListener.attachmentUpdated(attachmentFileName, contentId);
            }

        } catch (NotFoundException e) {
            this.confluenceClient.deletePropertyByKey(contentId, getAttachmentHashKey(attachmentFileName));
            this.confluenceClient.addAttachment(contentId, attachmentFileName, fileInputStream(absoluteAttachmentPath));
            this.confluenceClient.setPropertyByKey(contentId, getAttachmentHashKey(attachmentFileName), newAttachmentHash);
            this.confluencePublisherListener.attachmentAdded(attachmentFileName, contentId);
        }
    }

    private static String getAttachmentHashKey(String attachmentFileName) {
        return hash(attachmentFileName) + ATTACHMENT_HASH_SUFFIX;
    }

    private Path absoluteAttachmentPath(String attachmentPath) {
        return Paths.get(attachmentPath);
    }

    private void addOrUpdateLabels(String contentId, List<String> labels) {
        List<String> existingLabels = this.confluenceClient.getLabels(contentId);

        existingLabels.stream()
                .filter((existingLabel) -> !(labels.contains(existingLabel)))
                .forEach((labelToDelete) -> this.confluenceClient.deleteLabel(contentId, labelToDelete));

        List<String> labelsToAdd = labels.stream()
                .filter((label) -> !(existingLabels.contains(label)))
                .collect(toList());

        if (labelsToAdd.size() > 0) {
            this.confluenceClient.addLabels(contentId, labelsToAdd);
        }
    }

    private static boolean notSameHash(String actualHash, String newHash) {
        return actualHash == null || !actualHash.equals(newHash);
    }

    private static String hash(String content) {
        return sha256Hex(content);
    }

    private static String hash(InputStream content) {
        try {
            return sha256Hex(content);
        } catch (IOException e) {
            throw new RuntimeException("Could not compute hash from input stream", e);
        } finally {
            try {
                content.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static FileInputStream fileInputStream(Path filePath) {
        try {
            return new FileInputStream(filePath.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find attachment ", e);
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
        public void attachmentAdded(String attachmentFileName, String contentId) {
        }

        @Override
        public void attachmentUpdated(String attachmentFileName, String contentId) {
        }

        @Override
        public void attachmentDeleted(String attachmentFileName, String contentId) {
        }

        @Override
        public void publishCompleted() {
        }

    }

}
