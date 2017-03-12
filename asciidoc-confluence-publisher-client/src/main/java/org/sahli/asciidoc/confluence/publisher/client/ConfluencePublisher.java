/*
 * Copyright 2016 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 * @since 1.0
 */
public class ConfluencePublisher {

    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    private final ConfluencePublisherMetadata metadata;
    private final ConfluenceClient confluenceClient;
    private final String contentRoot;

    public ConfluencePublisher(String metadataFilePath, ConfluenceClient confluenceClient) {
        this.metadata = readConfig(metadataFilePath);
        this.contentRoot = new File(metadataFilePath).getParentFile().getAbsoluteFile().toString();
        this.confluenceClient = confluenceClient;
    }

    public ConfluencePublisher(ConfluencePublisherMetadata metadata, ConfluenceClient confluenceClient, String contentRoot) {
        this.metadata = metadata;
        this.confluenceClient = confluenceClient;
        this.contentRoot = contentRoot;
    }

    public ConfluencePublisherMetadata getMetadata() {
        return this.metadata;
    }

    public void publish() {
        assertMandatoryParameter(isNotBlank(this.getMetadata().getSpaceKey()), "spaceKey");

        String ancestorId;
        if (isNotBlank(this.metadata.getAncestorId())) {
            ancestorId = this.getMetadata().getAncestorId();
        } else {
            ancestorId = this.confluenceClient.getSpaceContentId(this.metadata.getSpaceKey());
        }

        startPublishingUnderAncestorId(this.metadata.getPages(), this.metadata.getSpaceKey(), ancestorId);
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        deleteConfluencePagesNotPresentUnderAncestor(pages, ancestorId);

        pages.forEach(page -> {
            String content = fileContent(Paths.get(this.contentRoot, page.getContentFilePath()).toString());
            String contentId = addOrUpdatePage(spaceKey, ancestorId, page, content);

            deleteConfluenceAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
            addAttachments(contentId, page.getContentFilePath(), page.getAttachments());
            startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
        });
    }

    private void deleteConfluencePagesNotPresentUnderAncestor(List<ConfluencePageMetadata> pagesToKeep, String ancestorId) {
        List<ConfluencePage> childPagesOnConfluence = this.confluenceClient.getChildPages(ancestorId);

        List<String> childPagesOnConfluenceToDelete = childPagesOnConfluence.stream()
                .filter(childPageOnConfluence -> pagesToKeep.stream().noneMatch(page -> page.getTitle().equals(childPageOnConfluence.getTitle())))
                .map(ConfluencePage::getContentId)
                .collect(toList());

        childPagesOnConfluenceToDelete.forEach(pageToDelete -> {
            List<ConfluencePage> pageScheduledForDeletionChildPagesOnConfluence = this.confluenceClient.getChildPages(pageToDelete);
            pageScheduledForDeletionChildPagesOnConfluence.forEach(parentPageToDelete -> this.deleteConfluencePagesNotPresentUnderAncestor(emptyList(), pageToDelete));
            this.confluenceClient.deletePage(pageToDelete);
        });
    }

    private void deleteConfluenceAttachmentsNotPresentUnderPage(String contentId, Map<String, String> attachments) {
        List<ConfluenceAttachment> confluenceAttachments = this.confluenceClient.getAttachments(contentId);

        List<String> confluenceAttachmentsToDelete = confluenceAttachments.stream()
                .filter(confluenceAttachment -> attachments.values().stream().noneMatch(attachmentFileName -> attachmentFileName.equals(confluenceAttachment.getTitle())))
                .map(ConfluenceAttachment::getId)
                .collect(toList());

        confluenceAttachmentsToDelete.forEach(this.confluenceClient::deleteAttachment);
    }


    private String addOrUpdatePage(String spaceKey, String ancestorId, ConfluencePageMetadata page, String content) {
        String contentId;

        try {
            contentId = this.confluenceClient.getPageByTitle(spaceKey, page.getTitle());
            ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);
            String existingContentHash = this.confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            String newContentHash = contentHash(content);

            if (notSameContentHash(existingContentHash, newContentHash)) {
                this.confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
                this.confluenceClient.updatePage(contentId, ancestorId, page.getTitle(), content, existingPage.getVersion() + 1);
                this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, newContentHash);
            }
        } catch (NotFoundException e) {
            contentId = this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content);
            this.confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, contentHash(content));
        }

        return contentId;
    }

    private static String contentHash(String content) {
        return sha256Hex(content);
    }

    private void addAttachments(String contentId, String contentFilePath, Map<String, String> attachments) {
        attachments.forEach((attachmentPath, attachmentFileName) -> addOrUpdateAttachment(contentId, contentFilePath, attachmentPath, attachmentFileName));
    }

    private void addOrUpdateAttachment(String contentId, String contentFilePath, String attachmentPath, String attachmentFileName) {
        Path absoluteAttachmentPath = absoluteAttachmentPath(contentFilePath, attachmentPath);

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

    private Path absoluteAttachmentPath(String contentFilePath, String attachmentPath) {
        if (isPageInContentRootFolder(contentFilePath)) {
            return Paths.get(this.contentRoot).resolve(attachmentPath);
        }

        return Paths.get(this.contentRoot).resolve(contentFilePath).getParent().resolve(attachmentPath);
    }

    private boolean isPageInContentRootFolder(String contentFilePath) {
        return Paths.get(contentFilePath).getParent() == null;
    }

    private static boolean notSameContentHash(String actualContentHash, String newContentHash) {
        return !actualContentHash.equals(newContentHash);
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

    private static ConfluencePublisherMetadata readConfig(String configPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        try {
            return objectMapper.readValue(new File(configPath), ConfluencePublisherMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read metadata", e);
        }
    }

    private static FileInputStream fileInputStream(Path filePath) {
        try {
            return new FileInputStream(filePath.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find attachment ", e);
        }
    }

}
