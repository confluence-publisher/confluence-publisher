/*
 * Copyright 2016-2017 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherPublishStrategy;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.lang.StringUtils.isNotBlank;
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
    private final ConfluenceClient confluenceClient;
    private final ConfluencePublisherListener confluencePublisherListener;

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, ConfluenceClient confluenceClient) {
        this(metadata, confluenceClient, new NoOpConfluencePublisherListener());
    }

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, ConfluenceClient confluenceClient, ConfluencePublisherListener confluencePublisherListener) {
        this.metadata = metadata;
        this.confluenceClient = confluenceClient;
        this.confluencePublisherListener = confluencePublisherListener;
    }

    public void publish() {
        assertMandatoryParameter(isNotBlank(this.metadata.getSpaceKey()), "spaceKey");
        assertMandatoryParameter(isNotBlank(this.metadata.getAncestorId()), "ancestorId");

        switch(this.metadata.getPublishStrategy()) {
            case APPEND_TO_ANCESTOR:
                startPublishingUnderAncestorId(this.metadata.getPages(), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                break;
            case REPLACE_ANCESTOR:
                // verify that only a single root exists
                if (this.metadata.getPages().size() > 1) {
                    throw new IllegalArgumentException(String.format("Multiple root pages detected: %s. " +
                        "Publishing to confluence with the %s strategy only allows a single root to be defined.",
                        StringUtils.join(this.metadata.getPages().stream().map(page -> "'" + page.getTitle() + "'").collect(Collectors.toList()), ", "),
                        ConfluencePublisherPublishStrategy.REPLACE_ANCESTOR.name())
                    );
                }

                if (this.metadata.getPages().size() > 0) {
                    // replace ancestor title with single root page title
                    ConfluencePageMetadata rootPageMetaData = this.metadata.getPages().get(0);
                    updatePage(this.metadata.getAncestorId(), rootPageMetaData);

                    // publish children under root page
                    startPublishingUnderAncestorId(rootPageMetaData.getChildren(), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
                }
                break;
            default:
                throw new IllegalStateException("Invalid publish strategy defined: " + this.metadata.getPublishStrategy());
        }
        this.confluencePublisherListener.publishCompleted();
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        deleteConfluencePagesNotPresentUnderAncestor(pages, ancestorId);
        pages.forEach(page -> {
            String contentId = addOrUpdatePageUnderAncestor(spaceKey, ancestorId, page);

            deleteConfluenceAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
            addAttachments(contentId, page.getAttachments());
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

    private void deleteConfluenceAttachmentsNotPresentUnderPage(String contentId, Map<String, String> attachments) {
        List<ConfluenceAttachment> confluenceAttachments = this.confluenceClient.getAttachments(contentId);

        List<String> confluenceAttachmentsToDelete = confluenceAttachments.stream()
                .filter(confluenceAttachment -> attachments.keySet().stream().noneMatch(attachmentFileName -> attachmentFileName.equals(confluenceAttachment.getTitle())))
                .map(ConfluenceAttachment::getId)
                .collect(toList());

        confluenceAttachmentsToDelete.forEach(this.confluenceClient::deleteAttachment);
    }

    private String addOrUpdatePageUnderAncestor(String spaceKey, String ancestorId, ConfluencePageMetadata page) {
        String contentId;

        try {
            contentId = this.confluenceClient.getPageByTitle(spaceKey, page.getTitle());
            updatePage(contentId, page);
        } catch (NotFoundException e) {
            String content = fileContent(page.getContentFilePath(), UTF_8);
            contentId = this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, contentHash(content));
            this.confluencePublisherListener.pageAdded(new ConfluencePage(ancestorId, contentId, page.getTitle(), content, INITIAL_PAGE_VERSION));
        }

        return contentId;
    }

    private void updatePage(String contentId, ConfluencePageMetadata page) {
        String content = fileContent(page.getContentFilePath(), UTF_8);
        ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = this.confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String newContentHash = contentHash(content);

        if (notSameContentHash(existingContentHash, newContentHash) || !existingPage.getTitle().equals(page.getTitle())) {
            this.confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            int newPageVersion = existingPage.getVersion() + 1;
            this.confluenceClient.updatePage(contentId, existingPage.getAncestorId(), page.getTitle(), content, newPageVersion);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, contentHash(content));
            this.confluencePublisherListener.pageUpdated(existingPage, new ConfluencePage(existingPage.getAncestorId(), contentId, page.getTitle(), content, newPageVersion));
        }
    }

    private static String contentHash(String content) {
        return sha256Hex(content);
    }

    private void addAttachments(String contentId, Map<String, String> attachments) {
        attachments.forEach((attachmentFileName, attachmentPath) -> addOrUpdateAttachment(contentId, attachmentPath, attachmentFileName));
    }

    private void addOrUpdateAttachment(String contentId, String attachmentPath, String attachmentFileName) {
        Path absoluteAttachmentPath = absoluteAttachmentPath(attachmentPath);

        try {
            ConfluenceAttachment existingAttachment = this.confluenceClient.getAttachmentByFileName(contentId, attachmentFileName);
            InputStream existingAttachmentContent = this.confluenceClient.getAttachmentContent(existingAttachment.getRelativeDownloadLink());

            if (!isSameContent(existingAttachmentContent, fileInputStream(absoluteAttachmentPath))) {
                this.confluenceClient.updateAttachmentContent(contentId, existingAttachment.getId(), fileInputStream(absoluteAttachmentPath));
            }

        } catch (NotFoundException e) {
            this.confluenceClient.addAttachment(contentId, attachmentFileName, fileInputStream(absoluteAttachmentPath));
        }
    }

    private Path absoluteAttachmentPath(String attachmentPath) {
        return Paths.get(attachmentPath);
    }

    private static boolean notSameContentHash(String actualContentHash, String newContentHash) {
        return actualContentHash == null || !actualContentHash.equals(newContentHash);
    }

    private static boolean isSameContent(InputStream left, InputStream right) {
        String leftHash = sha256Hash(left);
        String rightHash = sha256Hash(right);

        return leftHash.equals(rightHash);
    }

    private static String sha256Hash(InputStream content) {
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
        public void publishCompleted() {
        }

    }

}
