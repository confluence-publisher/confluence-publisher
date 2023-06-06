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

package org.sahli.confluence.publisher.converter;

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

public abstract class FolderBasedPagesStructureProvider implements PagesStructureProvider {

    private static final String INCLUDE_FILE_PREFIX = "_";

    private final PagesStructure structure;
    private final Charset sourceEncoding;

    public FolderBasedPagesStructureProvider(Path documentationRootFolder, Charset sourceEncoding) {
        this.structure = buildStructure(documentationRootFolder);
        this.sourceEncoding = sourceEncoding;
    }

    @Override
    public PagesStructure structure() {
        return this.structure;
    }

    @Override
    public Charset sourceEncoding() {
        return this.sourceEncoding;
    }

    private PagesStructure buildStructure(Path documentationRootFolder) {
        try {
            Map<Path, DefaultPage> pageIndex = indexPagesByFolderPath(documentationRootFolder);
            List<DefaultPage> allPages = connectPagesToParent(pageIndex);
            List<Page> topLevelPages = findTopLevelPages(allPages, documentationRootFolder);

            return new DefaultPagesStructure(topLevelPages);
        } catch (IOException e) {
            throw new RuntimeException("Could not create page source structure", e);
        }
    }

    @SuppressWarnings("CodeBlock2Expr")
    private List<DefaultPage> connectPagesToParent(Map<Path, DefaultPage> pageIndex) {
        pageIndex.forEach((pageFolderPath, page) -> {
            pageIndex.computeIfPresent(page.path().getParent(), (ignored, parentPage) -> {
                parentPage.addChild(page);

                return parentPage;
            });
        });

        return new ArrayList<>(pageIndex.values());
    }

    private Map<Path, DefaultPage> indexPagesByFolderPath(Path documentationRootFolder) throws IOException {
        return walk(documentationRootFolder)
                .filter((path) -> isFile(path) && !isIncludeFile(path))
                .collect(toMap((pagePath) -> removeExtension(pagePath), (pagePath) -> new DefaultPage(pagePath)));
    }

    private List<Page> findTopLevelPages(List<DefaultPage> pageByFolderPath, Path documentationRootFolder) {
        return pageByFolderPath.stream()
                .filter((page) -> page.path().equals(documentationRootFolder.resolve(page.path().getFileName())))
                .collect(toList());
    }

    private Path removeExtension(Path path) {
        return Paths.get(path.toString().substring(0, path.toString().lastIndexOf('.')));
    }

    private boolean isFile(Path file) {
        return file.toString().endsWith(getExtension());
    }

    protected abstract String getExtension();

    private static boolean isIncludeFile(Path file) {
        return file.getFileName().toString().startsWith(INCLUDE_FILE_PREFIX);
    }


    private static class DefaultPage implements Page {

        private final Path path;
        private final List<Page> children;

        DefaultPage(Path path) {
            this.path = path;
            this.children = new ArrayList<>();
        }

        void addChild(Page child) {
            this.children.add(child);
        }

        @Override
        public Path path() {
            return this.path;
        }

        @Override
        public List<Page> children() {
            return unmodifiableList(this.children);
        }

    }


    private static class DefaultPagesStructure implements PagesStructure {

        private final List<Page> pages;

        DefaultPagesStructure(List<Page> pages) {
            this.pages = pages;
        }

        @Override
        public List<Page> pages() {
            return this.pages;
        }

    }

}
