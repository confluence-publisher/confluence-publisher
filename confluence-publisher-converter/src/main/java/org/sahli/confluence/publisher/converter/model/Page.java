package org.sahli.confluence.publisher.converter.model;

import org.sahli.confluence.publisher.converter.IoUtils;

import java.nio.file.Path;
import java.util.List;

public interface Page {

    Path path();

    List<Page> children();

    default String extension() {
        return IoUtils.extension(path());
    }

}
