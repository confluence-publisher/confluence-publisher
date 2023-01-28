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

import org.junit.Test;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class FolderBasedAsciidocPagesStructureProviderTest {

    private static AsciidocPage NON_EXISTING_ASCIIDOC_PAGE = mock(AsciidocPage.class);

    @Test
    public void structure_nestedStructure_returnsAsciidocPagesStructureWithAllNonIncludeAdocFiles() {
        // arrange
        Path documentationRootFolder = Paths.get("src/test/resources/folder-based-asciidoc-page-structure");
        FolderBasedAsciidocPagesStructureProvider folderBasedAsciidocSourceStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);

        // act
        AsciidocPagesStructureProvider.AsciidocPagesStructure structure = folderBasedAsciidocSourceStructureProvider.structure();

        // assert
        assertThat(structure.pages().size(), is(1));

        AsciidocPage indexPage = asciidocPageByPath(structure.pages(), documentationRootFolder.resolve("index.adoc"));
        assertThat(indexPage, is(not(nullValue())));
        assertThat(indexPage.children().size(), is(2));

        // .asciidoctorconfig
        AsciidocPage asciiDoctorConfig = asciidocPageByPath(structure.pages(), documentationRootFolder.resolve(".asciidoctorconfig"));
        assertThat(asciiDoctorConfig, is(not(nullValue())));
        assertThat(asciiDoctorConfig.children().size(), is(0));

        AsciidocPage subPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one.adoc"));
        assertThat(subPageOne, is(not(nullValue())));
        assertThat(subPageOne.children().size(), is(1));

        AsciidocPage subPageTwo = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-two.adoc"));
        assertThat(subPageTwo, is(not(nullValue())));
        assertThat(subPageTwo.children().size(), is(0));

        // index .asciidoctorconfig.adoc
        AsciidocPage asciiDoctorConfigAdoc = asciidocPageByPath(structure.pages(), documentationRootFolder.resolve("index/.asciidoctorconfig.adoc"));
        assertThat(asciiDoctorConfigAdoc, is(not(nullValue())));
        assertThat(asciiDoctorConfigAdoc.children().size(), is(0));

        AsciidocPage subSubPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one/sub-sub-page-one.adoc"));
        assertThat(subSubPageOne, is(not(nullValue())));
        assertThat(subSubPageOne.children().size(), is(0));
    }

    @Test
    public void sourceEncoding_sourceEncodingProvided_returnsProvidedSourceEncoding() {
        // arrange
        Path documentationRootFolder = Paths.get("src/test/resources/folder-based-asciidoc-page-structure");
        FolderBasedAsciidocPagesStructureProvider folderBasedAsciidocSourceStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);

        // act
        Charset sourceEncoding = folderBasedAsciidocSourceStructureProvider.sourceEncoding();

        // assert
        assertThat(sourceEncoding, is(UTF_8));
    }

    private AsciidocPage asciidocPageByPath(List<AsciidocPage> asciidocPages, Path asciidocPagePath) {
        return asciidocPages.stream()
                .filter((asciidocPage) -> asciidocPage.path().equals(asciidocPagePath))
                .findFirst()
                .orElse(NON_EXISTING_ASCIIDOC_PAGE);
    }

}
