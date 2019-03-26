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
import static org.hamcrest.Matchers.is;

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
                "asciidocRootFolder=src/it/resources",
                "attributes={\"key1\": \"value1\", \"key2\": \"value2\"}"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        String childPageId = givenAuthenticatedAsPublisher()
                .when()
                .get(childPagesFor("327706"))
                .then()
                .body("results.title", hasItem("Index"))
                .extract()
                .body().jsonPath().getString("results[0].id");

        givenAuthenticatedAsPublisher()
                .when()
                .get(contentFor(childPageId))
                .then()
                .body("body.storage.value", is("<p>Hello! value1 value2</p>"));
    }

    private String contentFor(String pageId) {
        return "http://localhost:8090/rest/api/content/" + pageId + "?expand=body.storage";
    }

    private static String childPagesFor(String pageId) {
        return "http://localhost:8090/rest/api/content/" + pageId + "/child/page";
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

}