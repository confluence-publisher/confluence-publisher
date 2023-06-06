package org.sahli.confluence.publisher.converter;

import java.nio.file.Path;
import java.util.List;

public interface Page {

    Path path();

    List<Page> children();
}
