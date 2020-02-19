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

package org.sahli.asciidoc.confluence.publisher.converter;

import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileSystems.newFileSystem;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.list;
import static java.nio.file.Files.write;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage.newAsciidocConfluencePage;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public final class AsciidocConfluenceConverter {

    private static final String TEMPLATE_ROOT_CLASS_PATH_LOCATION = "org/sahli/asciidoc/confluence/publisher/converter/templates";

    private final String spaceKey;
    private final String ancestorId;

    public AsciidocConfluenceConverter(String spaceKey, String ancestorId) {
        this.spaceKey = spaceKey;
        this.ancestorId = ancestorId;
    }

    public ConfluencePublisherMetadata convert(AsciidocPagesStructureProvider asciidocPagesStructureProvider, Path buildFolder, Map<String, Object> userAttributes) {
        return convert(asciidocPagesStructureProvider, new NoOpPageTitlePostProcessor(), buildFolder, userAttributes);
    }

    public ConfluencePublisherMetadata convert(AsciidocPagesStructureProvider asciidocPagesStructureProvider, PageTitlePostProcessor pageTitlePostProcessor, Path buildFolder, Map<String, Object> userAttributes) {
        Path templatesRootFolder = buildFolder.resolve("templates").toAbsolutePath();
        createDirectories(templatesRootFolder);

        Path assetsRootFolder = buildFolder.resolve("assets").toAbsolutePath();
        createDirectories(assetsRootFolder);

        extractTemplatesFromClassPathTo(templatesRootFolder);

        AsciidocPagesStructureProvider.AsciidocPagesStructure structure = asciidocPagesStructureProvider.structure();
        List<AsciidocPage> asciidocPages = structure.pages();
        Charset sourceEncoding = asciidocPagesStructureProvider.sourceEncoding();
        List<ConfluencePageMetadata> confluencePages = buildPageTree(templatesRootFolder, assetsRootFolder, asciidocPages, sourceEncoding, pageTitlePostProcessor, userAttributes);

        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();
        confluencePublisherMetadata.setSpaceKey(this.spaceKey);
        confluencePublisherMetadata.setAncestorId(this.ancestorId);
        confluencePublisherMetadata.setPages(confluencePages);

        return confluencePublisherMetadata;
    }

    private static List<ConfluencePageMetadata> buildPageTree(Path templatesRootFolder, Path assetsRootFolder, List<AsciidocPage> asciidocPages, Charset sourceEncoding, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) {
        List<ConfluencePageMetadata> confluencePages = new ArrayList<>();

        asciidocPages.forEach((asciidocPage) -> {
            Path pageAssetsFolder = determinePageAssetsFolder(assetsRootFolder, asciidocPage);
            createDirectories(pageAssetsFolder);

            AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesRootFolder, pageAssetsFolder, pageTitlePostProcessor, userAttributes);
            Path contentFileTargetPath = writeToTargetStructure(asciidocPage, pageAssetsFolder, asciidocConfluencePage);

            List<AttachmentMetadata> attachments = buildAttachments(asciidocPage, pageAssetsFolder, asciidocConfluencePage.attachments());
            copyAttachmentsAvailableInSourceStructureToTargetStructure(attachments);
            ensureAttachmentsExist(attachments);

            List<ConfluencePageMetadata> childConfluencePages = buildPageTree(templatesRootFolder, assetsRootFolder, asciidocPage.children(), sourceEncoding, pageTitlePostProcessor, userAttributes);
            ConfluencePageMetadata confluencePageMetadata = buildConfluencePageMetadata(asciidocConfluencePage, contentFileTargetPath, childConfluencePages, attachments);

            confluencePages.add(confluencePageMetadata);
        });

        return confluencePages;
    }

    private static List<AttachmentMetadata> buildAttachments(AsciidocPage asciidocPage, Path pageAssetsFolder, Map<String, String> attachmentsWithRelativePath) {
        return attachmentsWithRelativePath.keySet().stream()
                .map((attachmentWithRelativePath) -> {
                    Path relativeAttachmentPath = Paths.get(attachmentWithRelativePath);
                    Path attachmentSourcePath = asciidocPage.path().getParent().resolve(relativeAttachmentPath);
                    Path attachmentTargetPath = pageAssetsFolder.resolve(relativeAttachmentPath.getFileName());

                    return new AttachmentMetadata(attachmentSourcePath, attachmentTargetPath);
                })
                .collect(toList());
    }

    private static ConfluencePageMetadata buildConfluencePageMetadata(AsciidocConfluencePage asciidocConfluencePage, Path contentFileTargetPath, List<ConfluencePageMetadata> childConfluencePages, List<AttachmentMetadata> attachments) {
        ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
        confluencePageMetadata.setTitle(asciidocConfluencePage.pageTitle());
        confluencePageMetadata.setContentFilePath(contentFileTargetPath.toAbsolutePath().toString());
        confluencePageMetadata.setChildren(childConfluencePages);
        confluencePageMetadata.getAttachments().putAll(toTargetAttachmentFileNameAndAttachmentPath(attachments));
        confluencePageMetadata.getLabels().addAll(asciidocConfluencePage.keywords());

        return confluencePageMetadata;
    }

    private static Path writeToTargetStructure(AsciidocPage asciidocPage, Path pageAssetsFolder, AsciidocConfluencePage asciidocConfluencePage) {
        try {
            Path contentFileTargetPath = determineTargetPagePath(asciidocPage, pageAssetsFolder);
            write(contentFileTargetPath, asciidocConfluencePage.content().getBytes(UTF_8));

            return contentFileTargetPath;
        } catch (IOException e) {
            throw new RuntimeException("Could not write content of page '" + asciidocPage.path().toAbsolutePath().toString() + "' to target folder", e);
        }
    }

    private static void copyAttachmentsAvailableInSourceStructureToTargetStructure(List<AttachmentMetadata> attachments) {
        attachments.forEach((attachment) -> {
            try {
                if (exists(attachment.sourcePath())) {
                    copy(attachment.sourcePath(), attachment.targetPath(), REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not copy attachment to target structure", e);
            }
        });
    }

    private static Map<String, String> toTargetAttachmentFileNameAndAttachmentPath(List<AttachmentMetadata> attachments) {
        return attachments.stream().collect(toMap(
                (attachment) -> attachment.targetPath().getFileName().toString(),
                (attachment) -> attachment.targetPath().toString()
        ));
    }

    private static Path determineTargetPagePath(AsciidocPage asciidocPage, Path pageAssetsFolder) {
        return replaceExtension(pageAssetsFolder.resolve(asciidocPage.path().getFileName()), ".adoc", ".html");
    }

    private static Path determinePageAssetsFolder(Path assetsRootFolder, AsciidocPage asciidocPage) {
        String uniquePageId = uniquePageId(asciidocPage.path());
        Path pageAssetsFolder = assetsRootFolder.resolve(uniquePageId);

        return pageAssetsFolder;
    }

    static String uniquePageId(Path asciidocPagePath) {
        return sha256Hex(asciidocPagePath.toAbsolutePath().toString());
    }

    private static Path replaceExtension(Path path, String existingExtension, String newExtension) {
        return Paths.get(path.toString().replace(existingExtension, newExtension));
    }

    private static void extractTemplatesFromClassPathTo(Path targetFolder) {
        createDirectories(targetFolder);
        withTemplates((template) -> copyTemplateTo(targetFolder, template));
    }

    private static void withTemplates(Consumer<Path> templateConsumer) {
        try {
            URI templatePathUri = resolveTemplateRootUri();

            if (templatePathUri.getScheme().startsWith("jar")) {
                withTemplatesFromJar(templatePathUri, templateConsumer);
            } else {
                withTemplatesFromFileSystem(templatePathUri, templateConsumer);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve template root folder", e);
        }
    }

    private static URI resolveTemplateRootUri() throws URISyntaxException {
        URL templateRootUrl = AsciidocConfluencePage.class.getClassLoader().getResource(TEMPLATE_ROOT_CLASS_PATH_LOCATION);

        if (templateRootUrl == null) {
            throw new RuntimeException("Could not load templates from class path '" + TEMPLATE_ROOT_CLASS_PATH_LOCATION + "'");
        }

        return templateRootUrl.toURI();
    }

    private static void withTemplatesFromFileSystem(URI templatePathUri, Consumer<Path> templateConsumer) throws IOException {
        list(Paths.get(templatePathUri)).forEach(templateConsumer);
    }

    private static void withTemplatesFromJar(URI templatePathUri, Consumer<Path> templateConsumer) throws IOException {
        URI jarFileUri = URI.create(templatePathUri.toString().substring(0, templatePathUri.toString().indexOf('!')));

        try (FileSystem jarFileSystem = newFileSystem(jarFileUri, emptyMap())) {
            Path templateRootFolder = jarFileSystem.getPath("/" + TEMPLATE_ROOT_CLASS_PATH_LOCATION);
            list(templateRootFolder).forEach(templateConsumer);
        }
    }

    private static void ensureAttachmentsExist(List<AttachmentMetadata> attachments) {
        attachments.forEach((attachment) -> ensureAttachmentExists(attachment));
    }

    private static void ensureAttachmentExists(AttachmentMetadata attachment) {
        boolean attachmentExists = exists(attachment.targetPath());

        if (!(attachmentExists)) {
            throw new RuntimeException("Attachment '" + attachment.sourcePath().getFileName() + "' does not exist");
        }
    }

    private static void createDirectories(Path templatesRootFolder) {
        try {
            Files.createDirectories(templatesRootFolder);
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory '" + templatesRootFolder.toAbsolutePath().toString() + "'", e);
        }
    }

    private static void copyTemplateTo(Path targetFolder, Path template) {
        try {
            copy(template, targetFolder.resolve(template.getFileName().toString()), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not write template to target file", e);
        }
    }


    private static class AttachmentMetadata {

        private final Path sourcePath;
        private final Path targetPath;

        AttachmentMetadata(Path sourcePath, Path targetPath) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
        }

        Path sourcePath() {
            return this.sourcePath;
        }

        Path targetPath() {
            return this.targetPath;
        }

    }

}
