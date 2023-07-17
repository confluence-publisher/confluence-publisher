package org.sahli.confluence.publisher.converter.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.sahli.confluence.publisher.converter.IoUtils.extension;

public abstract class FileTypeProvider {

    protected abstract Set<String> supportedExtensions();

    protected boolean isFile(Path file) {
        if (Files.isRegularFile(file)) {
            return supportedExtensions().contains(extension(file));
        } else {
            return false;
        }
    }
}
