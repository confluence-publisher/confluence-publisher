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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.exists;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.sahli.asciidoc.confluence.publisher.maven.plugin.AsciidocConfluenceConverter.convertAndBuildConfluencePages;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluenceConverterTest {

    private static final String CLASSPATH_DOC_LOCATION = "src/test/resources/org/sahli/asciidoc/confluence/publisher/maven/plugin/doc";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void convertAndBuildConfluencePages_withThreeLevelAdocStructure_convertsTemplatesAndReturnsMetadata() throws Exception {
        // arrange
        Path sourceDocFolder = Paths.get(CLASSPATH_DOC_LOCATION).toAbsolutePath();

        File generatedDocOutput = this.temporaryFolder.newFolder();
        String generatedDocOutputPath = generatedDocOutput.getAbsolutePath();
        String asciidocConfluenceTemplatesPath = this.temporaryFolder.newFolder().getAbsolutePath();

        // act
        convertAndBuildConfluencePages(sourceDocFolder.toString(), generatedDocOutputPath, asciidocConfluenceTemplatesPath, "~personalSpace", "1234");

        // assert
        assertThat("index.html", exists(Paths.get(generatedDocOutputPath, "index.html")), is(true));
        assertThat("index/", exists(Paths.get(generatedDocOutputPath, "index")), is(true));
        assertThat("index/sub-page.html", exists(Paths.get(generatedDocOutputPath, "index", "sub-page.html")), is(true));
        assertThat("index/sub-page/", exists(Paths.get(generatedDocOutputPath, "index", "sub-page")), is(true));
        assertThat("index/sub-page/sub-sub-page.html", exists(Paths.get(generatedDocOutputPath, "index", "sub-page", "sub-sub-page.html")), is(true));
        assertThat("index/embedded-diagram.png", exists(Paths.get(generatedDocOutputPath, "index", "embedded-diagram.png")), is(true));
    }

    @Test
    public void convertAndBuildConfluencePages_withTemplates_extractsTemplatesFromClassPathToTargetFolder() throws Exception {
        // arrange
        String sourceDocFolderPath = this.temporaryFolder.newFolder().getAbsolutePath();
        String generatedDocOutputPath = this.temporaryFolder.newFolder().getAbsolutePath();
        String asciidocConfluenceTemplatesPath = this.temporaryFolder.newFolder().getAbsolutePath();

        // act
        convertAndBuildConfluencePages(sourceDocFolderPath, generatedDocOutputPath, asciidocConfluenceTemplatesPath, "~personalSpace", "1234");

        // assert
        assertThat(exists(Paths.get(asciidocConfluenceTemplatesPath, "helpers.rb")), is(true));
    }

}
