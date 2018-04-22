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

import io.restassured.specification.RequestSpecification;
import org.junit.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

public class AsciidocConfluencePublisherCommandLineClientIntegrationTest {

    @Test
    public void publish_mandatoryArgumentsProvided_publishesDocumentationToConfluence() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=http://localhost:8090",
                "username=confluence-publisher-it",
                "password=1234",
                "spaceKey=CPI",
                "ancestorId=327706",
                "asciidocRootFolder=src/it/resources"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPages())
                .then().body("results.title", hasItem("Index"));
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

    private static String childPages() {
        return "http://localhost:8090/rest/api/content/327706/child/page";
    }

}