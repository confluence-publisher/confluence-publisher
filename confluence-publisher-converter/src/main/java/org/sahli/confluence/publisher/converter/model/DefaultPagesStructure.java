package org.sahli.confluence.publisher.converter.model;

import java.util.List;

public class DefaultPagesStructure  implements PagesStructure {

    private final List<Page> pages;

    public DefaultPagesStructure(List<Page> pages) {
        this.pages = pages;
    }

    @Override
    public List<Page> pages() {
        return this.pages;
    }
}
