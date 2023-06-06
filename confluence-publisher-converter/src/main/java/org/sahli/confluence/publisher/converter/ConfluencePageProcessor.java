package org.sahli.confluence.publisher.converter;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;

public abstract class ConfluencePageProcessor {

    private static final Pattern CDATA_PATTERN = compile("<!\\[CDATA\\[.*?\\]\\]>", DOTALL);

    private static final Pattern ATTACHMENT_PATH_PATTERN = compile("<ri:attachment ri:filename=\"(.*?)\"");
    public static final Pattern PAGE_TITLE_PATTERN = compile("<ri:page ri:content-title=\"(.*?)\"");

    public ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder) {
        return newConfluencePage(page, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), "");
    }

    public ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, String spaceKey) {
        return newConfluencePage(page, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap(), spaceKey);
    }

    public ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, Map<String, Object> userAttributes) {
        return newConfluencePage(page, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), userAttributes, "");
    }

    public ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor) {
        return newConfluencePage(page, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, emptyMap(), "");
    }

    public ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) {
        return newConfluencePage(page, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, userAttributes, "");
    }

    public abstract ConfluencePage newConfluencePage(Page page, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes, String spaceKey);


    protected Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector, Charset sourceEncoding) {
        return (content) -> replaceAll(content, ATTACHMENT_PATH_PATTERN, (matchResult) -> {
            String attachmentPath = urlDecode(matchResult.group(1), sourceEncoding);
            String attachmentFileName = deriveAttachmentName(attachmentPath);

            attachmentCollector.put(attachmentPath, attachmentFileName);

            return "<ri:attachment ri:filename=\"" + attachmentFileName + "\"";
        });
    }

    protected static String replaceAll(String content, Pattern pattern, Function<MatchResult, String> replacer) {
        StringBuffer replacedContent = new StringBuffer();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            matcher.appendReplacement(replacedContent, quoteReplacement(replacer.apply(matcher.toMatchResult())));
        }

        matcher.appendTail(replacedContent);

        return replacedContent.toString();
    }

    private static String urlDecode(String value, Charset encoding) {
        try {
            return URLDecoder.decode(value, encoding.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not url-decode value '" + value + "'", e);
        }
    }

    private static String deriveAttachmentName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    protected static Function<String, String> unescapeCdataHtmlContent() {
        return (content) -> replaceAll(content, CDATA_PATTERN, (matchResult) -> unescapeHtml(matchResult.group()));
    }

    @SafeVarargs
    protected static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }

    protected static String replaceUserAttributes(String title, Map<String, Object> userAttributes) {
        return userAttributes.entrySet().stream().reduce(title, (accumulator, entry) -> accumulator.replace("{" + entry.getKey() + "}", entry.getValue().toString()), unusedCombiner());
    }

    protected static String readIntoString(InputStream input, Charset encoding) {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, encoding))) {
                return buffer.lines().collect(joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read file content", e);
        }
    }

    protected static Map<String, Object> maskNullWithEmptyString(Map<String, Object> userAttributes) {
        return userAttributes.entrySet().stream().collect(toMap((entry) -> entry.getKey(), (entry) -> entry.getValue() != null ? entry.getValue() : ""));
    }

}
