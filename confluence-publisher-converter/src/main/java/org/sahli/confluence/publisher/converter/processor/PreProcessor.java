package org.sahli.confluence.publisher.converter.processor;

import org.sahli.confluence.publisher.converter.model.DefaultPage;
import org.sahli.confluence.publisher.converter.provider.FileTypeProvider;

import java.nio.file.Path;
import java.util.Map;

public abstract class PreProcessor extends FileTypeProvider implements Processor {
    public abstract Map<Path, DefaultPage> process(Map<Path, DefaultPage> pageIndex);
}
