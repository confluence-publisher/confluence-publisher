package org.sahli.asciidoc.confluence.publisher.converter;

import java.nio.file.Path;
import java.util.List;

public interface AsciidocPagesStructureProvider {

    AsciidocPagesStructure structure();


    interface AsciidocPagesStructure {

        List<AsciidocPage> pages();

    }


    interface AsciidocPage {

        Path path();

        List<AsciidocPage> children();

    }

}
