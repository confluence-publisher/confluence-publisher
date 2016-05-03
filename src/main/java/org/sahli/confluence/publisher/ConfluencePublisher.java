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

package org.sahli.confluence.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.sahli.confluence.publisher.http.ConfluenceAttachment;
import org.sahli.confluence.publisher.http.ConfluenceClient;
import org.sahli.confluence.publisher.http.ConfluencePage;
import org.sahli.confluence.publisher.http.NotFoundException;
import org.sahli.confluence.publisher.metadata.ConfluencePageMetadata;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.jsoup.Jsoup.parse;
import static org.jsoup.parser.Parser.xmlParser;
import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisher {

    private final ConfluencePublisherMetadata metadata;
    private final ConfluenceClient confluenceClient;
    private final String contentRoot;

    public ConfluencePublisher(String metadataFilePath, ConfluenceClient confluenceClient) {
        this.metadata = readConfig(metadataFilePath);
        this.contentRoot = new File(metadataFilePath).getParentFile().getAbsoluteFile().toString();
        this.confluenceClient = confluenceClient;
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
            String contentId = addOrUpdatePage(spaceKey, page, content,
                    () -> this.confluenceClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content));

            deleteConfluenceAttachmentsNotPresentUnderPage(contentId, page.getAttachments());
            addAttachments(contentId, page.getAttachments());
            startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
        });
    }

    private void deleteConfluencePagesNotPresentUnderAncestor(List<ConfluencePageMetadata> pages, String ancestorId) {
        List<ConfluencePage> childPagesOnConfluence = this.confluenceClient.getChildPages(ancestorId);

        List<String> childPagesOnConfluenceToDelete = childPagesOnConfluence.stream()
                .filter(childPageOnConfluence -> !pages.stream().anyMatch(page -> page.getTitle().equals(childPageOnConfluence.getTitle())))
                .map(ConfluencePage::getContentId)
                .collect(toList());

        childPagesOnConfluenceToDelete.forEach(this.confluenceClient::deletePage);
    }

    private void deleteConfluenceAttachmentsNotPresentUnderPage(String contentId, List<String> attachments) {
        List<ConfluenceAttachment> confluenceAttachments = this.confluenceClient.getAttachments(contentId);

        List<String> confluenceAttachmentsToDelete = confluenceAttachments.stream()
                .filter(confluenceAttachment -> !attachments.stream().anyMatch(attachment -> attachment.equals(confluenceAttachment.getTitle())))
                .map(ConfluenceAttachment::getId)
                .collect(toList());

        confluenceAttachmentsToDelete.forEach(this.confluenceClient::deleteAttachment);
    }


    private String addOrUpdatePage(String spaceKey, ConfluencePageMetadata page, String content, Supplier<String> addPage) {
        String contentId;
        try {
            contentId = this.confluenceClient.getPageByTitle(spaceKey, page.getTitle());
            ConfluencePage existingPage = this.confluenceClient.getPageWithContentAndVersionById(contentId);

            if (notSameHtmlContent(content, existingPage.getContent())) {
                this.confluenceClient.updatePage(contentId, page.getTitle(), content, existingPage.getVersion() + 1);
            }
        } catch (NotFoundException e) {
            contentId = addPage.get();
        }

        return contentId;
    }

    private void addAttachments(String contentId, List<String> attachments) {
        attachments.forEach(attachment -> addOrUpdateAttachment(contentId, attachment));
    }

    private void addOrUpdateAttachment(String contentId, String attachment) {
        try {
            ConfluenceAttachment existingAttachment = this.confluenceClient.getAttachmentByFileName(contentId, attachment);
            InputStream existingAttachmentContent = this.confluenceClient.getAttachmentContent(existingAttachment.getRelativeDownloadLink());

            if (!isSameContent(existingAttachmentContent, fileInputStream(Paths.get(this.contentRoot, attachment).toString()))) {
                this.confluenceClient.updateAttachmentContent(contentId, existingAttachment.getId(), fileInputStream(Paths.get(this.contentRoot, attachment).toString()));
            }

        } catch (NotFoundException e) {
            this.confluenceClient.addAttachment(contentId, attachment, fileInputStream(Paths.get(this.contentRoot, attachment).toString()));
        }
    }

    private static boolean notSameHtmlContent(String htmlContent1, String htmlContent2) {
        Document document1 = parse(htmlContent1.trim(), "UTF-8", xmlParser());
        Document document2 = parse(htmlContent2.trim(), "UTF-8", xmlParser());

        return !document1.hasSameValue(document2);
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

    private static FileInputStream fileInputStream(String filePath) {
        try {
            return new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find attachment ", e);
        }
    }

}
