package org.sahli.confluence.publisher.converter.model;

import java.nio.file.Path;

public class AttachmentMetadata {

    private final Path sourcePath;
    private final Path targetPath;

    public AttachmentMetadata(Path sourcePath, Path targetPath) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
    }

    public Path sourcePath() {
        return this.sourcePath;
    }

    public Path targetPath() {
        return this.targetPath;
    }
}
