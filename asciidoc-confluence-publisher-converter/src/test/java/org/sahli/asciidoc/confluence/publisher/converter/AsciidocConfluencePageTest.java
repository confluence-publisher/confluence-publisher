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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePage.newAsciidocConfluencePage;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePageTest {

    private static final String TEMPLATES_DIR = "src/main/resources/org/sahli/asciidoc/confluence/publisher/converter/templates";

    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void render_asciidocWithTopLevelHeader_returnsConfluencePageWithPageTitleFromTopLevelHeader() throws Exception {
        // arrange
        String adoc = "= Page title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title"));
    }

    @Test
    public void render_asciidocWithTitleMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() throws Exception {
        // arrange
        String adoc = "= Page title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() throws Exception {
        // arrange
        String adoc = ":title: Page title (meta)\n" +
                "= Page Title (header)";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title (meta)"));
    }

    @Test
    public void render_asciidocWithNeitherTopLevelHeaderNorTitleMetaInformation_returnsConfluencePageWithPageTitleFromMetaInformation() throws Exception {
        // arrange
        String adoc = "Content";

        // assert
        this.expectedException.expect(RuntimeException.class);
        this.expectedException.expectMessage("top-level heading or title meta information must be set");

        // act
        newAsciidocConfluencePage(new ByteArrayInputStream(adoc.getBytes()), TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());
    }

    @Test
    public void renderConfluencePage_asciiDocWithListing_returnsConfluencePageContentWithMacroWithNameNoFormat() throws Exception {
        // arrange
        String adocContent = "----\n" +
                "import java.util.List;\n" +
                "----";

        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"noformat\">" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListing_returnsConfluencePageContentWithMacroWithNameCode() throws Exception {
        // arrange
        String adocContent = "[source]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithJavaSourceListing_returnsConfluencePageContentWithMacroWithNameCodeAndParameterJava() throws Exception {
        // arrange
        String adocContent = "[source,java]\n" +
                "----\n" +
                "import java.util.List;\n" +
                "----";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithListingWithHtmlMarkup_returnsConfluencePageContentWithMacroWithoutHtmlEscape() throws Exception {
        // arrange
        String adocContent = "----\n" +
                "<b>line one</b>\n" +
                "<b>line two</b>\n" +
                "----";

        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"noformat\">" +
                "<ac:plain-text-body><![CDATA[<b>line one</b>\n<b>line two</b>]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithSourceListingWithHtmlContent_returnsConfluencePageContentWithoutHtmlEscape() throws Exception {
        // arrange
        String adocContent = "[source]\n" +
                "----\n" +
                "<b>content with html</b>\n" +
                "----";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:plain-text-body><![CDATA[<b>content with html</b>]]></ac:plain-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciiDocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithAllPossibleSectionLevels_returnsConfluencePageContentWithAllSectionHavingCorrectMarkup() throws Exception {
        // arrange
        String adocContent = "= Title level 0\n\n" +
                "== Title level 1\n" +
                "=== Title level 2\n" +
                "==== Title level 3\n" +
                "===== Title level 4\n" +
                "====== Title level 5";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<h1>Title level 1</h1>" +
                "<h2>Title level 2</h2>" +
                "<h3>Title level 3</h3>" +
                "<h4>Title level 4</h4>" +
                "<h5>Title level 5</h5>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithParagraph_returnsConfluencePageContentHavingCorrectParagraphMarkup() throws Exception {
        // arrange
        String adoc = "some paragraph";
        InputStream is = stringAsInputStream(prependTitle(adoc));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p>some paragraph</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithBoldText_returnsConfluencePageContentWithBoldMarkup() throws Exception {
        // arrange
        String adocContent = "*Bold phrase.* bold le**t**ter.";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><strong>Bold phrase.</strong> bold le<strong>t</strong>ter.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithItalicText_returnsConfluencePageContentWithItalicMarkup() throws Exception {
        // arrange
        String adocContent = "_Italic phrase_ italic le__t__ter.";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><em>Italic phrase</em> italic le<em>t</em>ter.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImageWithHeightAndWidthAttributeSurroundedByLink_returnsConfluencePageContentWithImageWithHeightAttributeMacroWrappedInLink() throws Exception {
        // arrange
        String adocContent = "image::sunset.jpg[Sunset, 300, 200, link=\"http://www.foo.ch\"]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<a href=\"http://www.foo.ch\"><ac:image ac:height=\"200\" ac:width=\"300\"><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image></a>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImage_returnsConfluencePageContentWithImage() throws Exception {
        // arrange
        String adocContent = "image::sunset.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:image><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImageInDifferentFolder_returnsConfluencePageContentWithImageAttachmentFileNameOnly() throws Exception {
        // arrange
        String adocContent = "image::sub-folder/sunset.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:image><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithoutTableWithHeader_returnsConfluencePageContentWithTableWithoutHeader() throws Exception {
        // arrange
        String adocContent = "" +
                "[cols=\"3*\"]\n" +
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
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<table><tbody><tr><td>A</td><td>B</td><td>C</td></tr><tr><td>10</td><td>11</td><td>12</td></tr><tr><td>20</td><td>21</td><td>22</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTableWithHeader_returnsConfluencePageContentWithTableWithHeader() throws Exception {
        // arrange
        String adocContent = "" +
                "[cols=\"3*\", options=\"header\"]\n" +
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
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<table><thead><tr><th>A</th><th>B</th><th>C</th></tr></thead><tbody><tr><td>10</td><td>11</td><td>12</td></tr><tr><td>20</td><td>21</td><td>22</td></tr></tbody></table>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithNoteContent_returnsConfluencePageContentWithInfoMacroWithContent() {
        // arrange
        String adocContent = "[NOTE]\n" +
                "====\n" +
                "Some note.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:rich-text-body><p>Some note.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithNoteContentAndTitle_returnsConfluencePageContentWithInfoMacroWithContentAndTitle() throws Exception {
        // arrange
        String adocContent = "[NOTE]\n" +
                ".Note Title\n" +
                "====\n" +
                "Some note.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:parameter ac:name=\"title\">Note Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some note.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTipContent_returnsConfluencePageContentWithInfoMacroWithContent() {
        // arrange
        String adocContent = "[TIP]\n" +
                "====\n" +
                "Some tip.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:rich-text-body><p>Some tip.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithTipContentAndTitle_returnsConfluencePageContentWithInfoMacroWithContentAndTitle() throws Exception {
        // arrange
        String adocContent = "[TIP]\n" +
                ".Tip Title\n" +
                "====\n" +
                "Some tip.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"info\">" +
                "<ac:parameter ac:name=\"title\">Tip Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some tip.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCautionContent_returnsConfluencePageContentWithNoteMacroWithContent() throws Exception {
        // arrange
        String adocContent = "[CAUTION]\n" +
                "====\n" +
                "Some caution.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:rich-text-body><p>Some caution.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCautionContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() throws Exception {
        // arrange
        String adocContent = "[CAUTION]\n" +
                ".Caution Title\n" +
                "====\n" +
                "Some caution.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:parameter ac:name=\"title\">Caution Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some caution.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithWarningContent_returnsConfluencePageContentWithNoteMacroWithContent() throws Exception {
        // arrange
        String adocContent = "[WARNING]\n" +
                "====\n" +
                "Some warning.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:rich-text-body><p>Some warning.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithWarningContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() throws Exception {
        // arrange
        String adocContent = "[WARNING]\n" +
                ".Warning Title\n" +
                "====\n" +
                "Some warning.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"note\">" +
                "<ac:parameter ac:name=\"title\">Warning Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some warning.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImportantContent_returnsConfluencePageContentWithNoteMacroWithContent() throws Exception {
        // arrange
        String adocContent = "[IMPORTANT]\n" +
                "====\n" +
                "Some important.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"warning\">" +
                "<ac:rich-text-body><p>Some important.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImportantContentAndTitle_returnsConfluencePageContentWithNoteMacroWithContentAndTitle() throws Exception {
        // arrange
        String adocContent = "[IMPORTANT]\n" +
                ".Important Title\n" +
                "====\n" +
                "Some important.\n" +
                "====";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"warning\">" +
                "<ac:parameter ac:name=\"title\">Important Title</ac:parameter>" +
                "<ac:rich-text-body><p>Some important.</p></ac:rich-text-body>" +
                "</ac:structured-macro>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInterDocumentCrossReference_returnsConfluencePageWithLinkToReferencedPageByPageTitle() throws Exception {
        // arrange
        String relativeSourcePagePath = "src/test/resources/inter-document-cross-references/source-page.adoc";
        FileInputStream sourceInputStream = new FileInputStream(relativeSourcePagePath);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(sourceInputStream, TEMPLATES_DIR, dummyOutputPath(), pagePathOf(relativeSourcePagePath));

        // assert
        String expectedContent = "<p>This is a <ac:link><ri:page ri:content-title=\"Target Page\"></ri:page>" +
                "<ac:plain-text-link-body><![CDATA[reference]]></ac:plain-text-link-body>" +
                "</ac:link> to the target page.</p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithCircularInterDocumentCrossReference_returnsConfluencePagesWithLinkToReferencedPageByPageTitle() throws Exception {
        // arrange
        String relativePagePathOne = "src/test/resources/circular-inter-document-cross-references/page-one.adoc";
        FileInputStream sourceInputStreamOne = new FileInputStream(relativePagePathOne);
        String relativePagePathTwo = "src/test/resources/circular-inter-document-cross-references/page-two.adoc";
        FileInputStream sourceInputStreamTwo = new FileInputStream(relativePagePathTwo);

        // act
        AsciidocConfluencePage asciidocConfluencePageOne = newAsciidocConfluencePage(sourceInputStreamOne, TEMPLATES_DIR, dummyOutputPath(), pagePathOf(relativePagePathOne));
        AsciidocConfluencePage asciidocConfluencePageTwo = newAsciidocConfluencePage(sourceInputStreamTwo, TEMPLATES_DIR, dummyOutputPath(), pagePathOf(relativePagePathTwo));

        // assert
        assertThat(asciidocConfluencePageOne.content(), containsString("<ri:page ri:content-title=\"Page Two\">"));
        assertThat(asciidocConfluencePageTwo.content(), containsString("<ri:page ri:content-title=\"Page One\">"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentWithoutLinkText_returnsConfluencePageWithLinkToAttachmentAndAttachmentNameAsLinkText() throws Exception {
        // arrange
        String adocContent = "link:foo.txt[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentWithLinkText_returnsConfluencePageWithLinkToAttachmentAndSpecifiedLinkText() throws Exception {
        // arrange
        String adocContent = "link:foo.txt[Bar]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment><ac:plain-text-link-body><![CDATA[Bar]]></ac:plain-text-link-body></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithInclude_returnsConfluencePageWithContentFromIncludedPage() throws Exception {
        // arrange
        String relativeSourcePagePath = "src/test/resources/includes/page.adoc";
        FileInputStream sourceInputStream = new FileInputStream(relativeSourcePagePath);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(sourceInputStream, TEMPLATES_DIR, dummyOutputPath(), pagePathOf(relativeSourcePagePath));

        // assert
        assertThat(asciidocConfluencePage.content(), containsString("<p>main content</p>"));
        assertThat(asciidocConfluencePage.content(), containsString("<p>included content</p>"));
    }

    @Test
    public void renderConfluencePage_asciiDocWithLinkToAttachmentInDifferentFolder_returnsConfluencePageWithLinkToAttachmentFileNameOnly() throws Exception {
        // arrange
        String adocContent = "link:bar/foo.txt[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><ac:link><ri:attachment ri:filename=\"foo.txt\"></ri:attachment></ac:link></p>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExplicitExternalLinkAndLinkText_returnsConfluencePageWithLinkToExternalPageAndSpecifiedLinkText() throws Exception {
        // arrange
        String adocContent = "link:http://www.google.com[Google]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">Google</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithExternalLinkWithoutLinkText_returnsConfluencePageWithLinkToExternalPageAndUrlAsLinkText() throws Exception {
        // arrange
        String adocContent = "link:http://www.google.com[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">http://www.google.com</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithImplicitExternalLink_returnsConfluencePageWithLinkToExternalPageAndUrlAsLinkText() throws Exception {
        // arrange
        String adocContent = "http://www.google.com";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<p><a href=\"http://www.google.com\">http://www.google.com</a></p>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
    }

    @Test
    public void renderConfluencePage_asciiDocWithEmbeddedPlantUmlDiagram_returnsConfluencePageWithLinkToGeneratedPlantUmlImage() throws Exception {
        // arrange
        String adocContent = "[plantuml, embedded-diagram, png]\n" +
                "....\n" +
                "A <|-- B\n" +
                "....";

        InputStream is = stringAsInputStream(prependTitle(adocContent));
        String imagesOutDirectory = dummyOutputPath();

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, imagesOutDirectory, dummyPagePath());

        // assert
        String expectedContent = "<ac:image ac:height=\"175\" ac:width=\"57\"><ri:attachment ri:filename=\"embedded-diagram.png\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
        assertThat(new File(imagesOutDirectory, "embedded-diagram.png").exists(), is(true));
    }

    @Test
    public void renderConfluencePage_asciiDocWithIncludedPlantUmlFile_returnsConfluencePageWithLinkToGeneratedPlantUmlImage() throws Exception {
        // arrange
        String relativeSourcePagePath = "src/test/resources/plantuml/page.adoc";
        FileInputStream sourceInputStream = new FileInputStream(relativeSourcePagePath);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(sourceInputStream, TEMPLATES_DIR, dummyOutputPath(), pagePathOf(relativeSourcePagePath));

        // assert
        String expectedContent = "<ac:image ac:height=\"175\" ac:width=\"57\"><ri:attachment ri:filename=\"included-diagram.png\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), containsString(expectedContent));
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
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ul><li>L1-1<ul><li>L2-1<ul><li>L3-1<ul><li>L4-1<ul><li>L5-1</li></ul></li></ul></li></ul></li></ul></li><li>L1-2</li></ul>";
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
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        String expectedContent = "<ol><li>L1-1<ol><li>L2-1<ol><li>L3-1<ol><li>L4-1<ol><li>L5-1</li></ol></li></ol></li></ol></li></ol></li><li>L1-2</li></ol>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void attachments_asciiDocWithImage_returnsImageAsAttachmentWithPathAndName() throws Exception {
        // arrange
        String adocContent = "image::sunset.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunset.jpg", "sunset.jpg"));
    }

    @Test
    public void attachments_asciiDocWithImageInDifferentFolder_returnsImageAsAttachmentWithPathAndFileNameOnly() throws Exception {
        // arrange
        String adocContent = "image::sub-folder/sunset.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sub-folder/sunset.jpg", "sunset.jpg"));
    }

    @Test
    public void attachments_asciiDocWithMultipleLevelsAndImages_returnsAllAttachments() throws Exception {
        // arrange
        String adocContent = "= Title 1\n\n" +
                "image::sunset.jpg[]\n" +
                "== Title 2\n" +
                "image::sunrise.jpg[]";
        InputStream is = stringAsInputStream(adocContent);

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(2));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunset.jpg", "sunset.jpg"));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
    }

    @Test
    public void attachments_asciiDocWithMultipleTimesSameImage_returnsNoDuplicateAttachments() throws Exception {
        // arrange
        String adocContent = "image::sunrise.jpg[]\n" +
                "image::sunrise.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
    }

    @Test
    public void attachments_asciiDocWithLinkToAttachment_returnsAttachmentWithPathAndName() throws Exception {
        // arrange
        String adocContent = "link:foo.txt[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("foo.txt", "foo.txt"));
    }

    @Test
    public void attachments_asciiDocWithLinkToAttachmentInDifferentFolder_returnsAttachmentWithPathAndFileNameOnly() throws Exception {
        // arrange
        String adocContent = "link:sub-folder/foo.txt[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(1));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sub-folder/foo.txt", "foo.txt"));
    }

    @Test
    public void attachments_asciiDocWithImageAndLinkToAttachment_returnsAllAttachments() throws Exception {
        // arrange
        String adocContent = "image::sunrise.jpg[]\n" +
                "link:foo.txt[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR, dummyOutputPath(), dummyPagePath());

        // assert
        assertThat(asciidocConfluencePage.attachments().size(), is(2));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("sunrise.jpg", "sunrise.jpg"));
        assertThat(asciidocConfluencePage.attachments(), hasEntry("foo.txt", "foo.txt"));
    }

    private static String prependTitle(String content) {
        if (!(content.startsWith("= "))) {
            content = "= Default Page Title\n\n" + content;
        }
        return content;
    }

    private static InputStream stringAsInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
    }

    private static Path pagePathOf(String relativeSourcePagePath) {
        return Paths.get(relativeSourcePagePath);
    }

    private static Path dummyPagePath() {
        return temporaryPath();
    }

    private static String dummyOutputPath() {
        return temporaryPath().toString();
    }

    private static Path temporaryPath() {
        try {
            return TEMPORARY_FOLDER.newFolder().toPath();
        } catch (IOException e) {
            throw new IllegalStateException("unable to create temporary path", e);
        }
    }

}
