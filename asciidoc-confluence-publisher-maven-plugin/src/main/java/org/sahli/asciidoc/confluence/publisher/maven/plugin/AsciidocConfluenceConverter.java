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

package org.sahli.asciidoc.confluence.publisher.maven.plugin;

import org.apache.commons.io.IOUtils;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.apache.commons.io.IOUtils.write;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage.newAsciidocConfluencePage;

/**
 * @author Alain Sahli
 */
final class AsciidocConfluenceConverter {

    private AsciidocConfluenceConverter() {
        throw new UnsupportedOperationException("Instantiation not supported");
    }

    static ConfluencePublisherMetadata convertAndBuildConfluencePages(String asciidocRootFolderPath, String generatedDocOutputPath, String asciidocConfluenceTemplatesPath, String spaceKey, String ancestorId) throws IOException {
        ConfluencePublisherMetadata confluencePublisherMetadata = initializeConfluencePublisherMetadata(spaceKey, ancestorId);

        AsciidocConfluenceConverter.AdocFileVisitor visitor = new AsciidocConfluenceConverter.AdocFileVisitor(asciidocRootFolderPath, generatedDocOutputPath,
                asciidocConfluenceTemplatesPath);
        walkFileTree(Paths.get(asciidocRootFolderPath), visitor);

        MultiValueMap<String, ConfluencePageMetadata> confluencePublisherMetadataRegistry = visitor.confluencePageMetadataRegistry();

        List<ConfluencePageMetadata> rootPages = confluencePublisherMetadataRegistry.get(generatedDocOutputPath);
        if (rootPages != null) {
            confluencePublisherMetadata.getPages().addAll(rootPages);
            buildPageTree(confluencePublisherMetadata.getPages(), confluencePublisherMetadataRegistry, generatedDocOutputPath);
        }

        return confluencePublisherMetadata;
    }

    private static void buildPageTree(List<ConfluencePageMetadata> parentPages, MultiValueMap<String, ConfluencePageMetadata> confluencePageMetadataRegistry, String generatedDocOutputPath) {
        parentPages.forEach(page -> {
            String parentFolder = removeExtension(Paths.get(generatedDocOutputPath, page.getContentFilePath()).toFile().getAbsolutePath());
            List<ConfluencePageMetadata> childPages = confluencePageMetadataRegistry.get(parentFolder);

            if (childPages != null) {
                page.getChildren().addAll(childPages);
                buildPageTree(childPages, confluencePageMetadataRegistry, generatedDocOutputPath);
            }
        });
    }

    private static ConfluencePublisherMetadata initializeConfluencePublisherMetadata(String spaceKey, String ancestorId) {
        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();

        confluencePublisherMetadata.setSpaceKey(spaceKey);
        if (ancestorId != null) {
            confluencePublisherMetadata.setAncestorId(ancestorId);
        }

        return confluencePublisherMetadata;
    }


    private static class AdocFileVisitor implements FileVisitor<Path> {

        private static final String ADOC_FILE_EXTENSION = ".adoc";
        private static final String INCLUDE_FILE_PREFIX = "_";
        private final String asciidocRootFolder;
        private final String generatedDocOutputPath;
        private final String asciidocConfluenceTemplatesPath;
        private final MultiValueMap<String, ConfluencePageMetadata> confluencePageMetadataRegistry = new LinkedMultiValueMap<>();

        private AdocFileVisitor(String asciidocRootFolder, String generatedDocOutputPath, String asciidocConfluenceTemplatesPath) {
            this.asciidocRootFolder = asciidocRootFolder;
            this.generatedDocOutputPath = generatedDocOutputPath;
            this.asciidocConfluenceTemplatesPath = asciidocConfluenceTemplatesPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return CONTINUE;
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String absolutePath = file.toFile().getAbsolutePath();
            String relativePath = absolutePath.substring(this.asciidocRootFolder.length() + 1);
            File targetFile = new File(this.generatedDocOutputPath, relativePath);
            String imagesOutDir = targetFile.getParent();

            createMissingDirectories(targetFile);

            if (!isIncludeFile(file)) {
                if (isAdocFile(file)) {
                    File confluenceHtmlOutputFile = replaceFileExtension(targetFile, "html");
                    confluenceHtmlOutputFile.createNewFile();
                    String adocContent = readAllLines(file, UTF_8).stream().collect(joining(lineSeparator()));
                    AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(adocContent, this.asciidocConfluenceTemplatesPath, imagesOutDir, file);
                    write(asciidocConfluencePage.content(), new FileOutputStream(confluenceHtmlOutputFile), "UTF-8");

                    ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
                    confluencePageMetadata.setTitle(asciidocConfluencePage.pageTitle());
                    confluencePageMetadata.setContentFilePath(Paths.get(this.generatedDocOutputPath).relativize(Paths.get(confluenceHtmlOutputFile.toURI())).toString());
                    confluencePageMetadata.getAttachments().putAll(asciidocConfluencePage.attachments());

                    this.confluencePageMetadataRegistry.add(confluenceHtmlOutputFile.getParent(), confluencePageMetadata);
                } else {
                    targetFile.createNewFile();
                    IOUtils.copy(Files.newInputStream(file), new FileOutputStream(targetFile));
                }
            }

            return CONTINUE;
        }

        private boolean isAdocFile(Path file) {
            return file.toString().endsWith(ADOC_FILE_EXTENSION);
        }

        private boolean isIncludeFile(Path file) {
            return file.getFileName().toString().startsWith(INCLUDE_FILE_PREFIX);
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private void createMissingDirectories(File file) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
        }

        private File replaceFileExtension(File file, String fileExtension) {
            return new File(file.getParent(), file.getName().substring(0, file.getName().lastIndexOf(".") + 1) + fileExtension);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return CONTINUE;
        }

        MultiValueMap<String, ConfluencePageMetadata> confluencePageMetadataRegistry() {
            return this.confluencePageMetadataRegistry;
        }
    }
}
