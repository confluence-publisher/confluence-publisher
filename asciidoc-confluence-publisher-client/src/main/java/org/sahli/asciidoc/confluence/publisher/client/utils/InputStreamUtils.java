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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

/**
 * @author Alain Sahli
 */
public final class InputStreamUtils {

    private InputStreamUtils() {
        throw new UnsupportedOperationException("Utils class cannot be instantiated");
    }

    public static String inputStreamAsString(InputStream is, Charset encoding) {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is, encoding))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Could not convert InputStream to String ", e);
        }
    }

    public static String fileContent(String filePath, Charset encoding) {
        try (FileInputStream fileInputStream = new FileInputStream(new File(filePath))) {
            return inputStreamAsString(fileInputStream, encoding);
        } catch (IOException e) {
            throw new RuntimeException("Could not read file", e);
        }
    }

}
