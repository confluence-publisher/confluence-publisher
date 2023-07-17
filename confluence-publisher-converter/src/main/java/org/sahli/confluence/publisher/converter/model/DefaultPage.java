package org.sahli.confluence.publisher.converter.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public class DefaultPage implements Page {

    private final Path path;
    private final List<Page> children;

    public DefaultPage(Path path) {
        this.path = path;
        this.children = new ArrayList<>();
    }

    public DefaultPage(Path path, List<Page> children) {
        this.path = path;
        this.children = children;
    }

    public void addChild(Page child) {
        this.children.add(child);
    }

    @Override
    public Path path() {
        return this.path;
    }

    @Override
    public List<Page> children() {
        return unmodifiableList(this.children);
    }

}
