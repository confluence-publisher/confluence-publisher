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

package org.sahli.confluence.publisher.converter.provider;

import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.confluence.publisher.converter.PagesStructureProvider;
import org.sahli.confluence.publisher.converter.model.DefaultPage;
import org.sahli.confluence.publisher.converter.model.DefaultPagesStructure;
import org.sahli.confluence.publisher.converter.model.Page;
import org.sahli.confluence.publisher.converter.model.PagesStructure;
import org.sahli.confluence.publisher.converter.processor.ConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.processor.PreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.walk;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sahli.confluence.publisher.converter.IoUtils.removeExtension;

public class FolderBasedPagesStructureProvider extends FileTypeProvider implements PagesStructureProvider {

    private final static Logger LOGGER = LoggerFactory.getLogger(FolderBasedPagesStructureProvider.class);

    private static final String INCLUDE_FILE_PREFIX = "_";

    private final PagesStructure structure;
    private final Map<String, ConfluencePageProcessor> pageProcessors;
    private final Map<String, PreProcessor> preProcessors;

    private final Set<String> supportedExtensions = new HashSet<>();

    private int pageCount = 0;


    public FolderBasedPagesStructureProvider(Path documentationRootFolder, Map<String, ConfluencePageProcessor> pageProcessors) {
        this.pageProcessors = pageProcessors;
        this.preProcessors = new HashMap<>();
        this.supportedExtensions.addAll(pageProcessors.keySet());
        this.structure = buildStructure(documentationRootFolder);
    }

    public FolderBasedPagesStructureProvider(Path documentationRootFolder, Map<String, ConfluencePageProcessor> pageProcessors, Map<String, PreProcessor> preProcessors) {
        this.pageProcessors = pageProcessors;
        this.preProcessors = preProcessors;
        this.supportedExtensions.addAll(pageProcessors.keySet());
        this.supportedExtensions.addAll(preProcessors.keySet());
        this.structure = buildStructure(documentationRootFolder);
    }

    @Override
    public PagesStructure structure() {
        return this.structure;
    }

    @Override
    protected Set<String> supportedExtensions() {
        return this.supportedExtensions;
    }


    private PagesStructure buildStructure(Path documentationRootFolder) {
        try {
            Map<Path, DefaultPage> pageIndex = indexPagesByFolderPath(documentationRootFolder);
            for (PreProcessor preProcessor : preProcessors.values()) {
                pageIndex = preProcessor.process(pageIndex);
            }
            List<DefaultPage> allPages = connectPagesToParent(pageIndex);
            List<Page> topLevelPages = findTopLevelPages(allPages, documentationRootFolder);

            DefaultPagesStructure pagesStructure = new DefaultPagesStructure(topLevelPages);
            return pagesStructure;
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

    private static boolean isIncludeFile(Path file) {
        return file.getFileName().toString().startsWith(INCLUDE_FILE_PREFIX);
    }

    public List<ConfluencePageMetadata> buildPageTree(List<Page> pages) {
        List<ConfluencePageMetadata> confluencePages = new ArrayList<>();

        pages.forEach((page) -> {
            try {
                pageCount++;
                ConfluencePageMetadata confluencePageMetadata = pageProcessors.get(page.extension()).process(page);
                confluencePageMetadata.setChildren(buildPageTree(page.children()));
                confluencePages.add(confluencePageMetadata);
            } catch (RuntimeException e){
                LOGGER.warn(e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });


        return confluencePages;
    }

    public List<ConfluencePageMetadata> buildPageTree() {
        List<ConfluencePageMetadata> confluencePageMetadata = buildPageTree(structure().pages());
        LOGGER.info("Page Count : " + pageCount);
        return confluencePageMetadata;
    }
}
