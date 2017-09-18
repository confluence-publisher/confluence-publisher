package org.sahli.asciidoc.confluence.publisher.converter;

import java.util.Optional;

/**
 * @author Christian Stettler
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PrefixAndSuffixPageTitlePostProcessor implements PageTitlePostProcessor {

    private final Optional<String> prefix;
    private final Optional<String> suffix;

    public PrefixAndSuffixPageTitlePostProcessor(String prefix, String suffix) {
        this.prefix = Optional.ofNullable(prefix);
        this.suffix = Optional.ofNullable(suffix);
    }

    @Override
    public String process(String pageTitle) {
        return this.prefix.orElse("")
                .concat(pageTitle)
                .concat(this.suffix.orElse(""));
    }

}
