package org.sahli.asciidoc.confluence.publisher.converter;

/**
 * @author Christian Stettler
 */
public class NoOpPageTitlePostProcessor implements PageTitlePostProcessor {

    @Override
    public String process(String pageTitle) {
        return pageTitle;
    }

}
