package org.sahli.asciidoc.confluence.publisher.converter;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Stettler
 */
public class NoOpPageTitlePostProcessorTest {

    @Test
    public void process_default_doesNotModifyPageTitle() throws Exception {
        // arrange
        NoOpPageTitlePostProcessor noOpPageTitlePostProcessor = new NoOpPageTitlePostProcessor();

        // act
        String pageTitle = noOpPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(pageTitle, is("page-title"));
    }

}