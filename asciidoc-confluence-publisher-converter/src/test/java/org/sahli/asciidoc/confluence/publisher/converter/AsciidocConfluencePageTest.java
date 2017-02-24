/*
 * Copyright 2016 the original author or authors.
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
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.Matchers.contains;
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

    @Rule
    public ExpectedException expectedException = none();

    @Test
    public void render_asciidocWithTopLevelHeader_returnsConfluencePageWithPageTitleFromTopLevelHeader() throws Exception {
        // arrange
        String adoc = "= Page title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR);

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title"));
    }

    @Test
    public void render_asciidocWithTitleMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() throws Exception {
        // arrange
        String adoc = ":title: Page title";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR);

        // assert
        assertThat(asciiDocConfluencePage.pageTitle(), is("Page title"));
    }

    @Test
    public void render_asciidocWithTopLevelHeaderAndMetaInformation_returnsConfluencePageWithPageTitleFromTitleMetaInformation() throws Exception {
        // arrange
        String adoc = ":title: Page title (meta)\n" +
                "= Page Title (header)";

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(stringAsInputStream(adoc), TEMPLATES_DIR);

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
        newAsciidocConfluencePage(new ByteArrayInputStream(adoc.getBytes()), TEMPLATES_DIR);
    }

    @Test
    public void renderConfluencePage_asciiDocWithListing_returnsConfluencePageContentWithMacroWithNameNoformat() throws Exception {
        // arrange
        String adocContent = "----\n" +
                "import java.util.List;\n" +
                "----";

        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciiDocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

        // assert
        String expectedContent = "<ac:structured-macro ac:name=\"code\">" +
                "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
                "<ac:plain-text-body><![CDATA[import java.util.List;]]></ac:plain-text-body>" +
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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

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
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

        // assert
        String expectedContent = "<ac:image><ri:attachment ri:filename=\"sunset.jpg\"></ri:attachment></ac:image>";
        assertThat(asciidocConfluencePage.content(), is(expectedContent));
    }

    @Test
    public void images_asciiDocwithImage_returnsFilePathToImage() throws Exception {
        // arrange
        String adocContent = "image::sunset.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

        // assert
        assertThat(asciidocConfluencePage.images(), contains("sunset.jpg"));
    }

    @Test
    public void images_asciiDocWithMultipleLevelsAndImages_returnsAllImages() throws Exception {
        // arrange
        String adocContent = "= Title 1\n\n" +
                "image::sunset.jpg[]\n" +
                "== Title 2\n" +
                "image::sunrise.jpg[]";
        InputStream is = stringAsInputStream(prependTitle(adocContent));

        // act
        AsciidocConfluencePage asciidocConfluencePage = newAsciidocConfluencePage(is, TEMPLATES_DIR);

        // assert
        assertThat(asciidocConfluencePage.images(), contains("sunset.jpg", "sunrise.jpg"));
    }

    private static String prependTitle(String content) {
        if (!(content.startsWith("= "))) {
            content = "= Default Page Title\n\n" + content;
        }
        return content;
    }

    private static InputStream stringAsInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes());
    }

}