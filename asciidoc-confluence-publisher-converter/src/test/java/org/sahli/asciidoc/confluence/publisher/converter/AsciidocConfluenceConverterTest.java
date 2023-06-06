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

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.confluence.publisher.converter.PagesStructureProvider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter.uniquePageId;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluenceConverterTest {

    private static final String DOCUMENTATION_LOCATION = "src/test/resources/org/sahli/asciidoc/confluence/publisher/converter/doc";

    @ClassRule
    public static final GraphvizInstallationCheck GRAPHVIZ_INSTALLATION_CHECK = new GraphvizInstallationCheck();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void convertAndBuildConfluencePages_withThreeLevelAdocStructure_convertsTemplatesAndReturnsMetadata() throws Exception {
        // arrange
        Path documentationRootFolder = Paths.get(DOCUMENTATION_LOCATION).toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("name", "Rick and Morty");
        userAttributes.put("genre", "science fiction");

        PagesStructureProvider pagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);

        // act
        AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter("~personalSpace", "1234");
        ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(pagesStructureProvider, buildFolder, userAttributes);

        // assert
        assertThat(confluencePublisherMetadata.getSpaceKey(), is("~personalSpace"));
        assertThat(confluencePublisherMetadata.getAncestorId(), is("1234"));
        assertThat(confluencePublisherMetadata.getPages().size(), is(1));

        ConfluencePageMetadata indexPageMetadata = confluencePublisherMetadata.getPages().get(0);
        assertThat(indexPageMetadata.getTitle(), is("Test Document"));
        assertThat(indexPageMetadata.getAttachments().size(), is(0));
        assertThat(indexPageMetadata.getChildren().size(), is(1));
        assertThat(indexPageMetadata.getLabels().size(), is(0));

        ConfluencePageMetadata subPageMetadata = indexPageMetadata.getChildren().get(0);
        assertThat(subPageMetadata.getTitle(), is("Sub Page"));
        assertThat(subPageMetadata.getAttachments().size(), is(2));
        assertThat(subPageMetadata.getChildren().size(), is(1));
        assertThat(subPageMetadata.getLabels().size(), is(0));

        ConfluencePageMetadata subSubPageMetadata = subPageMetadata.getChildren().get(0);
        assertThat(subSubPageMetadata.getTitle(), is("Sub Sub Page"));
        assertThat(subSubPageMetadata.getAttachments().size(), is(0));
        assertThat(subSubPageMetadata.getLabels().size(), is(1));

        assertContentFilePath(indexPageMetadata, targetFilePath(buildFolder, documentationRootFolder, "index.adoc", "index.html"));
        assertContentFilePath(subPageMetadata, targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "sub-page.html"));
        assertContentFilePath(subSubPageMetadata, targetFilePath(buildFolder, documentationRootFolder, "index/sub-page/sub-sub-page.adoc", "sub-sub-page.html"));

        assertAttachmentFilePath(subPageMetadata, "attachmentOne.txt", targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "attachmentOne.txt"));
        assertAttachmentFilePath(subPageMetadata, "embedded-diagram.png", targetFilePath(buildFolder, documentationRootFolder, "index/sub-page.adoc", "embedded-diagram.png"));
    }

    @Test
    public void convertAndBuildConfluencePages_withPageReferencingNonExistingAttachment_throwsException() throws Exception {
        // arrange
        Path documentationRootFolder = Paths.get("src/test/resources/non-existing-attachment").toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        PagesStructureProvider pagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);

        // assert
        this.expectedException.expect(RuntimeException.class);
        this.expectedException.expectMessage("Attachment 'non-existing-attachment.png' does not exist");

        // act
        AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter("~personalSpace", "1234");
        asciidocConfluenceConverter.convert(pagesStructureProvider, buildFolder, emptyMap());
    }

    @Test
    public void convertAndBuildConfluencePages_withPageTitlePostProcessor_convertsTemplatesAndReturnsMetadata() throws Exception {
        // arrange
        Path documentationRootFolder = Paths.get(DOCUMENTATION_LOCATION).toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        PagesStructureProvider pagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);
        PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor("(Doc) ", " (1.0)");

        // act
        AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter("~personalSpace", "1234");
        ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(pagesStructureProvider, pageTitlePostProcessor, buildFolder, emptyMap());

        // assert
        assertThat(confluencePublisherMetadata.getSpaceKey(), is("~personalSpace"));
        assertThat(confluencePublisherMetadata.getAncestorId(), is("1234"));
        assertThat(confluencePublisherMetadata.getPages().size(), is(1));

        ConfluencePageMetadata indexPageMetadata = confluencePublisherMetadata.getPages().get(0);
        assertThat(indexPageMetadata.getTitle(), is("(Doc) Test Document (1.0)"));
    }

    @Test
    public void convertAndBuildConfluencePages_withTemplates_extractsTemplatesFromClassPathToTargetFolder() throws Exception {
        // arrange
        Path documentationRootFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();

        PagesStructureProvider pagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, UTF_8);
        AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter("~personalSpace", "1234");

        // act
        asciidocConfluenceConverter.convert(pagesStructureProvider, buildFolder, emptyMap());

        // assert
        assertThat(exists(buildFolder.resolve("templates").resolve("helpers.rb")), is(true));
    }

    private static String targetFilePath(Path buildFolder, Path documentationRootFolder, String relevantAdocFilePath, String targetFileName) {
        Path sourceFilePath = documentationRootFolder.resolve(relevantAdocFilePath);
        Path targetFilePath = buildFolder.resolve("assets").resolve(uniquePageId(sourceFilePath)).resolve(targetFileName);

        return targetFilePath.toAbsolutePath().toString();
    }

    private void assertContentFilePath(ConfluencePageMetadata confluencePageMetadata, String targetFilePath) {
        assertThat(confluencePageMetadata.getContentFilePath(), is(targetFilePath));
        assertThat(exists(Paths.get(confluencePageMetadata.getContentFilePath())), is(true));
    }

    private void assertAttachmentFilePath(ConfluencePageMetadata confluencePageMetadata, String attachmentFileName, String targetFilePath) {
        assertThat(confluencePageMetadata.getAttachments().get(attachmentFileName), is(targetFilePath));
        assertThat(exists(Paths.get(confluencePageMetadata.getAttachments().get(attachmentFileName))), is(true));
    }

}
