package org.sahli.asciidoc.confluence.publisher.client.http.payloads;

import org.sahli.asciidoc.confluence.publisher.client.support.RuntimeUse;

public class Label {

    private final String name;

    public Label(String name) {
        this.name = name;
    }

    @RuntimeUse
    public String getName() {
        return name;
    }
}