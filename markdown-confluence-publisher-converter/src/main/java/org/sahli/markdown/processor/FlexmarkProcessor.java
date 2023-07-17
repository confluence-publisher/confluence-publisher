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

package org.sahli.markdown.processor;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jetbrains.annotations.NotNull;
import org.sahli.confluence.publisher.converter.processor.ConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.processor.PageTitlePostProcessor;
import org.sahli.confluence.publisher.converter.model.ConfluencePage;
import org.sahli.confluence.publisher.converter.model.Page;
import org.sahli.markdown.visitor.TitleVisitor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.nio.file.Files.newInputStream;
import static java.util.Collections.emptyList;
import static org.sahli.confluence.publisher.converter.IoUtils.readIntoString;

//TODO implement flexmark parsing
public class FlexmarkProcessor extends ConfluencePageProcessor {

    public static final String MD_FILE_EXTENSION = "md";

    public static final String TEMPLATE_ROOT_CLASS_PATH_LOCATION = "org/sahli/markdown/confluence/publisher/converter/templates";

    private final Parser parser;
    private final HtmlRenderer renderer;

    static final TitleVisitor TITLE_VISITOR = new TitleVisitor();

    public FlexmarkProcessor(Path buildFolder, String spaceKey, Charset sourceEncoding, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) throws IOException {
        super(buildFolder,spaceKey, sourceEncoding, pageTitlePostProcessor, userAttributes);
        parser = initParser();
        renderer = HtmlRenderer.builder(getOptions()).build();
    }

    public FlexmarkProcessor(Path buildFolder, String spaceKey) throws IOException {
        super(buildFolder, spaceKey);
        parser = initParser();
        renderer = HtmlRenderer.builder(getOptions()).build();
    }

    private Parser initParser(){
        return Parser.builder(getOptions()).build();
    }

    @NotNull
    private static MutableDataHolder getOptions() {
        MutableDataHolder options = new MutableDataSet();
        options.setFrom(ParserEmulationProfile.MARKDOWN);
        return options;
    }

    @Override
    protected String templatesLocation() {
        return TEMPLATE_ROOT_CLASS_PATH_LOCATION;
    }

    @Override
    protected String extension() {
        return MD_FILE_EXTENSION;
    }

    @Override
    public ConfluencePage newConfluencePage(Page page, Path pageAssetsFolder) {
        try {
            Path pagePath = page.path();
            String content = readIntoString(newInputStream(pagePath),sourceEncoding());
            Document document = parser.parse(content);
            String pageTitle = title(document);


            Map<String, String> attachmentCollector = new HashMap<>();
            String pageContent = convertedContent(document, pagePath, attachmentCollector);

            List<String> keywords = keywords(document);

            return new ConfluencePage(pageTitle, pageContent, attachmentCollector, keywords);
        } catch (Exception e) {
            throw new RuntimeException("failed to create confluence page for asciidoc content in '" + page.path().toAbsolutePath() + " '", e);
        }
    }

    private static String title(Document document) {
        TITLE_VISITOR.visit(document);
        return TITLE_VISITOR.title();
    }


    private String convertedContent(Document document, Path pagePath, Map<String, String> attachmentCollector) {

        String content = renderer.render(document);
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath),
                collectAndReplaceAttachmentFileNames(attachmentCollector),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }


    private Function<String, String> replaceCrossReferenceTargets(Path pagePath) {
        return (content) -> replaceAll(content, PAGE_TITLE_PATTERN, (matchResult) -> {
            String htmlTarget = matchResult.group(1);
            Path referencedPagePath = pagePath.getParent().resolve(Paths.get(htmlTarget.substring(0, htmlTarget.lastIndexOf('.')) + ".md"));

            try {
                String referencedPageContent = readIntoString(new FileInputStream(referencedPagePath.toFile()),sourceEncoding());
                Document referencedDocument = parser.parse(referencedPageContent);
                String referencedPageTitle = title(referencedDocument);


                //    Currently the ri:space-key attribute is required in order
                //    to update a page that has been converted to the new confluence
                //    editor, this seems to be a bug in confluence but needs to be
                //    addressed here until it is fixed. See confluence issue:
                //    https://jira.atlassian.com/browse/CONFCLOUD-69902
                return "<ri:page ri:content-title=\"" + referencedPageTitle + "\" ri:space-key=\"" + spaceKey() + "\"";
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to find cross-referenced page '" + referencedPagePath + "'", e);
            }
        });
    }

    private static List<String> keywords(Document document) {
        return emptyList();
    }

}
