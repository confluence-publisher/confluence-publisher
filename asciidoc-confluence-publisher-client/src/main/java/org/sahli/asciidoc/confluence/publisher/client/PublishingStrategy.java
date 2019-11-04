/*
 * Copyright 2019 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client;

/**
 * @author Laurent Verbruggen
 */
public enum PublishingStrategy {
    APPEND_TO_ANCESTOR(true, false, true),
    APPEND_TO_ANCESTOR_KEEP_CHILDREN(true, false, false),
    REPLACE_ANCESTOR(false, true, true);

    private final boolean appendToAncestor;
    private final boolean replaceAncestor;
    private final boolean deleteExistingChildren;

    PublishingStrategy(boolean appendToAncestor, final boolean replaceAncestor, boolean deleteExistingChildren) {
        this.appendToAncestor = appendToAncestor;
        this.replaceAncestor = replaceAncestor;
        this.deleteExistingChildren = deleteExistingChildren;
    }

    boolean isAppendToAncestor() {
        return appendToAncestor;
    }

    boolean isReplaceAncestor() {
        return replaceAncestor;
    }

    boolean isDeleteExistingChildren() {
        return deleteExistingChildren;
    }
}
