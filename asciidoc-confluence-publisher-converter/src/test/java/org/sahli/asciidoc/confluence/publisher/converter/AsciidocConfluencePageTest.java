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
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage.newAsciidocConfluencePage;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePageTest {

    private static final Path TEMPLATES_FOLDER = Paths.get("src/main/resources/org/sahli/asciidoc/confluence/publisher/converter/templates");

    @ClassRule
    public static final GraphvizInstallationCheck GRAPHVIZ_INSTALLATION_CHECK = new GraphvizInstallationCheck();

    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    @Test
    public void render_asciidocWithTopLevelHeader_returnsConfluencePageWithPageTitleFromTopLevelHeader() {
        // arrange
        String adoc = "= Page title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndDocumentAttribute_returnsConfluencePageWithPageTitleFromTopLevelHeaderAndDocumentAttributeResolved() {
        // arrange
        String adoc = ":attributeWithValue: test\n" +
                ":attributeWithoutValue:\n" +
                "= Page title ({attributeWithValue}{attributeWithoutValue}{unknownAttribute})";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (test{unknownAttribute})"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndUserAttribute_returnsConfluencePageWithPageTitleFromTopLevelHeaderAndUserAttributeResolved() {
        // arrange
        String adoc = "= Page title ({attributeWithValue}{attributeWithoutValue}{unknownAttribute})";

        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("attributeWithValue", "test");
        userAttributes.put("attributeWithoutValue", null);

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), userAttributes);

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (test{unknownAttribute})"));
    }

    @Test
    public void render_asciidocWithTitleMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() {
        // arrange
        String adoc = ":title: Page title (meta)";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (meta)"));
    }

    @Test
    public void render_asciidocWithTitleMetaInformationAndDocumentAttribute_returnsConfluencePageWithPageTitleFromTitleMetaInformationAndDocumentAttributeResolved() {
        // arrange
        String adoc = ":attributeWithValue: test\n" +
                ":attributeWithoutValue:\n" +
                ":title: Page title ({attributeWithValue}{attributeWithoutValue}{unknownAttribute})";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (test{unknownAttribute})"));
    }

    @Test
    public void render_asciidocWithTitleMetaInformationAndUserAttribute_returnsConfluencePageWithPageTitleFromTitleMetaInformationAndUserAttributeResolved() {
        // arrange
        String adoc = ":title: Page title ({attributeWithValue}{attributeWithoutValue}{unknownAttribute})";

        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("attributeWithValue", "test");
        userAttributes.put("attributeWithoutValue", null);

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), userAttributes);

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (test{unknownAttribute})"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() {
        // arrange
        String adoc = ":title: Page title (meta)\n" +
                "= Page Title (header)";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (meta)"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndMetaInformationAndPageTitlePostProcessorConfigured_returnsConfluencePageWithPostProcessedPageTitleFromTitleMetaInformation() {
        // arrange
        String adoc = ":title: Page title (meta)\n" +
                "= Page Title (header)";

        PageTitlePostProcessor pageTitlePostProcessor = mock(PageTitlePostProcessor.class);
        when(pageTitlePostProcessor.process("Page title (meta)")).thenReturn("Post-Processed Page Title");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), pageTitlePostProcessor);

        // assert
        assertThat(asciidocConfluencePage.pageTitle(), is("Post-Processed Page Title"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderWithHtmlCharacter_returnsConfluencePageWithPageTitleWithUnescapedHtmlCharacter() {
        // arrange
        String adoc = "= Page&title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), emptyMap());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page&title"));
    }

    @Test
    public void render_asciidocWithoutAttributes_returnsConfluencePageWithoutAttributes() {
        // arrange
        String adoc = prependTitle("Hello {user}");

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), emptyMap());

        // assert
        assertThat(asciiDocConfluencePage.content(), is("<p>Hello {user}</p>"));
    }

    @Test
    public void render_asciidocWithPageTitleAndPageTitlePostProcessorConfigured_returnsConfluencePageWithPostProcessedPageTitle() {
        // arrange
        String adoc = "= Page Title";
        PageTitlePostProcessor pageTitlePostProcessor = mock(PageTitlePostProcessor.class);
        when(pageTitlePostProcessor.process("Page Title")).thenReturn("Post-Processed Page Title");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), pageTitlePostProcessor);

        // assert
        assertThat(asciidocConfluencePage.pageTitle(), is("Post-Processed Page Title"));
    }

    @Test
    public void render_asciidocWithNeitherTopLevelHeaderNorTitleMetaInformation_returnsConfluencePageWithPageTitleFromMetaInformation() {
        // arrange
        String adoc = "Content";

        // act
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            newAsciidocConfluencePage(asciidocPage(adoc), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath())
        );
        assertThat(exception.getMessage(), containsString("failed to create confluence page for asciidoc content in"));
        assertThat(exception.getCause().getMessage(), equalTo("top-level heading or title meta information must be set"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithAttributes_returnsConfluencePageContentWithReplacedAttributes() {
        // arrange
        String adocContent = prependTitle("{attributeWithValue}{attributeWithoutValue}{unknownAttribute}");

        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("attributeWithValue", "test");
        userAttributes.put("attributeWithoutValue", null);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adocContent), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath(), userAttributes);

        // assert
        assertThat(asciidocConfluencePage.content(), is("<p>test{unknownAttribute}</p>"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithListing_returnsConfluencePageContentWithMacroWithNameNoFormat() {
        // arrange
        String adocContent = "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"noformat\">" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithListingAndTitle_returnsConfluencePageContentWithMacroWithNameNoFormatAndTitle() {
        // arrange
        String adocContent = ".A block title\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"noformat\">" +
                "<ac:parameter ac:name=\"title\">A block title</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListing_returnsConfluencePageContentWithMacroWithNameCode() {
        // arrange
        String adocContent = "[source]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithJavaSourceListing_returnsConfluencePageContentWithMacroWithJavaParameter() {
        // arrange
        String adocContent = "[source,java]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithLineNumbers_returnsConfluencePageContentWithMacroWithLineNumbersParameter() {
        // arrange
        String adocContent = "[source%linenums]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"linenumbers\">true</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithLineNumbersAndJavaLanguage_returnsConfluencePageContentWithMacroWithLineNumbersAndJavaParameter() {
        // arrange
        String adocContent = "[source,java,linenums]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
                "<ac:parameter ac:name=\"linenumbers\">true</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithLineNumbersAndStartIndex_returnsConfluencePageContentWithMacroWithLineNumbersAndFirstLineParameter() {
        // arrange
        String adocContent = "[source%linenums,start=3]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"linenumbers\">true</ac:parameter>" +
                "<ac:parameter ac:name=\"firstline\">3</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithGlobalLineNumbersAttribute_returnsConfluencePageContentWithMacroWithLineNumbersParameter() {
        // arrange
        String adocContent = ":source-linenums-option:\n" +
                "[source]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"linenumbers\">true</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingOfUnsupportedLanguage_returnsConfluencePageContentWithMacroWithoutLanguageElement() {
        // arrange
        String adocContent = "[source,unsupported]\n" +
                "----\n" +
                "GET /events?param1=value1&param2=value2 HTTP/1.1\n" +
                "Host: localhost:8080\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body>" +
                "<![CDATA[GET /events?param1=value1&param2=value2 HTTP/1.1\nHost: localhost:8080]]>" +
                "</ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithListingWithHtmlMarkup_returnsConfluencePageContentWithMacroWithoutHtmlEscape() {
        // arrange
        String adocContent = "----\n" +
                "<b>line one</b>\n" +
                "<b>line two</b>\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"noformat\">" +
                "<ac:plain-text-body><![CDATA[<b>line one</b>\n<b>line two</b>]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithHtmlContent_returnsConfluencePageContentWithoutHtmlEscape() {
        // arrange
        String adocContent = "[source]\n" +
                "----\n" +
                "<b>content with html</b>\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[<b>content with html</b>]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithRegularExpressionSymbols_returnsConfluencePageContentWithRegularExpressionSymbolsEscaped() {
        // arrange
        String adocContent = "[source]\n" +
                "----\n" +
                "[0-9][0-9]\\.[0-9][0-9]\\.[0-9]{4}$\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[[0-9][0-9]\\.[0-9][0-9]\\.[0-9]{4}$]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingAndSourceHighlighter_returnsConfluencePageContentWithoutSourceHighlighting() {
        // arrange
        String adocContent = ":source-highlighter: coderay\n" +
                "[source,java]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithCollapseOptionSetToTrue_returnsConfluencePageContentWithCodeMacroWithCollapsedOption() {
        // arrange
        String adocContent = "[source,collapse=true]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"collapse\">true</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithCollapseOptionSetToFalse_returnsConfluencePageContentWithCodeMacroWithoutCollapsedOption() {
        // arrange
        String adocContent = "[source,collapse=false]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExampleBlock_returnsConfluencePageContentWithTipAdmonition() {
        // arrange
        String adocContent = "====\n" +
                "Content\n" +
                "====";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"tip\">" +
                "<ac:rich-text-body><p>Content</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExampleBlockWithTitle_returnsConfluencePageContentWithTipAdmonitionWithTitle() {
        // arrange
        String adocContent = ".Title\n" +
                "====\n" +
                "Content\n" +
                "====";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"tip\">" +
                "<ac:parameter ac:name=\"title\">Title</ac:parameter>" +
                "<ac:rich-text-body><p>Content</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCollapsibleExampleBlock_returnsConfluencePageContentWithExpandMacro() {
        // arrange
        String adocContent = "[%collapsible]\n" +
                "====\n" +
                "Collapsed Content\n" +
                "====";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"expand\">" +
                "<ac:rich-text-body><p>Collapsed Content</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCollapsibleExampleBlockWithTitle_returnsConfluencePageContentWithExpandMacroAndTitle() {
        // arrange
        String adocContent = ".Title\n" +
                "[%collapsible]\n" +
                "====\n" +
                "Collapsed Content\n" +
                "====";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"expand\">" +
                "<ac:parameter ac:name=\"title\">Title</ac:parameter>" +
                "<ac:rich-text-body><p>Collapsed Content</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithAllPossibleSectionLevels_returnsConfluencePageContentWithAllSectionHavingCorrectMarkup() {
        // arrange
        String adocContent = "= Title level 0\n\n" +
                "== Title level 1\n" +
                "=== Title level 2\n" +
                "==== Title level 3\n" +
                "===== Title level 4\n" +
                "====== Title level 5";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_title_level_1</ac:parameter></ac:structured-macro>Title level 1</h1>" +
                "<h2><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_title_level_2</ac:parameter></ac:structured-macro>Title level 2</h2>" +
                "<h3><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_title_level_3</ac:parameter></ac:structured-macro>Title level 3</h3>" +
                "<h4><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_title_level_4</ac:parameter></ac:structured-macro>Title level 4</h4>" +
                "<h5><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_title_level_5</ac:parameter></ac:structured-macro>Title level 5</h5>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithParagraph_returnsConfluencePageContentHavingCorrectParagraphMarkup() {
        // arrange
        String adoc = "some paragraph";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adoc)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>some paragraph</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithBoldText_returnsConfluencePageContentWithBoldMarkup() {
        // arrange
        String adocContent = "*Bold phrase.* bold le**t**ter.";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><strong>Bold phrase.</strong> bold le<strong>t</strong>ter.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithBr_returnsConfluencePageContentWithXhtml() {
        // arrange
        String adocContent = "a +\nb +\nc";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>a<br/>\nb<br/>\nc</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithItalicText_returnsConfluencePageContentWithItalicMarkup() {
        // arrange
        String adocContent = "_Italic phrase_ italic le__t__ter.";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><em>Italic phrase</em> italic le<em>t</em>ter.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImageWithHeightAndWidthAttributeSurroundedByLink_returnsConfluencePageContentWithImageWithHeightAttributeMacroWrappedInLink() {
        // arrange
        String adocContent = "image::sunset.jpg[Sunset, 300, 200, link=\"http://www.foo.ch\"]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<a href=\"http://www.foo.ch\"><ac:image ac:height=\"200\" ac:width=\"300\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image></a>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImageWithTitle_returnsConfluencePageContentWithImageWithTitle() {
        String adocContent = ".A beautiful sunset\n" +
                "image::sunset.jpg[]";

        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        String expectedContent = "<ac:image ac:title=\"A beautiful sunset\" ac:alt=\"A beautiful sunset\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image><div class=\"cp-image-title\"><em>Figure 1. A beautiful sunset</em></div>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImage_returnsConfluencePageContentWithImage() {
        // arrange
        String adocContent = "image::sunset.jpg[]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:image><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImage_returnsConfluencePageContentWithImageAndBorder() {
        // arrange
        String adocContent = "image::sunset.jpg[border=true]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:image ac:border=\"true\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImageInDifferentFolder_returnsConfluencePageContentWithImageAttachmentFileNameOnly() {
        // arrange
        String adocContent = "image::sub-folder/sunset.jpg[]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:image><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithoutTableWithHeader_returnsConfluencePageContentWithTableWithoutHeader() {
        // arrange
        String adocContent = "[cols=\"3*\"]\n" +
                "|===\n" +
                "| A\n" +
                "| B\n" +
                "| C\n" +
                "\n" +
                "| 10\n" +
                "| 11\n" +
                "| 12\n" +
                "\n" +
                "| 20\n" +
                "| 21\n" +
                "| 22\n" +
                "|===";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<table><tbody><tr><td>A</td><td>B</td><td>C</td></tr><tr><td>10</td><td>11</td><td>12</td></tr><tr><td>20</td><td>21</td><td>22</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableWithHeader_returnsConfluencePageContentWithTableWithHeader() {
        // arrange
        String adocContent = "[cols=\"3*\", options=\"header\"]\n" +
                "|===\n" +
                "| A\n" +
                "| B\n" +
                "| C\n" +
                "\n" +
                "| 10\n" +
                "| 11\n" +
                "| 12\n" +
                "\n" +
                "| 20\n" +
                "| 21\n" +
                "| 22\n" +
                "|===";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<table><thead><tr><th>A</th><th>B</th><th>C</th></tr></thead><tbody><tr><td>10</td><td>11</td><td>12</td></tr><tr><td>20</td><td>21</td><td>22</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableWithRowSpan_returnsConfluencePageWithTableWithRowSpan() {
        // arrange
        String adocContent = "[cols=\"3*\", options=\"header\"]\n" +
                "|===\n" +
                "| A\n" +
                "| B\n" +
                "| C\n" +
                "\n" +
                ".2+| 10\n" +
                "| 11\n" +
                "| 12\n" +
                "| 13\n" +
                "| 14\n" +
                "|===";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContent = "<table><thead><tr><th>A</th><th>B</th><th>C</th></tr></thead><tbody><tr><td rowspan=\"2\">10</td><td>11</td><td>12</td></tr><tr><td>13</td><td>14</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableWithColSpan_returnsConfluencePageWithTableWithColSpan() {
        // arrange
        String adocContent = "[cols=\"3*\", options=\"header\"]\n" +
                "|===\n" +
                "| A\n" +
                "| B\n" +
                "| C\n" +
                "\n" +
                "| 10\n" +
                "2+| 11 & 12\n" +
                "\n" +
                "|===";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContent = "<table><thead><tr><th>A</th><th>B</th><th>C</th></tr></thead><tbody><tr><td>10</td><td colspan=\"2\">11 &amp; 12</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableWithAsciiDocCell_returnsConfluencePageWithTableWithAsciiDocCell() {
        // arrange
        String adocContent = "|===\n" +
                "| A " +
                "| B\n" +
                "\n" +
                "| 10 " +
                "a|11\n" +
                "\n" +
                "* 12 \n" +
                "* 13 \n" +
                "\n" +
                "|===";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContent = "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>10</td><td><div><p>11</p>\n<ul><li>12</li><li>13</li></ul></div></td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithNoteContent_returnsConfluencePageContentWithInfoMacroWithContent() {
        // arrange
        String adocContent = "[NOTE]\n" +
                "====\n" +
                "Some note.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:rich-text-body><p>Some note.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithNoteContentAndTitle_returnsConfluencePageContentWithInfoMacroWithContentAndTitle() {
        // arrange
        String adocContent = "[NOTE]\n" +
                ".Note Title\n" +
                "====\n" +
                "Some note.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:parameter ac:name=\"title\">Note Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some note.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTipContent_returnsConfluencePageContentWithTipMacroWithContent() {
        // arrange
        String adocContent = "[TIP]\n" +
                "====\n" +
                "Some tip.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"tip\">" +
                "<ac:rich-text-body><p>Some tip.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTipContentAndTitle_returnsConfluencePageContentWithTipMacroWithContentAndTitle() {
        // arrange
        String adocContent = "[TIP]\n" +
                ".Tip Title\n" +
                "====\n" +
                "Some tip.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"tip\">" +
                "<ac:parameter ac:name=\"title\">Tip Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some tip.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCautionContent_returnsConfluencePageContentWithNoteMacroWithContent() {
        // arrange
        String adocContent = "[CAUTION]\n" +
                "====\n" +
                "Some caution.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:rich-text-body><p>Some caution.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCautionContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() {
        // arrange
        String adocContent = "[CAUTION]\n" +
                ".Caution Title\n" +
                "====\n" +
                "Some caution.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:parameter ac:name=\"title\">Caution Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some caution.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithWarningContent_returnsConfluencePageContentWithNoteMacroWithContent() {
        // arrange
        String adocContent = "[WARNING]\n" +
                "====\n" +
                "Some warning.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:rich-text-body><p>Some warning.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithWarningContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() {
        // arrange
        String adocContent = "[WARNING]\n" +
                ".Warning Title\n" +
                "====\n" +
                "Some warning.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:parameter ac:name=\"title\">Warning Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some warning.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImportantContent_returnsConfluencePageContentWithNoteMacroWithContent() {
        // arrange
        String adocContent = "[IMPORTANT]\n" +
                "====\n" +
                "Some important.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"warning\">" +
                "<ac:rich-text-body><p>Some important.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImportantContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() {
        // arrange
        String adocContent = "[IMPORTANT]\n" +
                ".Important Title\n" +
                "====\n" +
                "Some important.\n" +
                "====";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"warning\">" +
                "<ac:parameter ac:name=\"title\">Important Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some important.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInterDocumentCrossReference_returnsConfluencePageWithLinkToReferencedPageByPageTitle() {
        // arrange
        Path rootFolder = copyAsciidocSourceToTemporaryFolder("src/test/resources/inter-document-cross-references");
        AsciidocPage asciidocPage = asciidocPage(rootFolder, "source-page.adoc");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage), "TEST");

        // assert
        String expectedContent = "<p>This is a <ac:link><ri:page ri:content-title=\"Target Page\" ri:space-key=\"TEST\"></ri:page>" +
                "<ac:plain-text-link-body><![CDATA[reference]]></ac:plain-text-link-body>" +
                "</ac:link> to the target page.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInterDocumentCrossReferenceAnchor_returnsConfluencePageWithLinkToReferencedPageByPageTitleWithAnchor() {
        // arrange
        Path rootFolder = copyAsciidocSourceToTemporaryFolder("src/test/resources/inter-document-cross-references");
        AsciidocPage asciidocPage = asciidocPage(rootFolder, "source-page-reference-with-anchor.adoc");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage), "TEST");

        // assert
        String expectedContent = "<p>This is a <ac:link ac:anchor=\"_anchor\"><ri:page ri:content-title=\"Target Page\" ri:space-key=\"TEST\"></ri:page>" +
                "<ac:plain-text-link-body><![CDATA[reference with anchor]]></ac:plain-text-link-body>" +
                "</ac:link> to the target page.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInterDocumentCrossReferenceToTargetPageWithHtmlCharacterInTitle_returnsConfluencePageWithLinkToReferencedPageByPageTitle() {
        // arrange
        Path rootFolder = copyAsciidocSourceToTemporaryFolder("src/test/resources/inter-document-cross-references");
        AsciidocPage asciidocPage = asciidocPage(rootFolder, "source-page-with-reference-to-target-page-with-html-character-in-title.adoc");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage), "TEST");

        // assert
        String expectedContent = "<p>This is a <ac:link><ri:page ri:content-title=\"Target&amp;Page\" ri:space-key=\"TEST\"></ri:page>" +
                "<ac:plain-text-link-body><![CDATA[reference]]></ac:plain-text-link-body>" +
                "</ac:link> to the target page.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCircularInterDocumentCrossReference_returnsConfluencePagesWithLinkToReferencedPageByPageTitle() {
        // arrange
        AsciidocPage asciidocPageOne = asciidocPage(Paths.get("src/test/resources/circular-inter-document-cross-references/page-one.adoc"));
        AsciidocPage asciidocPageTwo = asciidocPage(Paths.get("src/test/resources/circular-inter-document-cross-references/page-two.adoc"));

        // act
        AsciidocConfluencePage asciidocConfluencePageOne = newAsciidocConfluencePage(asciidocPageOne, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPageOne), "TEST");
        AsciidocConfluencePage asciidocConfluencePageTwo = newAsciidocConfluencePage(asciidocPageTwo, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPageTwo), "TEST");

        // assert
        assertThat(asciidocConfluencePageOne.content(), containsString("<ri:page ri:content-title=\"Page Two\" ri:space-key=\"TEST\">"));
        assertThat(asciidocConfluencePageTwo.content(), containsString("<ri:page ri:content-title=\"Page One\" ri:space-key=\"TEST\">"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentWithoutLinkText_returnsConfluencePageWithLinkToAttachmentAndAttachmentNameAsLinkText() {
        // arrange
        String adocContent = "link:foo.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentWithLinkText_returnsConfluencePageWithLinkToAttachmentAndSpecifiedLinkText() {
        // arrange
        String adocContent = "link:foo.txt[Bar]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment><ac:plain-text-link-body><![CDATA[Bar]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInclude_returnsConfluencePageWithContentFromIncludedPage() {
        // arrange
        Path rootFolder = copyAsciidocSourceToTemporaryFolder("src/test/resources/includes");
        AsciidocPage asciidocPage = asciidocPage(rootFolder, "page.adoc");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        assertThat(asciidocConfluencePage.content(), containsString("<p>main content</p>"));
        assertThat(asciidocConfluencePage.content(), containsString("<p>included content</p>"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithUtf8CharacterInTitle_returnsConfluencePageWithCorrectlyEncodedUtf8CharacterInTitle() {
        try {
            // arrange
            setDefaultCharset(ISO_8859_1);

            String adocContent = "= Title © !";
            AsciidocPage asciidocPage = asciidocPage(adocContent);

            // act
            AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

            // assert
            assertThat(asciidocConfluencePage.pageTitle(), is("Title © !"));
        } finally {
            setDefaultCharset(UTF_8);
        }
    }

    @Test
    public void renderConfluencePage_asciiDocWithUtf8CharacterInContent_returnsConfluencePageWithCorrectlyEncodedUtf8CharacterInContent() {
        try {
            // arrange
            setDefaultCharset(ISO_8859_1);

            String adocContent = "Copyrighted content © !";
            AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

            // act
            AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

            // assert
            assertThat(asciidocConfluencePage.content(), is("<p>Copyrighted content © !</p>"));
        } finally {
            setDefaultCharset(UTF_8);
        }
    }

    @Test
    public void renderConfluencePage_asciiDocWithIsoEncodingAndSpecificSourceEncodingConfigured_returnsConfluencePageWithCorrectlyEncodedContent() {
        // arrange
        AsciidocPage asciidocPage = asciidocPage(Paths.get("src/test/resources/encoding/iso-encoded-source.adoc"));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, ISO_8859_1, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        assertThat(asciidocConfluencePage.content(), is("<p>This line contains an ISO-8859-1 encoded special character 'À'.</p>"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentInDifferentFolder_returnsConfluencePageWithLinkToAttachmentFileNameOnly() {
        // arrange
        String adocContent = "link:bar/foo.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExplicitExternalLinkAndLinkText_returnsConfluencePageWithLinkToExternalPageAndSpecifiedLinkText() {
        // arrange
        String adocContent = "link:http://www.google.com[Google]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">Google</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExternalLinkWithoutLinkText_returnsConfluencePageWithLinkToExternalPageAndUrlAsLinkText() {
        // arrange
        String adocContent = "link:http://www.google.com[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">http://www.google.com</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImplicitExternalLink_returnsConfluencePageWithLinkToExternalPageAndUrlAsLinkText() {
        // arrange
        String adocContent = "http://www.google.com";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">http://www.google.com</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithEmbeddedPlantUmlDiagram_returnsConfluencePageWithLinkToGeneratedPlantUmlImage() {
        // arrange
        String adocContent = "[plantuml, embedded-diagram, png]\n" +
                "....\n" +
                "A <|-- B\n" +
                "....";

        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContentPattern = "<ac:image ac:height=\"[1-9][0-9]*\" ac:width=\"[1-9][0-9]*\"><ri:attachment ri:filename=\"embedded-diagram.png\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), matchesPattern(expectedContentPattern));
        assertTrue(exists(assetsTargetFolderFor(asciidocPage).resolve("embedded-diagram.png")));
    }

    @Test
    public void renderConfluencePage_asciiDocWithIncludedPlantUmlFile_returnsConfluencePageWithLinkToGeneratedPlantUmlImage() {
        // arrange
        Path rootFolder = copyAsciidocSourceToTemporaryFolder("src/test/resources/plantuml");
        AsciidocPage asciidocPage = asciidocPage(rootFolder, "page.adoc");

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContentPattern = "<ac:image ac:height=\"[1-9][0-9]*\" ac:width=\"[1-9][0-9]*\"><ri:attachment ri:filename=\"included-diagram.png\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), matchesPattern(expectedContentPattern));
    }

    @Test
    public void renderConfluencePage_asciiDocWithUnorderedList_returnsConfluencePageHavingCorrectUnorderedListMarkup() {
        // arrange
        String adocContent = "* L1-1\n" +
                "** L2-1\n" +
                "*** L3-1\n" +
                "**** L4-1\n" +
                "***** L5-1\n" +
                "* L1-2";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ul><li>L1-1<ul><li>L2-1<ul><li>L3-1<ul><li>L4-1<ul><li>L5-1</li></ul></li></ul></li></ul></li></ul></li><li>L1-2</li></ul>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithUnorderedListAndTitle_returnsConfluencePageHavingCorrectUnorderedListAndTitleMarkup() {
        // arrange
        String adocContent = ".An unordered list title\n" +
                "* L1-1\n" +
                "* L1-2\n";

        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<div class=\"cp-ulist-title\"><em>An unordered list title</em></div>" + "<ul><li>L1-1</li><li>L1-2</li></ul>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithOrderedList_returnsConfluencePageHavingCorrectOrderedListMarkup() {
        // arrange
        String adocContent = ". L1-1\n" +
                ".. L2-1\n" +
                "... L3-1\n" +
                ".... L4-1\n" +
                "..... L5-1\n" +
                ". L1-2";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ol><li>L1-1<ol><li>L2-1<ol><li>L3-1<ol><li>L4-1<ol><li>L5-1</li></ol></li></ol></li></ol></li></ol></li><li>L1-2</li></ol>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithOrderedListAndTitle_returnsConfluencePageHavingCorrectOrderedListAndTitleMarkup() {
        // arrange
        String adocContent = ".An ordered list title\n" +
                ". L1-1\n" +
                ". L1-2\n";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<div class=\"cp-olist-title\"><em>An ordered list title</em></div><ol><li>L1-1</li><li>L1-2</li></ol>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInlineImage_returnsConfluencePageWithInlineImage() {
        // arrange
        String adocContent = "Some text image:sunset.jpg[] with inline image";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>Some text <ac:image ac:alt=\"sunset\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image> with inline image</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInlineImage_returnsConfluencePageWithInlineImageAndBorder() {
        // arrange
        String adocContent = "Some text image:sunset.jpg[border=true] with inline image";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>Some text <ac:image ac:alt=\"sunset\" ac:border=\"true\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image> with inline image</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInlineRemoteImage_returnsConfluencePageWithInlineRemoteImage() {
        // arrange
        String adocContent = "Some text image:https://asciidoctor.org/images/octocat.jpg[GitHub mascot]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>Some text " +
                "<ac:image ac:alt=\"GitHub mascot\">" +
                "<ri:url ri:value=\"https://asciidoctor.org/images/octocat.jpg\"></ri:url>" +
                "</ac:image></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
        assertThat(asciidocConfluencePage.attachments().size(), is(0));
    }

    @Test
    public void renderConfluencePage_asciiDocWithBlockRemoteImageAndBlockTitle_returnsConfluencePageWithBlockRemoteImageAndImageCaption() {
        String adocContent = ".GitHub mascot\n" +
                "image::https://asciidoctor.org/images/octocat.jpg[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:image ac:title=\"GitHub mascot\" ac:alt=\"GitHub mascot\">" +
                "<ri:url ri:value=\"https://asciidoctor.org/images/octocat.jpg\"></ri:url>" +
                "</ac:image>" +
                "<div class=\"cp-image-title\"><em>Figure 1. GitHub mascot</em></div>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
        assertThat(asciidocConfluencePage.attachments().size(), is(0));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInlineImageWithHeightAndWidthAttributeSurroundedByLink_returnsConfluencePageContentWithInlineImageWithHeightAttributeMacroWrappedInLink() {
        // arrange
        String adocContent = "Some text image:sunset.jpg[Sunset, 16, 20, link=\"http://www.foo.ch\"] with inline image";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>Some text <a href=\"http://www.foo.ch\"><ac:image ac:height=\"20\" ac:width=\"16\" ac:alt=\"Sunset\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image></a> with inline image</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInlineImageInDifferentFolder_returnsConfluencePageContentWithInlineImageAttachmentFileNameOnly() {
        // arrange
        String adocContent = "Some text image:sub-folder/sunset.jpg[] with inline image";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>Some text <ac:image ac:alt=\"sunset\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image> with inline image</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToSectionWithBlockAnchor_returnsConfluencePageContentWithInternalCrossReferenceToSectionUsingBlockAnchor() {
        // arrange
        String adocContent = "[[section-1]]\n" +
                "== Section 1\n" +
                "Cross reference to <<section-1>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">section-1</ac:parameter></ac:structured-macro>Section 1</h1>" +
                "<p>Cross reference to <ac:link ac:anchor=\"section-1\"><ac:plain-text-link-body><![CDATA[Section 1]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToSectionWithCustomId_returnsConfluencePageContentWithInternalCrossReferenceToSectionUsingCustomId() {
        // arrange
        String adocContent = "[#section-1]\n" +
                "== Section 1\n" +
                "Cross reference to <<section-1>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">section-1</ac:parameter></ac:structured-macro>Section 1</h1>" +
                "<p>Cross reference to <ac:link ac:anchor=\"section-1\"><ac:plain-text-link-body><![CDATA[Section 1]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceInlineToSectionWithInlineAnchor_returnsConfluencePageContentWithInternalCrossReferenceToSectionUsingSectionTitle() {
        // arrange
        String adocContent = "== Section 1 [[section-1]]\n" +
                "Cross reference to <<section-1>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">section-1</ac:parameter></ac:structured-macro>Section 1</h1>" +
                "<p>Cross reference to <ac:link ac:anchor=\"section-1\"><ac:plain-text-link-body><![CDATA[Section 1]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToSectionAndCustomLabel_returnsConfluencePageContentWithInternalCrossReferenceToSectionUsingCustomLabel() {
        // arrange
        String adocContent = "== Section 1 [[section-1]]\n" +
                "Cross reference to <<section-1,section 1>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">section-1</ac:parameter></ac:structured-macro>Section 1</h1>" +
                "<p>Cross reference to <ac:link ac:anchor=\"section-1\"><ac:plain-text-link-body><![CDATA[section 1]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToParagraph_returnsConfluencePageContentWithInternalCrossReferenceToParagraphUsingAnchorId() {
        // arrange
        String adocContent = "[[paragraph1]]Paragraph\n\n" +
                "Cross reference to <<paragraph1>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">paragraph1</ac:parameter></ac:structured-macro>Paragraph</p>\n" +
                "<p>Cross reference to <ac:link ac:anchor=\"paragraph1\"><ac:plain-text-link-body><![CDATA[[paragraph1]]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToParagraphAndCustomLabel_returnsConfluencePageContentWithInternalCrossReferenceToParagraphUsingCustomLabel() {
        // arrange
        String adocContent = "[[paragraph1]]Paragraph\n\n" +
                "Cross reference to <<paragraph1,Paragraph>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">paragraph1</ac:parameter></ac:structured-macro>Paragraph</p>\n" +
                "<p>Cross reference to <ac:link ac:anchor=\"paragraph1\"><ac:plain-text-link-body><![CDATA[Paragraph]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInternalCrossReferenceToBibliographyAnchor_returnsConfluencePageContentWithInternalCrossReferenceToBibliographyAnchor() {
        // arrange
        String adocContent = "[bibliography]\n" +
                "== References\n\n" +
                "* [[[pp]]] Entry1\n\n" +
                "* [[[gof,gang]]] Entry2\n\n" +
                "Cross reference to <<pp>> and <<gof>>";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(prependTitle(adocContent)), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<h1><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">_references</ac:parameter></ac:structured-macro>References</h1><ul>" +
                "<li><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">pp</ac:parameter></ac:structured-macro>[pp] Entry1</li>" +
                "<li><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">gof</ac:parameter></ac:structured-macro>[gof] Entry2</li></ul>\n" +
                "<p>Cross reference to <ac:link ac:anchor=\"pp\"><ac:plain-text-link-body><![CDATA[[pp]]]></ac:plain-text-link-body></ac:link>" +
                " and <ac:link ac:anchor=\"gof\"><ac:plain-text-link-body><![CDATA[[gang]]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableOfContentsAsAttribute_returnsConfluencePageContentWithTableOfContentsMacro() {
        // arrange
        String adocContent = "= Page Title\n" +
                ":toc: auto\n";
        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adocContent), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>" +
                "<ac:structured-macro ac:name=\"toc\">" +
                "<ac:parameter ac:name=\"maxLevel\">2</ac:parameter>" +
                "</ac:structured-macro>" +
                "</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableOfContentsAsAttributeWithCustomDepth_returnsConfluencePageContentWithTableOfContentsMacroWithCustomDepth() {
        // arrange
        String adocContent = "= Page Title\n" +
                ":toc: auto\n" +
                ":toclevels: 4\n";
        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adocContent), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<p>" +
                "<ac:structured-macro ac:name=\"toc\">" +
                "<ac:parameter ac:name=\"maxLevel\">4</ac:parameter>" +
                "</ac:structured-macro>" +
                "</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableOfContentsAsMacro_returnsConfluencePageContentWithTableOfContentsMacro() {
        // arrange
        String adocContent = "= Page Title\n" +
                ":toc: macro\n" +
                "\n" +
                "toc::[]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adocContent), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"toc\">" +
                "<ac:parameter ac:name=\"maxLevel\">2</ac:parameter>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableOfContentsAsMacroWithCustomDepth_returnsConfluencePageContentWithTableOfContentsMacroWithCustomDepth() {
        // arrange
        String adocContent = "= Page Title\n" +
                ":toc: macro\n" +
                ":toclevels: 4\n" +
                "\n" +
                "toc::[]";

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage(adocContent), UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"toc\">" +
                "<ac:parameter ac:name=\"maxLevel\">4</ac:parameter>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void attachments_asciiDocWithImage_returnsImageAsAttachmentWithPathAndName() {
        // arrange
        String adocContent = "image::sunset.jpg[]";

        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunset.jpg", "sunset.jpg"));
    }

    @Test
    public void attachments_asciiDocWithImageInDifferentFolder_returnsImageAsAttachmentWithPathAndFileNameOnly() {
        // arrange
        String adocContent = "image::sub-folder/sunset.jpg[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sub-folder/sunset.jpg", "sunset.jpg"));
    }

    @Test
    public void attachments_asciiDocWithMultipleLevelsAndImages_returnsAllAttachments() {
        // arrange
        String adocContent = "= Title 1\n\n" +
                "image::sunset.jpg[]\n" +
                "== Title 2\n" +
                "image::sunrise.jpg[]";
        AsciidocPage asciidocPage = asciidocPage(adocContent);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(2));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunset.jpg", "sunset.jpg"));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
    }

    @Test
    public void attachments_asciiDocWithMultipleTimesSameImage_returnsNoDuplicateAttachments() {
        // arrange
        String adocContent = "image::sunrise.jpg[]\n" +
                "image::sunrise.jpg[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
    }

    @Test
    public void attachments_asciiDocWithLinkToAttachment_returnsAttachmentWithPathAndName() {
        // arrange
        String adocContent = "link:foo.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("foo.txt", "foo.txt"));
    }

    @Test
    public void attachments_asciiDocWithLinkToAttachmentInDifferentFolder_returnsAttachmentWithPathAndFileNameOnly() {
        // arrange
        String adocContent = "link:sub-folder/foo.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sub-folder/foo.txt", "foo.txt"));
    }

    @Test
    public void attachments_asciiDocWithImageAndLinkToAttachment_returnsAllAttachments() {
        // arrange
        String adocContent = "image::sunrise.jpg[]\n" +
                "link:foo.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        assertThat(asciidocConfluencePage.attachments().size(), is(2));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("foo.txt", "foo.txt"));
    }

    @Test
    public void attachments_asciiDocWithAttachmentWithSpaceInName_returnsAttachmentWithPathAndName() {
        // arrange
        String adocContent = "link:attachment%20with%20space.txt[]";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("attachment with space.txt", "attachment with space.txt"));
    }

    @Test
    public void keywords_singleKeyword_returnsSingleKeyword() {
        // arrange
        String adoc = "= Page Title\n"
                + ":keywords: foo";

        AsciidocPage asciidocPage = asciidocPage(adoc);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.keywords().size(), is(1));
        assertThat(asciidocConfluencePage.keywords(), hasItem("foo"));
    }

    @Test
    public void keywords_multipleKeywords_returnAllKeywords() {
        // arrange
        String adoc = "= Page Title\n"
                + ":keywords: foo, bar";

        AsciidocPage asciidocPage = asciidocPage(adoc);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        // assert
        assertThat(asciidocConfluencePage.keywords().size(), is(2));
        assertThat(asciidocConfluencePage.keywords(), hasItem("foo"));
        assertThat(asciidocConfluencePage.keywords(), hasItem("bar"));
    }

    @Test
    public void renderConfluencePage_asciiDocErrorLogWhileRendering_throwsRuntimeException() {
        // arrange
        String adocRelyingOnMissingSequenceDiagramBinary = "[seqdiag#ex-seq-diag,ex-seq-diag,svg]\n" +
                "....\n" +
                "seqdiag {\n" +
                "  webserver -> processor;\n" +
                "}\n" +
                "....\n";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocRelyingOnMissingSequenceDiagramBinary));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath())
        );
        assertThat(exception.getMessage(), containsString("failed to create confluence page for asciidoc content in"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithEmbeddedPlantUmlDiagram_using_C4_macros_returnsConfluencePageWithLinkToGeneratedPlantUmlImage() {
        // arrange
        String adocContent = "[plantuml, embedded-c4-diagram, png]\n" +
                "....\n" +
                "!include <C4/C4_Container>\n" +
                "Container(containerAlias, \"Label\", \"Technology\", \"Optional Description\")\n" +
                "....";

        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, assetsTargetFolderFor(asciidocPage));

        // assert
        String expectedContentPattern = "<ac:image ac:height=\"[1-9][0-9]*\" ac:width=\"[1-9][0-9]*\"><ri:attachment ri:filename=\"embedded-c4-diagram.png\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), matchesPattern(expectedContentPattern));
        assertTrue(exists(assetsTargetFolderFor(asciidocPage).resolve("embedded-c4-diagram.png")));
    }


    @Test
    public void attachments_asciiDocWithStrikeThrough() {
        // arrange
        String adocContent = "normal1 [.strike-through]#strike trough# normal2";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        String expectedContent = "<p>normal1 <s>strike trough</s> normal2</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void attachments_asciiDocWithUnderline() {
        // arrange
        String adocContent = "normal1 [.underline]#underlined# normal2";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        String expectedContent = "<p>normal1 <u>underlined</u> normal2</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void attachments_asciiDocWithUnderline_multiline() {
        // arrange
        String adocContent = "normal1 [.underline]#underlined line1\n" +
            "underlined line2# normal2";
        AsciidocPage asciidocPage = asciidocPage(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(asciidocPage, UTF_8, TEMPLATES_FOLDER, dummyAssetsTargetPath());

        String expectedContent = "<p>normal1 <u>underlined line1\nunderlined line2</u> normal2</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    private static String prependTitle(String content) {
        if (!content.startsWith("= ")) {
            content = "= Default Page Title\n\n" + content;
        }
        return content;
    }

    private static Path assetsTargetFolderFor(AsciidocPage asciidocPage) {
        return asciidocPage.path().getParent();
    }

    private static Path dummyAssetsTargetPath() {
        try {
            return TEMPORARY_FOLDER.newFolder().toPath();
        } catch (IOException e) {
            throw new RuntimeException("Could not create assert target path", e);
        }
    }

    private static Path copyAsciidocSourceToTemporaryFolder(String pathToSampleAsciidocStructure) {
        try {
            Path sourceFolder = Paths.get(pathToSampleAsciidocStructure);
            Path targetFolder = TEMPORARY_FOLDER.newFolder().toPath();

            walk(Paths.get(pathToSampleAsciidocStructure)).forEach((path) -> copyTo(path, targetFolder.resolve(sourceFolder.relativize(path))));

            return targetFolder;
        } catch (IOException e) {
            throw new RuntimeException("Could not copy sample asciidoc structure", e);
        }
    }

    private static void copyTo(Path sourcePath, Path targetPath) {
        try {
            createDirectories(targetPath.getParent());
            copy(sourcePath, targetPath, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy source path to target path", e);
        }
    }

    private static Path temporaryPath(String content) {
        try {
            Path path = TEMPORARY_FOLDER.newFolder().toPath().resolve("tmp").resolve(sha256Hex(content) + ".adoc");
            createDirectories(path.getParent());
            write(path, content.getBytes(UTF_8));

            return path;
        } catch (IOException e) {
            throw new RuntimeException("Could not write content to temporary path", e);
        }
    }

    private AsciidocPage asciidocPage(Path rootFolder, String asciidocFileName) {
        return asciidocPage(rootFolder.resolve(asciidocFileName));
    }

    private AsciidocPage asciidocPage(Path contentPath) {
        return new TestAsciidocPage(contentPath);
    }

    private AsciidocPage asciidocPage(String content) {
        return asciidocPage(temporaryPath(content));
    }


    private static class TestAsciidocPage implements AsciidocPage {

        private final Path path;

        TestAsciidocPage(Path path) {
            this.path = path;
        }

        @Override
        public Path path() {
            return this.path;
        }

        @Override
        public List<AsciidocPage> children() {
            return emptyList();
        }

    }

    private static void setDefaultCharset(Charset charset) {
        try {
            Field defaultCharsetField = Charset.class.getDeclaredField("defaultCharset");
            defaultCharsetField.setAccessible(true);
            defaultCharsetField.set(null, charset);
        } catch (Exception e) {
            throw new RuntimeException("Could not set default charset", e);
        }
    }
}
