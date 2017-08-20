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

import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.nio.file.FileSystems.newFileSystem;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.list;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.emptyMap;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage.newAsciidocConfluencePage;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
final class AsciidocConfluenceConverter {

    private static final String TEMPLATE_ROOT_CLASS_PATH_LOCATION = "org/sahli/asciidoc/confluence/publisher/converter/templates";

    private AsciidocConfluenceConverter() {
        throw new UnsupportedOperationException("Instantiation not supported");
    }

    static ConfluencePublisherMetadata convertAndBuildConfluencePages(String asciidocRootFolderPath, String generatedDocOutputPath, String asciidocConfluenceTemplatesPath, String spaceKey, String ancestorId) throws IOException {
        extractTemplatesFromClassPathTo(Paths.get(asciidocConfluenceTemplatesPath));

        ConfluencePublisherMetadata confluencePublisherMetadata = initializeConfluencePublisherMetadata(spaceKey, ancestorId);

        AdocFileVisitor visitor = new AdocFileVisitor(asciidocRootFolderPath, generatedDocOutputPath, asciidocConfluenceTemplatesPath);
        walkFileTree(Paths.get(asciidocRootFolderPath), visitor);

        Map<String, List<ConfluencePageMetadata>> confluencePublisherMetadataRegistry = visitor.confluencePageMetadataRegistry();

        List<ConfluencePageMetadata> rootPages = confluencePublisherMetadataRegistry.get(generatedDocOutputPath);
        if (rootPages != null) {
            confluencePublisherMetadata.getPages().addAll(rootPages);
            buildPageTree(confluencePublisherMetadata.getPages(), confluencePublisherMetadataRegistry, generatedDocOutputPath);
        }

        return confluencePublisherMetadata;
    }

    private static void buildPageTree(List<ConfluencePageMetadata> parentPages, Map<String, List<ConfluencePageMetadata>> confluencePageMetadataRegistry, String generatedDocOutputPath) {
        parentPages.forEach(page -> {
            String childPagesFolder = resolveChildPagesFolder(generatedDocOutputPath, page);
            List<ConfluencePageMetadata> childPages = confluencePageMetadataRegistry.get(childPagesFolder);

            if (childPages != null) {
                page.getChildren().addAll(childPages);
                buildPageTree(childPages, confluencePageMetadataRegistry, generatedDocOutputPath);
            }
        });
    }

    private static String resolveChildPagesFolder(String generatedDocOutputPath, ConfluencePageMetadata page) {
        return removeExtension(Paths.get(generatedDocOutputPath, page.getContentFilePath()).toFile().getAbsolutePath());
    }

    private static String removeExtension(String path) {
        return path.substring(0, path.lastIndexOf('.'));
    }

    private static ConfluencePublisherMetadata initializeConfluencePublisherMetadata(String spaceKey, String ancestorId) {
        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();

        confluencePublisherMetadata.setSpaceKey(spaceKey);
        if (ancestorId != null) {
            confluencePublisherMetadata.setAncestorId(ancestorId);
        }

        return confluencePublisherMetadata;
    }

    private static void extractTemplatesFromClassPathTo(Path targetFolder) {
        createTemplatesTargetFolder(targetFolder);
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
        list(Paths.get(templatePathUri)).forEach((template) -> templateConsumer.accept(template));
    }

    private static void withTemplatesFromJar(URI templatePathUri, Consumer<Path> templateConsumer) throws IOException {
        URI jarFileUri = URI.create(templatePathUri.toString().substring(0, templatePathUri.toString().indexOf('!')));

        try (FileSystem jarFileSystem = newFileSystem(jarFileUri, emptyMap())) {
            Path templateRootFolder = jarFileSystem.getPath("/" + TEMPLATE_ROOT_CLASS_PATH_LOCATION);
            list(templateRootFolder).forEach((template) -> templateConsumer.accept(template));
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createTemplatesTargetFolder(Path targetFolder) {
        try {
            createDirectories(targetFolder);
        } catch (IOException e) {
            throw new RuntimeException("Could not create template folder", e);
        }
    }

    private static void copyTemplateTo(Path targetFolder, Path template) {
        try {
            copy(template, targetFolder.resolve(template.getFileName().toString()), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not write template to target file", e);
        }
    }


    private static class AdocFileVisitor implements FileVisitor<Path> {

        private static final String ADOC_FILE_EXTENSION = ".adoc";
        private static final String INCLUDE_FILE_PREFIX = "_";
        private final String asciidocRootFolder;
        private final String generatedDocOutputPath;
        private final String asciidocConfluenceTemplatesPath;
        private final Map<String, List<ConfluencePageMetadata>> confluencePageMetadataRegistry = new HashMap<>();

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
                    AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(newInputStream(file), this.asciidocConfluenceTemplatesPath, imagesOutDir, file);
                    write(confluenceHtmlOutputFile.toPath(), asciidocConfluencePage.content().getBytes("UTF-8"));

                    ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
                    confluencePageMetadata.setTitle(asciidocConfluencePage.pageTitle());
                    confluencePageMetadata.setContentFilePath(Paths.get(this.generatedDocOutputPath).relativize(Paths.get(confluenceHtmlOutputFile.toURI())).toString());
                    confluencePageMetadata.getAttachments().putAll(asciidocConfluencePage.attachments());

                    this.confluencePageMetadataRegistry
                            .computeIfAbsent(confluenceHtmlOutputFile.getParent(), (key) -> new ArrayList<>())
                            .add(confluencePageMetadata);
                } else {
                    copy(file, targetFile.toPath());
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

        Map<String, List<ConfluencePageMetadata>> confluencePageMetadataRegistry() {
            return this.confluencePageMetadataRegistry;
        }
    }
}
