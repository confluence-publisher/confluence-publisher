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

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sahli.confluence.publisher.converter.processor.ConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.provider.FolderBasedPagesStructureProvider;
import org.sahli.confluence.publisher.converter.model.PagesStructure;
import org.sahli.confluence.publisher.converter.PagesStructureProvider;
import org.sahli.confluence.publisher.converter.model.Page;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePageProcessor.ADOC_FILE_EXTENSION;
import static org.sahli.asciidoc.confluence.publisher.converter.Helper.SPACE_KEY;

public class FolderBasedAsciidocPagesStructureProviderTest {

    private static Page NON_EXISTING_ASCIIDOC_PAGE = mock(Page.class);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ConfluencePageProcessor processor(Path buildFolder) {
        try {
            return new AsciidocConfluencePageProcessor(buildFolder, SPACE_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void structure_nestedStructure_returnsAsciidocPagesStructureWithAllNonIncludeAdocFiles() throws IOException {
        // arrange
        Path documentationRootFolder = Paths.get("src/test/resources/folder-based-asciidoc-page-structure");
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        ConfluencePageProcessor pageProcessor = processor(buildFolder);
        Map<String, ConfluencePageProcessor> pageProcessorMap = ImmutableMap.of(ADOC_FILE_EXTENSION, pageProcessor);
        PagesStructureProvider folderBasedAsciidocSourceStructureProvider = new FolderBasedPagesStructureProvider(documentationRootFolder, pageProcessorMap);

        // act
        PagesStructure structure = folderBasedAsciidocSourceStructureProvider.structure();

        // assert
        assertThat(structure.pages().size(), is(1));

        Page indexPage = asciidocPageByPath(structure.pages(), documentationRootFolder.resolve("index.adoc"));
        assertThat(indexPage, is(not(nullValue())));
        assertThat(indexPage.children().size(), is(2));

        Page subPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one.adoc"));
        assertThat(subPageOne, is(not(nullValue())));
        assertThat(subPageOne.children().size(), is(1));

        Page subPageTwo = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-two.adoc"));
        assertThat(subPageTwo, is(not(nullValue())));
        assertThat(subPageTwo.children().size(), is(0));

        Page subSubPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one/sub-sub-page-one.adoc"));
        assertThat(subSubPageOne, is(not(nullValue())));
        assertThat(subSubPageOne.children().size(), is(0));
    }



    private Page asciidocPageByPath(List<Page> asciidocPages, Path asciidocPagePath) {
        return asciidocPages.stream()
                .filter((asciidocPage) -> asciidocPage.path().equals(asciidocPagePath))
                .findFirst()
                .orElse(NON_EXISTING_ASCIIDOC_PAGE);
    }

}
