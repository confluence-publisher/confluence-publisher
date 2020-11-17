/*
 * Copyright 2018 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.cli;

import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class AsciidocConfluencePublisherCommandLineClientTest {

    @Test
    public void main_mandatoryArgumentMissing_throwsException() {
        mandatoryArgumentNames().forEach((mandatoryArgumentName) -> {
            try {
                // arrange
                String[] args = buildArgumentsWithout(mandatoryArgumentName);

                // act
                AsciidocConfluencePublisherCommandLineClient.main(args);
                fail("expected exception for mandatory argument name '" + mandatoryArgumentName + "' not thrown");
            } catch (Exception e) {
                // assert
                assertThat(e, is(instanceOf(IllegalArgumentException.class)));
                assertThat(e.getMessage(), is("mandatory argument '" + mandatoryArgumentName + "' is missing"));
            }
        });
    }

    @Test
    public void main_mandatoryArgumentEmpty_throwsException() {
        mandatoryArgumentNames().forEach((mandatoryArgumentName) -> {
            try {
                // arrange
                String[] args = buildArgumentsWith(mandatoryArgumentName, "");

                // act
                AsciidocConfluencePublisherCommandLineClient.main(args);
                fail("expected exception for mandatory argument name '" + mandatoryArgumentName + "' not thrown");
            } catch (Exception e) {
                // assert
                assertThat(e, is(instanceOf(IllegalArgumentException.class)));
                assertThat(e.getMessage(), is("mandatory argument '" + mandatoryArgumentName + "' is missing"));
            }
        });
    }

    private static Iterable<String> mandatoryArgumentNames() {
        return asList(
                "ancestorId",
                "spaceKey",
                "password",
                "username",
                "rootConfluenceUrl",
                "asciidocRootFolder"
        );
    }

    private static String[] buildArgumentsWithout(String argumentName) {
        return stream(mandatoryArgumentNames().spliterator(), false)
                .filter((mandatoryArgumentName) -> !mandatoryArgumentName.equals(argumentName))
                .map((mandatoryArgumentName) -> mandatoryArgumentName + "=" + mandatoryArgumentName)
                .toArray((size) -> new String[size]);
    }

    private static String[] buildArgumentsWith(String argumentName, String argumentValue) {
        return stream(mandatoryArgumentNames().spliterator(), false)
                .map((mandatoryArgumentName) -> mandatoryArgumentName + "=" + (mandatoryArgumentName.equals(argumentName) ? argumentValue : mandatoryArgumentName))
                .toArray((size) -> new String[size]);
    }

}