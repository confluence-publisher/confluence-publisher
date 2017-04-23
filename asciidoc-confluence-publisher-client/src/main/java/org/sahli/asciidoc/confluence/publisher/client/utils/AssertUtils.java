/*
 * Copyright 2016-2017 the original author or authors.
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

/**
 * @author Alain Sahli
 */
public final class AssertUtils {

    private AssertUtils() {
        throw new UnsupportedOperationException("Utils class cannot be instantiated");
    }

    public static void assertMandatoryParameter(boolean assertion, String parameterName) {
        if (!assertion) {
            throw new IllegalArgumentException(parameterName + " must be set");
        }
    }
}
