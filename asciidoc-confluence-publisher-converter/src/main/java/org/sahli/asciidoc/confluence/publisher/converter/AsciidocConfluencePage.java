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
import org.asciidoctor.jruby.AsciidoctorJRuby;
import org.asciidoctor.jruby.internal.JRubyRuntimeContext;
import org.asciidoctor.log.LogHandler;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.jruby.Ruby;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;
import static org.asciidoctor.Asciidoctor.Factory.create;
import static org.asciidoctor.SafeMode.UNSAFE;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePage {

    private static final Pattern CDATA_PATTERN = compile("<!\\[CDATA\\[.*?\\]\\]>", DOTALL);
    private static final Pattern ATTACHMENT_PATH_PATTERN = compile("<ri:attachment ri:filename=\"(.*?)\"");
    private static final Pattern PAGE_TITLE_PATTERN = compile("<ri:page ri:content-title=\"(.*?)\"");

    private static final Asciidoctor ASCIIDOCTOR = getAsciidoctorJRuby();

    private static Asciidoctor getAsciidoctorJRuby() {
        Asciidoctor asciidoctor = null;
        //TODO: gemPath configurable 
        String gemPath = null;
        if (gemPath == null) {
            asciidoctor = AsciidoctorJRuby.Factory.create();
        } else {
            // Replace Windows path separator to avoid paths with mixed \ and /.
            // This happens for instance when setting: <gemPath>${project.build.directory}/gems-provided</gemPath>
            // because the project's path is converted to string.
            String normalizedGemPath = (File.separatorChar == '\\') ? gemPath.replaceAll("\\\\", "/") : gemPath;
            asciidoctor = AsciidoctorJRuby.Factory.create(normalizedGemPath);
        }

        Ruby rubyInstance = null;
        try {
            rubyInstance = (Ruby) JRubyRuntimeContext.class.getMethod("get")
                    .invoke(null);
        } catch (NoSuchMethodException e) {
            if (rubyInstance == null) {
                try {
                    rubyInstance = (Ruby) JRubyRuntimeContext.class.getMethod(
                            "get", Asciidoctor.class).invoke(null, asciidoctor);
                } catch (Exception e1) {
                    throw new RuntimeException(
                            "Failed to invoke get(AsciiDoctor) for JRubyRuntimeContext",
                            e1);
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to invoke get for JRubyRuntimeContext", e);
        }

        String gemHome = rubyInstance.evalScriptlet("ENV['GEM_HOME']").toString();
        String gemHomeExpected = (gemPath == null || "".equals(gemPath)) ? "" : gemPath.split(java.io.File.pathSeparator)[0];

        if (!"".equals(gemHome) && !gemHomeExpected.equals(gemHome)) {
           // getLog().warn("Using inherited external environment to resolve gems (" + gemHome + "), i.e. build is platform dependent!");
        }

        return asciidoctor;
    }

    static {
        //default lib
        ASCIIDOCTOR.requireLibrary("asciidoctor-kroki");
        ASCIIDOCTOR.registerLogHandler(new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                if (logRecord.getSeverity().compareTo(Severity.ERROR) >= 0) {
                    throw new RuntimeException(logRecord.getMessage());
                }
            }
        });
    }

    private final String pageTitle;
    private final String htmlContent;
    private final Map<String, String> attachments;
    private final List<String> keywords;

    private AsciidocConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments, List<String> keywords) {
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.attachments = attachments;
        this.keywords = keywords;
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

    public List<String> keywords() {
        return unmodifiableList(this.keywords);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), "");
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, String spaceKey) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), spaceKey);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, Map<String, Object> userAttributes) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), userAttributes, "");
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, emptyMap(), "");
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, userAttributes, "");
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes, String spaceKey) {
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

            return new AsciidocConfluencePage(pageTitle, pageContent, attachmentCollector, keywords);
        } catch (Exception e) {
            throw new RuntimeException("failed to create confluence page for asciidoc content in '" + asciidocPage.path().toAbsolutePath() + " '", e);
        }
    }

    private static String deriveAttachmentName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private static String convertedContent(Document document, Path pagePath, Map<String, String> attachmentCollector, Map<String, Object> userAttributes, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding, String spaceKey) {
        String content = document.convert();
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath, userAttributes, pageTitlePostProcessor, sourceEncoding, spaceKey),
                collectAndReplaceAttachmentFileNames(attachmentCollector, sourceEncoding),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }

    private static Function<String, String> unescapeCdataHtmlContent() {
        return (content) -> replaceAll(content, CDATA_PATTERN, (matchResult) -> unescapeHtml(matchResult.group()));
    }

    private static Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector, Charset sourceEncoding) {
        return (content) -> replaceAll(content, ATTACHMENT_PATH_PATTERN, (matchResult) -> {
            String attachmentPath = urlDecode(matchResult.group(1), sourceEncoding);
            String attachmentFileName = deriveAttachmentName(attachmentPath);

            attachmentCollector.put(attachmentPath, attachmentFileName);

            return "<ri:attachment ri:filename=\"" + attachmentFileName + "\"";
        });
    }

    private static String replaceAll(String content, Pattern pattern, Function<MatchResult, String> replacer) {
        StringBuffer replacedContent = new StringBuffer();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            matcher.appendReplacement(replacedContent, quoteReplacement(replacer.apply(matcher.toMatchResult())));
        }

        matcher.appendTail(replacedContent);

        return replacedContent.toString();
    }

    @SafeVarargs
    private static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
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
                .attribute("kroki-fetch-diagram", true)
                .attribute("outdir", generatedAssetsTargetFolder.toString())
                .attribute("imagesdir", generatedAssetsTargetFolder.toString())
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

    private static String replaceUserAttributes(String title, Map<String, Object> userAttributes) {
        return userAttributes.entrySet().stream().reduce(title, (accumulator, entry) -> accumulator.replace("{" + entry.getKey() + "}", entry.getValue().toString()), unusedCombiner());
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }

    private static Map<String, Object> maskNullWithEmptyString(Map<String, Object> userAttributes) {
        return userAttributes.entrySet().stream().collect(toMap((entry) -> entry.getKey(), (entry) -> entry.getValue() != null ? entry.getValue() : ""));
    }

    private static String readIntoString(InputStream input, Charset encoding) {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, encoding))) {
                return buffer.lines().collect(joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read file content", e);
        }
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

    private static String urlDecode(String value, Charset encoding) {
        try {
            return URLDecoder.decode(value, encoding.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not url-decode value '" + value + "'", e);
        }
    }

}
