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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.io.IOException;

/**
 * @author Alain Sahli
 */
public class SameJsonAsMatcher extends BaseMatcher<String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode expectedJson;

    private SameJsonAsMatcher(String expectedJson) {
        try {
            this.expectedJson = this.objectMapper.readTree(expectedJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean matches(Object actualValue) {
        if (actualValue instanceof String) {
            String actualStringValue = (String) actualValue;
            try {
                JsonNode actualJson = this.objectMapper.readTree(actualStringValue);
                return this.expectedJson.equals(actualJson);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(this.expectedJson);
    }

    public static SameJsonAsMatcher isSameJsonAs(String expectedJson) {
        return new SameJsonAsMatcher(expectedJson);
    }

}
