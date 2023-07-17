package org.sahli.markdown.processor;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.ConfluenceConverter;
import org.sahli.confluence.publisher.converter.processor.ConfluencePageProcessor;
import org.sahli.confluence.publisher.converter.provider.FolderBasedPagesStructureProvider;
import org.sahli.confluence.publisher.converter.PagesStructureProvider;
import org.sahli.confluence.publisher.converter.processor.PreProcessor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluencePageProcessor.ADOC_FILE_EXTENSION;
import static org.sahli.markdown.processor.FlexmarkProcessor.MD_FILE_EXTENSION;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ToAsciiDocPreProcessorTest {


    private static final String spaceKey = "~personalSpace";
    private static final String ancestorId = "1234";

    private static final String DOCUMENTATION_LOCATION = "src/test/resources/image";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test
    public void convertMarkdownFiles() throws IOException {
        //Arrange
        Path documentationRootFolder = Paths.get(DOCUMENTATION_LOCATION).toAbsolutePath();
        Path buildFolder = this.temporaryFolder.newFolder().toPath().toAbsolutePath();
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("name", "Rick and Morty");
        userAttributes.put("genre", "science fiction");

        ToAsciiDocPreProcessor preProcessor = new ToAsciiDocPreProcessor(UTF_8);

        Map<String, PreProcessor> preProcessors = ImmutableMap.of(MD_FILE_EXTENSION,preProcessor);
        ConfluencePageProcessor processor = new AsciidocConfluencePageProcessor(buildFolder,spaceKey);

        Map<String, ConfluencePageProcessor> pageProcessors = ImmutableMap.of(ADOC_FILE_EXTENSION,processor);

        PagesStructureProvider provider = new FolderBasedPagesStructureProvider(documentationRootFolder, pageProcessors, preProcessors);

        // act
        ConfluenceConverter confluenceConverter = new ConfluenceConverter(spaceKey,ancestorId);
        ConfluencePublisherMetadata confluencePublisherMetadata = confluenceConverter.convert(provider);

        // assert
        assertThat(confluencePublisherMetadata.getSpaceKey(), is("~personalSpace"));
        assertThat(confluencePublisherMetadata.getAncestorId(), is("1234"));
        assertThat(confluencePublisherMetadata.getPages().size(), is(2));
        assertThat(confluencePublisherMetadata.getPages().get(1).getAttachments().size(), is(1));

        //TODO remove generated files !!!

    }
}
