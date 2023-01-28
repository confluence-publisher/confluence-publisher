/*
 * Copyright 2017 the original author or authors.
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.file.Files.walk;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class FolderBasedAsciidocPagesStructureProvider implements AsciidocPagesStructureProvider {

    private static final String ADOC_FILE_EXTENSION = ".adoc";
    private static final String INCLUDE_FILE_PREFIX = "_";
    private static final String IGNORE_DOT_FILE_SUFFIX = ".";

    private final AsciidocPagesStructure structure;
    private final Charset sourceEncoding;

    public FolderBasedAsciidocPagesStructureProvider(Path documentationRootFolder, Charset sourceEncoding) {
        this.structure = buildStructure(documentationRootFolder);
        this.sourceEncoding = sourceEncoding;
    }

    @Override
    public AsciidocPagesStructure structure() {
        return this.structure;
    }

    @Override
    public Charset sourceEncoding() {
        return this.sourceEncoding;
    }

    private AsciidocPagesStructure buildStructure(Path documentationRootFolder) {
        try {
            Map<Path, DefaultAsciidocPage> asciidocPageIndex = indexAsciidocPagesByFolderPath(documentationRootFolder);
            List<DefaultAsciidocPage> allAsciidocPages = connectAsciidocPagesToParent(asciidocPageIndex);
            List<AsciidocPage> topLevelAsciiPages = findTopLevelAsciiPages(allAsciidocPages, documentationRootFolder);

            return new DefaultAsciidocPagesStructure(topLevelAsciiPages);
        } catch (IOException e) {
            throw new RuntimeException("Could not create asciidoc source structure", e);
        }
    }

    @SuppressWarnings("CodeBlock2Expr")
    private List<DefaultAsciidocPage> connectAsciidocPagesToParent(Map<Path, DefaultAsciidocPage> asciidocPageIndex) {
        asciidocPageIndex.forEach((asciidocPageFolderPath, asciidocPage) -> {
            asciidocPageIndex.computeIfPresent(asciidocPage.path().getParent(), (ignored, parentAsciidocPage) -> {
                parentAsciidocPage.addChild(asciidocPage);

                return parentAsciidocPage;
            });
        });

        return new ArrayList<>(asciidocPageIndex.values());
    }

    private static Map<Path, DefaultAsciidocPage> indexAsciidocPagesByFolderPath(Path documentationRootFolder) throws IOException {
        return walk(documentationRootFolder)
                .filter((path) -> isAdocFile(path) && !isIncludeFile(path))
                .collect(toMap((asciidocPagePath) -> removeExtension(asciidocPagePath), (asciidocPagePath) -> new DefaultAsciidocPage(asciidocPagePath)));
    }

    private static List<AsciidocPage> findTopLevelAsciiPages(List<DefaultAsciidocPage> asciiPageByFolderPath, Path documentationRootFolder) {
        return asciiPageByFolderPath.stream()
                .filter((asciidocPage) -> asciidocPage.path().equals(documentationRootFolder.resolve(asciidocPage.path().getFileName())))
                .collect(toList());
    }

    private static Path removeExtension(Path path) {
        return Paths.get(path.toString().substring(0, path.toString().lastIndexOf('.')));
    }

    /**
     * Ensure the file ends with the '.adoc' suffix and does not start with a hidden dot '.' prefix.
     * @param file
     * @return
     */
    private static boolean isAdocFile(Path file) {
        return file.toString().endsWith(ADOC_FILE_EXTENSION)
                && !file.getFileName().toString().startsWith(IGNORE_DOT_FILE_SUFFIX);
    }

    private static boolean isIncludeFile(Path file) {
        return file.getFileName().toString().startsWith(INCLUDE_FILE_PREFIX);
    }


    private static class DefaultAsciidocPage implements AsciidocPage {

        private final Path path;
        private final List<AsciidocPage> children;

        DefaultAsciidocPage(Path path) {
            this.path = path;
            this.children = new ArrayList<>();
        }

        void addChild(AsciidocPage child) {
            this.children.add(child);
        }

        @Override
        public Path path() {
            return this.path;
        }

        @Override
        public List<AsciidocPage> children() {
            return unmodifiableList(this.children);
        }

    }


    private static class DefaultAsciidocPagesStructure implements AsciidocPagesStructure {

        private final List<AsciidocPage> asciidocPages;

        DefaultAsciidocPagesStructure(List<AsciidocPage> asciidocPages) {
            this.asciidocPages = asciidocPages;
        }

        @Override
        public List<AsciidocPage> pages() {
            return this.asciidocPages;
        }

    }

}
