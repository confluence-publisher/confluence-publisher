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

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.ast.StructuredDocument;
import org.asciidoctor.ast.Title;
import org.asciidoctor.internal.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.asciidoctor.Asciidoctor.Factory.create;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 * @since 1.0
 */
public class AsciidocConfluencePage {

    private static Asciidoctor asciidoctor = create();

    private final String pageTitle;
    private final String htmlContent;
    private final Map<String, String> attachments;

    private AsciidocConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments) {
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.attachments = attachments;
    }

    public String content() {
        return this.htmlContent;
    }

    public String pageTitle() {
        return this.pageTitle;
    }

    public Map<String, String> attachments() {
        return unmodifiableMap(this.attachments);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(InputStream adoc, String templatesDir, Path pagePath) {
        Map<String, String> attachmentCollector = new HashMap<>();

        String adocContent = IOUtils.readFull(adoc);
        StructuredDocument structuredDocument = structuredDocument(adocContent);
        String pageContent = convertedContent(adocContent, options(templatesDir), pagePath, attachmentCollector);

        String pageTitle = pageTitle(structuredDocument);

        return new AsciidocConfluencePage(pageTitle, pageContent, attachmentCollector);
    }

    private static String deriveAttachmentName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private static String convertedContent(String adocContent, Options options, Path pagePath, Map<String, String> attachmentCollector) {
        String content = asciidoctor.convert(adocContent, options);
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath),
                collectAndReplaceAttachmentFileNames(attachmentCollector)
        );

        return postProcessedContent;
    }

    private static Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector) {
        return (content) -> {
            Document document = Jsoup.parse(content, "UTF-8");

            return document.getElementsByTag("ri:attachment").stream().reduce(content, (accumulator, attachmentElement) -> {
                String attachmentPath = attachmentElement.attr("ri:filename");
                String attachmentFileName = deriveAttachmentName(attachmentPath);

                attachmentCollector.put(attachmentPath, attachmentFileName);

                return accumulator.replace("<ri:attachment ri:filename=\"" + attachmentPath + "\"", "<ri:attachment ri:filename=\"" + attachmentFileName + "\"");
            }, unusedCombiner());
        };
    }

    @SafeVarargs
    private static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
    }

    private static StructuredDocument structuredDocument(String adocContent) {
        return asciidoctor.readDocumentStructure(adocContent, emptyMap());
    }

    private static String pageTitle(StructuredDocument structuredDocument) {
        return Optional.ofNullable(structuredDocument.getHeader().getDocumentTitle())
                .map(Title::getMain)
                .orElseThrow(() -> new RuntimeException("top-level heading or title meta information must be set"));
    }

    private static Options options(String templateDir) {
        File templateDirFolder = new File(templateDir);

        if (!templateDirFolder.exists()) {
            throw new RuntimeException("templateDir folder does not exist");
        }

        if (!templateDirFolder.isDirectory()) {
            throw new RuntimeException("templateDir folder is not a folder");
        }

        return OptionsBuilder.options()
                .backend("html")
                .templateDirs(templateDirFolder)
                .get();
    }

    private static Function<String, String> replaceCrossReferenceTargets(Path pagePath) {
        return (content) -> {
            Document document = Jsoup.parse(content, "UTF-8");

            return document.getElementsByTag("ri:page").stream().reduce(content, (accumulator, pageElement) -> {
                String htmlTarget = pageElement.attr("ri:content-title");
                Path referencedPagePath = pagePath.getParent().resolve(Paths.get(htmlTarget.substring(0, htmlTarget.lastIndexOf('.')) + ".adoc"));
                try {
                    String referencedPageContent = IOUtils.readFull(new FileInputStream(referencedPagePath.toFile()));
                    StructuredDocument structuredDocument = structuredDocument(referencedPageContent);
                    String referencedPageTitle = pageTitle(structuredDocument);

                    return accumulator.replace("<ri:page ri:content-title=\"" + htmlTarget + "\"", "<ri:page ri:content-title=\"" + referencedPageTitle + "\"");
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("unable to find cross-referenced page '" + referencedPagePath + "'", e);
                }
            }, unusedCombiner());
        };
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }

}
