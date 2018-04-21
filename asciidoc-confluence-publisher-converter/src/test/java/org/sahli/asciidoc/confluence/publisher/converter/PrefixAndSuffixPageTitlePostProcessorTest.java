/*
 * Copyright 2017 the original author or authors.
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

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Stettler
 */
public class PrefixAndSuffixPageTitlePostProcessorTest {

    @Test
    public void process_pageTitlePostProcessorWithPrefixAndSuffix_returnsPageTitleWithPrefixAndSuffix() {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor("prefix-", "-suffix");

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("prefix-page-title-suffix"));
    }

    @Test
    public void process_pageTitlePostProcessorWithOnlyPrefix_returnsPageTitleWithPrefix() {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor("prefix-", null);

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("prefix-page-title"));
    }

    @Test
    public void process_pageTitlePostProcessorWithOnlySuffix_returnsPageTitleWithSuffix() {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(null, "-suffix");

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("page-title-suffix"));
    }

    @Test
    public void process_pageTitlePostProcessorWithoutPrefixAndSuffix_returnsPageTitleWithoutPrefixOrSuffix() {
        // arrange
        PrefixAndSuffixPageTitlePostProcessor prefixAndSuffixPageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(null, null);

        // act
        String postProcessedPageTitle = prefixAndSuffixPageTitlePostProcessor.process("page-title");

        // assert
        assertThat(postProcessedPageTitle, is("page-title"));
    }

}
