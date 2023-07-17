package org.sahli.confluence.publisher.converter.processor;

import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.confluence.publisher.converter.model.AttachmentMetadata;
import org.sahli.confluence.publisher.converter.model.ConfluencePage;
import org.sahli.confluence.publisher.converter.model.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileSystems.newFileSystem;
import static java.nio.file.Files.*;
import static java.nio.file.Files.list;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;
import static org.sahli.confluence.publisher.converter.ConfluenceHelper.uniquePageId;
import static org.sahli.confluence.publisher.converter.IoUtils.replaceExtension;

public abstract class ConfluencePageProcessor implements Processor {

    private final static Logger LOGGER = LoggerFactory.getLogger(ConfluencePageProcessor.class);

    private static final Pattern CDATA_PATTERN = compile("<!\\[CDATA\\[.*?\\]\\]>", DOTALL);

    private static final Pattern ATTACHMENT_PATH_PATTERN = compile("<ri:attachment ri:filename=\"(.*?)\"");
    public static final Pattern PAGE_TITLE_PATTERN = compile("<ri:page ri:content-title=\"(.*?)\"");

    private final Path buildFolder;
    private final Path templatesRootFolder;
    private final Path assetsRootFolder;

    private final Charset sourceEncoding;

    private final PageTitlePostProcessor pageTitlePostProcessor;

    private final Map<String, Object> userAttributes;

    private final String spaceKey;

    public ConfluencePageProcessor(Path buildFolder, String spaceKey, Charset sourceEncoding, Map<String, Object> userAttributes) throws IOException {
        this(buildFolder,spaceKey, sourceEncoding, new NoOpPageTitlePostProcessor(), userAttributes);
    }

    public ConfluencePageProcessor(Path buildFolder, String spaceKey, Map<String, Object> userAttributes) throws IOException {
        this(buildFolder,spaceKey, UTF_8, new NoOpPageTitlePostProcessor(), userAttributes);
    }

    public ConfluencePageProcessor(Path buildFolder, String spaceKey) throws IOException {
        this(buildFolder, spaceKey, UTF_8, new NoOpPageTitlePostProcessor(), emptyMap());
    }

    public ConfluencePageProcessor(Path buildFolder, String spaceKey, PageTitlePostProcessor pageTitlePostProcessor) throws IOException {
        this(buildFolder, spaceKey, UTF_8, pageTitlePostProcessor, emptyMap());
    }

    public ConfluencePageProcessor(Path buildFolder,String spaceKey, Charset charset) throws IOException {
        this(buildFolder, spaceKey, charset, new NoOpPageTitlePostProcessor(), emptyMap());
    }


    public ConfluencePageProcessor(Path buildFolder,String spaceKey, Charset sourceEncoding, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) throws IOException {
        this.buildFolder = buildFolder;
        this.sourceEncoding = sourceEncoding;
        this.pageTitlePostProcessor = pageTitlePostProcessor;
        this.userAttributes = userAttributes;
        this.spaceKey = spaceKey;
        this.templatesRootFolder = buildFolder().resolve("templates").toAbsolutePath();
        createDirectories(templatesRootFolder);

        this.assetsRootFolder = buildFolder().resolve("assets").toAbsolutePath();
        createDirectories(assetsRootFolder);

        extractTemplates();
    }

    private Path buildFolder() {
        return this.buildFolder;
    }

    protected Path templatesRootFolder() {
        return templatesRootFolder;
    }


    public Charset sourceEncoding() {
        return sourceEncoding;
    }

    protected PageTitlePostProcessor pageTitlePostProcessor() {
        return pageTitlePostProcessor;
    }

    protected Map<String, Object> userAttributes() {
        return userAttributes;
    }

    protected String spaceKey() {
        return spaceKey;
    }



    protected abstract String templatesLocation();

    protected abstract String extension();

    public void extractTemplates() throws IOException {
        createDirectories(this.templatesRootFolder);
        withTemplates((template) -> copyTemplateTo(this.templatesRootFolder, template));
    }

    private static void copyTemplateTo(Path targetFolder, Path template) {
        try {
            copy(template, targetFolder.resolve(template.getFileName().toString()), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not write template to target file", e);
        }
    }

    private void withTemplates(Consumer<Path> templateConsumer) {
        try {
            URI templatePathUri = resolveTemplateRootUri();

            if (templatePathUri.getScheme().startsWith("jar")) {
                withTemplatesFromJar(templatePathUri, templateConsumer);
            } else {
                withTemplatesFromFileSystem(templatePathUri, templateConsumer);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve template root folder", e);
        }
    }

    private static void withTemplatesFromFileSystem(URI templatePathUri, Consumer<Path> templateConsumer) throws IOException {
        list(Paths.get(templatePathUri)).forEach(templateConsumer);
    }

    private void withTemplatesFromJar(URI templatePathUri, Consumer<Path> templateConsumer) throws IOException {
        URI jarFileUri = URI.create(templatePathUri.toString().substring(0, templatePathUri.toString().indexOf('!')));

        try (FileSystem jarFileSystem = newFileSystem(jarFileUri, emptyMap())) {
            Path templateRootFolder = jarFileSystem.getPath("/" + templatesLocation());
            list(templateRootFolder).forEach(templateConsumer);
        }
    }


    private URI resolveTemplateRootUri() throws URISyntaxException {
        URL templateRootUrl = ConfluencePage.class.getClassLoader().getResource(templatesLocation());

        if (templateRootUrl == null) {
            throw new RuntimeException("Could not load templates from class path '" + templatesLocation() + "'");
        }

        return templateRootUrl.toURI();
    }

    private Path determinePageAssetsFolder(Page page) {
        String uniquePageId = uniquePageId(page.path());
        Path pageAssetsFolder = assetsRootFolder.resolve(uniquePageId);

        return pageAssetsFolder;
    }

    public abstract ConfluencePage newConfluencePage(Page page, Path pageAssetsFolder);


    protected Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector) {
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

    protected Map<String, Object> maskNullWithEmptyString() {
        return userAttributes().entrySet().stream().collect(toMap((entry) -> entry.getKey(), (entry) -> entry.getValue() != null ? entry.getValue() : ""));
    }


    public ConfluencePageMetadata process(Page page) throws IOException {
        Path pageAssetsFolder = determinePageAssetsFolder(page);
        createDirectories(pageAssetsFolder);

        ConfluencePage confluencePage = newConfluencePage(page, pageAssetsFolder);
        Path contentFileTargetPath = writeToTargetStructure(page, pageAssetsFolder, confluencePage);

        List<AttachmentMetadata> attachments = buildAttachments(page, pageAssetsFolder, confluencePage.attachments());
        copyAttachmentsAvailableInSourceStructureToTargetStructure(attachments);
        ensureAttachmentsExist(attachments);

        return buildConfluencePageMetadata(confluencePage, contentFileTargetPath, attachments);
    }

    private ConfluencePageMetadata buildConfluencePageMetadata(ConfluencePage confluencePage, Path contentFileTargetPath, List<AttachmentMetadata> attachments) {
        ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
        confluencePageMetadata.setTitle(confluencePage.pageTitle());
        confluencePageMetadata.setContentFilePath(contentFileTargetPath.toAbsolutePath().toString());
        confluencePageMetadata.getAttachments().putAll(toTargetAttachmentFileNameAndAttachmentPath(attachments));
        confluencePageMetadata.getLabels().addAll(confluencePage.keywords());

        return confluencePageMetadata;
    }


    private static Map<String, String> toTargetAttachmentFileNameAndAttachmentPath(List<AttachmentMetadata> attachments) {
        return attachments.stream().collect(toMap(
                (attachment) -> attachment.targetPath().getFileName().toString(),
                (attachment) -> attachment.targetPath().toString()
        ));
    }

    private static Path writeToTargetStructure(Page page, Path pageAssetsFolder, ConfluencePage confluencePage) {
        try {
            Path contentFileTargetPath = determineTargetPagePath(page, pageAssetsFolder);
            write(contentFileTargetPath, confluencePage.content().getBytes(UTF_8));

            return contentFileTargetPath;
        } catch (IOException e) {
            throw new RuntimeException("Could not write content of page '" + page.path().toAbsolutePath().toString() + "' to target folder", e);
        }
    }

    private static List<AttachmentMetadata> buildAttachments(Page page, Path pageAssetsFolder, Map<String, String> attachmentsWithRelativePath) {
        return attachmentsWithRelativePath.keySet().stream()
                .map((attachmentWithRelativePath) -> {
                    LOGGER.info(" Attachments Relative Path : " + attachmentWithRelativePath);
                    Path relativeAttachmentPath = Paths.get(attachmentWithRelativePath);
                    Path attachmentSourcePath = page.path().getParent().resolve(relativeAttachmentPath);
                    Path attachmentTargetPath = pageAssetsFolder.resolve(relativeAttachmentPath.getFileName());

                    return new AttachmentMetadata(attachmentSourcePath, attachmentTargetPath);
                })
                .collect(toList());
    }

    private static void copyAttachmentsAvailableInSourceStructureToTargetStructure(List<AttachmentMetadata> attachments) {
        attachments.forEach((attachment) -> {
            try {
                LOGGER.info("Trying to copy file from : " + attachment.sourcePath());
                LOGGER.info("to : " + attachment.targetPath());
                if (exists(attachment.sourcePath())) {
                    copy(attachment.sourcePath(), attachment.targetPath(), REPLACE_EXISTING);
                }
            } catch (DirectoryNotEmptyException e){
                LOGGER.warn(e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException("Could not copy attachment to target structure", e);
            }
        });
    }

    private static void ensureAttachmentsExist(List<AttachmentMetadata> attachments) {
        attachments.forEach((attachment) -> ensureAttachmentExists(attachment));
    }

    private static void ensureAttachmentExists(AttachmentMetadata attachment) {
        boolean attachmentExists = exists(attachment.targetPath());

        if (!(attachmentExists)) {
            throw new RuntimeException("Attachment '" + attachment.sourcePath().getFileName() + "' does not exist");
        }
    }

    private static Path determineTargetPagePath(Page asciidocPage, Path pageAssetsFolder) {
        return replaceExtension(pageAssetsFolder.resolve(asciidocPage.path().getFileName()), ".adoc", ".html");
    }
}
