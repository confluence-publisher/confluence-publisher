package org.sahli.asciidoc.confluence.publisher.converter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sahli.confluence.publisher.converter.processor.ConfluencePageProcessor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.sahli.asciidoc.confluence.publisher.converter.Helper.SPACE_KEY;

public class AsciidocConfluencePageProcessorTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ConfluencePageProcessor processor(Path buildFolder) {
        try {
            return new AsciidocConfluencePageProcessor(buildFolder, SPACE_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void sourceEncoding_sourceEncodingProvided_returnsProvidedSourceEncoding() throws IOException {
        // arrange
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();
        ConfluencePageProcessor pageProcessor = processor(buildFolder);

        // act
        Charset sourceEncoding = pageProcessor.sourceEncoding();

        // assert
        assertThat(sourceEncoding, is(UTF_8));
    }
}
