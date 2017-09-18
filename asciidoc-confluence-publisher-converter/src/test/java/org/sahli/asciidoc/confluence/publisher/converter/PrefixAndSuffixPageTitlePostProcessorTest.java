package org.sahli.asciidoc.confluence.publisher.converter;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Stettler
 */
public class PrefixAndSuffixPageTitlePostProcessorTest {

    @Test
    public void process_pageTitlePostProcessorWithPrefixAndSuffix_returnsPageTitleWithPrefixAndSuffix() throws Exception {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor("prefix-", "-suffix");

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("prefix-page-title-suffix"));
    }

    @Test
    public void process_pageTitlePostProcessorWithOnlyPrefix_returnsPageTitleWithPrefix() throws Exception {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor("prefix-", null);

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("prefix-page-title"));
    }

    @Test
    public void process_pageTitlePostProcessorWithOnlySuffix_returnsPageTitleWithSuffix() throws Exception {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(null, "-suffix");

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("page-title-suffix"));
    }

    @Test
    public void process_pageTitlePostProcessorWithoutPrefixAndSuffix_returnsPageTitleWithoutPrefixOrSuffix() throws Exception {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(null, null);

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("page-title"));
    }

}