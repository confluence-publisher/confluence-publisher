package org.sahli.markdown.processor;

import com.google.common.collect.Sets;
import nl.jworks.markdown_to_asciidoc.Converter;
import org.sahli.confluence.publisher.converter.processor.PreProcessor;
import org.sahli.confluence.publisher.converter.model.DefaultPage;
import org.sahli.confluence.publisher.converter.model.Page;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.file.Files.newInputStream;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePageProcessor.ADOC_FILE_EXTENSION;
import static org.sahli.confluence.publisher.converter.IoUtils.*;
import static org.sahli.markdown.processor.FlexmarkProcessor.MD_FILE_EXTENSION;

public class ToAsciiDocPreProcessor extends PreProcessor {

    private final Charset sourceEncoding;

    private final Set<String> supportedExtensions = Sets.newHashSet(MD_FILE_EXTENSION);

    public ToAsciiDocPreProcessor(Charset sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
    }

    @Override
    public Charset sourceEncoding() {
        return sourceEncoding;
    }

    @Override
    protected Set<String> supportedExtensions() {
        return supportedExtensions;
    }


    private DefaultPage toAsciiDoc(Page page) {
        try {
            String markdownContent = readIntoString(newInputStream(page.path()), sourceEncoding());
            String asciiDoc = Converter.convertMarkdownToAsciiDoc(markdownContent);

            Path asciiDocPath = replaceExtension(page.path(), MD_FILE_EXTENSION, ADOC_FILE_EXTENSION);
            toFile(asciiDoc, asciiDocPath.toFile());

            return new DefaultPage(asciiDocPath, page.children());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<Path, DefaultPage> process(Map<Path, DefaultPage> pageIndex) {

        Map<Path, DefaultPage> reworkedPageIndex = new HashMap<>();

        pageIndex.entrySet().forEach(entry -> {
            if (isFile(entry.getValue().path())) {
                DefaultPage asciiDocPage = toAsciiDoc(entry.getValue());
                reworkedPageIndex.put(entry.getKey(), asciiDocPage);
            } else {
                reworkedPageIndex.put(entry.getKey(), entry.getValue());
            }
        });

        return reworkedPageIndex;

    }

}
