package org.sahli.confluence.publisher.converter;

import java.nio.file.Path;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public class ConfluenceHelper {

    public static String uniquePageId(Path pagePath) {
        return sha256Hex(pagePath.toAbsolutePath().toString());
    }

}
