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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.exists;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter.convertAndBuildConfluencePages;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter.uniquePageId;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluenceConverterTest {

    private static final String DOCUMENTATION_LOCATION = "src/test/resources/org/sahli/asciidoc/confluence/publisher/converter/doc";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void convertAndBuildConfluencePages_withThreeLevelAdocStructure_convertsTemplatesAndReturnsMetadata() throws Exception {
        // arrange
        Path documentationRootFolder = Paths.get(DOCUMENTATION_LOCATION).toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder);

        // act
        convertAndBuildConfluencePages("~personalSpace", "1234", buildFolder, asciidocPagesStructureProvider);

        // assert
        assertThat("index.html", exists(targetFilePath(buildFolder, documentationRootFolder, "index.adoc", "index.html")), is(true));
        assertThat("index/sub-page.html", exists(targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "sub-page.html")), is(true));
        assertThat("index/attachmentOne.txt", exists(targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "attachmentOne.txt")), is(true));
        assertThat("index/embedded-diagram.png", exists(targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "embedded-diagram.png")), is(true));
        assertThat("index/sub-page/sub-sub-page.html", exists(targetFilePath(buildFolder, documentationRootFolder, "index/sub-page/sub-sub-page.adoc", "sub-sub-page.html")), is(true));
    }

    @Test
    public void convertAndBuildConfluencePages_withTemplates_extractsTemplatesFromClassPathToTargetFolder() throws Exception {
        // arrange
        Path documentationRootFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder);

        // act
        convertAndBuildConfluencePages("~personalSpace", "1234", buildFolder, asciidocPagesStructureProvider);

        // assert
        assertThat(exists(buildFolder.resolve("templates").resolve("helpers.rb")), is(true));
    }

    private static Path targetFilePath(Path buildFolder, Path documentationRootFolder, String relevantAdocFilePath, String targetFileName) {
        Path sourceFilePath = documentationRootFolder.resolve(relevantAdocFilePath);
        Path targetFilePath = buildFolder.resolve("assets").resolve(uniquePageId(sourceFilePath)).resolve(targetFileName);

        return targetFilePath;
    }

}
