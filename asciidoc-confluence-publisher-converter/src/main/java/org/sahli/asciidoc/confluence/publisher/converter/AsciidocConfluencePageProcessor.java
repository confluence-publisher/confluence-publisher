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

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.asciidoctor.log.LogHandler;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.sahli.confluence.publisher.converter.ConfluencePage;
import org.sahli.confluence.publisher.converter.ConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.Page;
import org.sahli.confluence.publisher.converter.PageTitlePostProcessor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

import static java.nio.file.Files.*;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;
import static org.asciidoctor.Asciidoctor.Factory.create;
import static org.asciidoctor.SafeMode.UNSAFE;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePageProcessor extends ConfluencePageProcessor {

    private static final Asciidoctor ASCIIDOCTOR = create();

    static {
        ASCIIDOCTOR.requireLibrary("asciidoctor-diagram");
        ASCIIDOCTOR.registerLogHandler(new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                if (logRecord.getSeverity().compareTo(Severity.ERROR) >= 0) {
                    throw new RuntimeException(logRecord.getMessage());
                }
            }
        });
    }

    public ConfluencePage newConfluencePage(Page asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes, String spaceKey) {
        try {
            Path asciidocPagePath = asciidocPage.path();
            String asciidocContent = readIntoString(newInputStream(asciidocPagePath), sourceEncoding);

            Map<String, String> attachmentCollector = new HashMap<>();

            Map<String, Object> userAttributesWithMaskedNullValues = maskNullWithEmptyString(userAttributes);
            Options options = options(templatesDir, asciidocPagePath.getParent(), pageAssetsFolder, userAttributesWithMaskedNullValues);

            Document document = ASCIIDOCTOR.load(asciidocContent, options);

            String pageTitle = unescapeHtml(pageTitle(document, userAttributesWithMaskedNullValues, pageTitlePostProcessor));
            String pageContent = convertedContent(document, asciidocPagePath, attachmentCollector, userAttributesWithMaskedNullValues, pageTitlePostProcessor, sourceEncoding, spaceKey);

            List<String> keywords = keywords(document);

            return new ConfluencePage(pageTitle, pageContent, attachmentCollector, keywords);
        } catch (Exception e) {
            throw new RuntimeException("failed to create confluence page for asciidoc content in '" + asciidocPage.path().toAbsolutePath() + " '", e);
        }
    }

    private String convertedContent(Document document, Path pagePath, Map<String, String> attachmentCollector, Map<String, Object> userAttributes, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding, String spaceKey) {
        String content = document.convert();
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath, userAttributes, pageTitlePostProcessor, sourceEncoding, spaceKey),
                collectAndReplaceAttachmentFileNames(attachmentCollector, sourceEncoding),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }

    private static String pageTitle(Document document, Map<String, Object> userAttributes, PageTitlePostProcessor pageTitlePostProcessor) {
        return Optional.ofNullable(document.getStructuredDoctitle())
                .map(title -> title.getMain())
                .map(title -> replaceUserAttributes(title, userAttributes))
                .map(pageTitle -> pageTitlePostProcessor.process(pageTitle))
                .orElseThrow(() -> new RuntimeException("top-level heading or title meta information must be set"));
    }

    private static Options options(Path templatesFolder, Path baseFolder, Path generatedAssetsTargetFolder, Map<String, Object> userAttributes) {
        if (!exists(templatesFolder)) {
            throw new RuntimeException("templateDir folder does not exist");
        }

        if (!isDirectory(templatesFolder)) {
            throw new RuntimeException("templateDir folder is not a folder");
        }

        Attributes attributes = Attributes.builder()
                .attributes(userAttributes)
                .attribute("imagesoutdir", generatedAssetsTargetFolder.toString())
                .attribute("outdir", generatedAssetsTargetFolder.toString())
                .attribute("source-highlighter", "none")
                .build();


        return Options.builder()
                .backend("xhtml5")
                .safe(UNSAFE)
                .baseDir(baseFolder.toFile())
                .templateDirs(templatesFolder.toFile())
                .attributes(attributes)
                .build();
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

    private static List<String> keywords(Document document) {
        String keywords = (String) document.getAttribute("keywords");
        if (keywords == null) {
            return emptyList();
        }

        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .collect(toList());
    }

}
