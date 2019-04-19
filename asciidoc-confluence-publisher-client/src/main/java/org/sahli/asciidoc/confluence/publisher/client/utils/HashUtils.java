/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.client.utils;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public final class HashUtils {

    private HashUtils() {
        throw new UnsupportedOperationException("Utils class cannot be instantiated");
    }

    public static String contentHash(String content) {
        return sha256Hex(content);
    }

    public static boolean notSameHash(String actualContentHash, String newContentHash) {
        return actualContentHash == null || !actualContentHash.equals(newContentHash);
    }
}
