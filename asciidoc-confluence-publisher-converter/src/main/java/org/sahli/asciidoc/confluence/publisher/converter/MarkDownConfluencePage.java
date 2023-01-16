package org.sahli.asciidoc.confluence.publisher.converter;

import com.google.common.io.Files;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;

import com.vladsch.flexmark.parser.Parser;

import org.sahli.asciidoc.confluence.publisher.converter.MarkDownPagesStructureProvider.MarkDownPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkDownConfluencePage extends AbstractConfluencePage {

    private final static Logger LOGGER = LoggerFactory.getLogger(MarkDownConfluencePage.class);

    protected MarkDownConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments, List<String> keywords) {
        super(pageTitle, htmlContent, attachments, keywords);
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder) {
        return newMarkDownConfluencePage(markDownPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), "");
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, String spaceKey) {
        return newMarkDownConfluencePage(markDownPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), spaceKey);
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, Map<String, Object> userAttributes) {
        return newMarkDownConfluencePage(markDownPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), userAttributes, "");
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor) {
        return newMarkDownConfluencePage(markDownPage, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, emptyMap(), "");
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) {
        return newMarkDownConfluencePage(markDownPage, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, userAttributes, "");
    }

    public static MarkDownConfluencePage newMarkDownConfluencePage(MarkDownPage markDownPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes, String spaceKey) {
        try {
            Path markDownPagePath = markDownPage.path();
            String markDownContent = readIntoString(newInputStream(markDownPagePath), sourceEncoding);

            Map<String, String> attachmentCollector = new HashMap<>();

            Map<String, Object> userAttributesWithMaskedNullValues = maskNullWithEmptyString(userAttributes);
            DataHolder options = options(templatesDir, markDownPagePath.getParent(), pageAssetsFolder, userAttributesWithMaskedNullValues);

            Parser parser = Parser.builder(options).build();
            Node document = parser.parse(markDownContent);

            String pageTitle = pageTitle(markDownPagePath, pageTitlePostProcessor);
            String pageContent = convertedContent(document, markDownPagePath, attachmentCollector, userAttributesWithMaskedNullValues, pageTitlePostProcessor, sourceEncoding, spaceKey);

            List<String> keywords = keywords(document);

            return new MarkDownConfluencePage(pageTitle, pageContent, attachmentCollector, keywords);
        } catch (Exception e) {
            throw new RuntimeException("failed to create confluence page for asciidoc content in '" + markDownPage.path().toAbsolutePath() + " '", e);
        }
    }

    private static DataHolder options(Path templatesDir, Path parent, Path pageAssetsFolder, Map<String, Object> userAttributesWithMaskedNullValues) {
        LOGGER.debug("No Options implemented for MarkDown.");
        return null;
    }

    private static String convertedContent(Node document, Path pagePath, Map<String, String> attachmentCollector, Map<String, Object> userAttributes, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding, String spaceKey) {
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String content = renderer.render(document);
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath, userAttributes, pageTitlePostProcessor, sourceEncoding, spaceKey),
                collectAndReplaceAttachmentFileNames(attachmentCollector, sourceEncoding),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }

    @SafeVarargs
    private static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
    }

    private static Function<String, String> replaceCrossReferenceTargets(Path pagePath, Map<String, Object> userAttributes, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding, String spaceKey) {
        return (content) -> replaceAll(content, PAGE_TITLE_PATTERN, (matchResult) -> {
            String htmlTarget = matchResult.group(1);
            Path referencedPagePath = pagePath.getParent().resolve(Paths.get(htmlTarget.substring(0, htmlTarget.lastIndexOf('.')) + ".adoc"));

            try {
                String referencedPageContent = readIntoString(new FileInputStream(referencedPagePath.toFile()), sourceEncoding);
                Document referencedDocument = ASCIIDOCTOR.load(referencedPageContent, Options.builder().parseHeaderOnly(true).build());
                String referencedPageTitle = pageTitle(referencedDocument, userAttributes, pageTitlePostProcessor);

                /*
                    Currently the ri:space-key attribute is required in order
                    to update a page that has been converted to the new confluence
                    editor, this seems to be a bug in confluence but needs to be
                    addressed here until it is fixed. See confluence issue:

                    https://jira.atlassian.com/browse/CONFCLOUD-69902
                */
                return "<ri:page ri:content-title=\"" + referencedPageTitle + "\" ri:space-key=\"" + spaceKey + "\"";
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to find cross-referenced page '" + referencedPagePath + "'", e);
            }
        });
    }

    private static Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector, Charset sourceEncoding) {
        return (content) -> replaceAll(content, ATTACHMENT_PATH_PATTERN, (matchResult) -> {
            String attachmentPath = urlDecode(matchResult.group(1), sourceEncoding);
            String attachmentFileName = deriveAttachmentName(attachmentPath);

            attachmentCollector.put(attachmentPath, attachmentFileName);

            return "<ri:attachment ri:filename=\"" + attachmentFileName + "\"";
        });
    }

    private static Function<String, String> unescapeCdataHtmlContent() {
        return (content) -> replaceAll(content, CDATA_PATTERN, (matchResult) -> unescapeHtml(matchResult.group()));
    }

    private static String pageTitle(Path pagePath, PageTitlePostProcessor pageTitlePostProcessor) {
        return Optional.ofNullable(Files.getNameWithoutExtension(pagePath.toFile().getName()))
                .map(pageTitle -> pageTitlePostProcessor.process(pageTitle))
                .orElseThrow(() -> new RuntimeException("top-level heading or title meta information must be set"));
    }

    private static List<String> keywords(Node document) {
        LOGGER.debug("No Keywords extraction implemented for MarkDown.");
        return emptyList();
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }
}
