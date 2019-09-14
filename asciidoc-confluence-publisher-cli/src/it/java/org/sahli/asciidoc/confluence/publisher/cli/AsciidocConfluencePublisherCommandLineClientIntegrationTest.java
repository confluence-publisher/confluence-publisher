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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.String.valueOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.containers.Network.SHARED;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

public class AsciidocConfluencePublisherCommandLineClientIntegrationTest {

    @BeforeClass
    public static void exposeConfluenceServerPortOnHost() {
        Testcontainers.exposeHostPorts(8090);
    }

    @Before
    public void deleteAllPages() throws Exception {
        String[] args = {
                "rootConfluenceUrl=http://localhost:8090",
                "username=confluence-publisher-it",
                "password=1234",
                "spaceKey=CPI",
                "ancestorId=327706",
                "asciidocRootFolder=src/it/resources/empty"
        };

        AsciidocConfluencePublisherCommandLineClient.main(args);
    }

    @Test
    public void publish_mandatoryArgumentsProvided_publishesDocumentationToConfluence() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=http://localhost:8090",
                "username=confluence-publisher-it",
                "password=1234",
                "spaceKey=CPI",
                "ancestorId=327706",
                "asciidocRootFolder=src/it/resources/default"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor("327706"))
                .then().body("results.title", hasItem("Index"));

        givenAuthenticatedAsPublisher()
                .when().get(contentFor(pageIdBy("Index")))
                .then().body("body.storage.value", is("<p>Content of Index</p>"));
    }

    @Test
    public void publish_customAttributesProvided_replacesAttributesInContent() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=http://localhost:8090",
                "username=confluence-publisher-it",
                "password=1234",
                "spaceKey=CPI",
                "ancestorId=327706",
                "asciidocRootFolder=src/it/resources/attributes",
                "attributes={\"attribute-1\": \"value-1\", \"attribute-2\": \"value-2\"}"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(contentFor(pageIdBy("Index")))
                .then().body("body.storage.value", is("<p>value-1 value-2</p>"));
    }

    @Test
    public void publish_withSkipSslVerificationTrue_allowsPublishingViaSslAndUntrustedCertificate() throws Exception {
        // arrange
        withReverseProxyEnabled("localhost", 8443, "host.testcontainers.internal", 8090, (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=https://localhost:" + proxyPort,
                    "username=confluence-publisher-it",
                    "password=1234",
                    "spaceKey=CPI",
                    "ancestorId=327706",
                    "asciidocRootFolder=src/it/resources/default",
                    "skipSslVerification=true"
            };

            // act
            AsciidocConfluencePublisherCommandLineClient.main(args);
        });

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor("327706"))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_proxySchemeHostAndPortProvided_publishesDocumentationViaProxyToConfluence() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=http://host.testcontainers.internal:8090",
                    "username=confluence-publisher-it",
                    "password=1234",
                    "spaceKey=CPI",
                    "ancestorId=327706",
                    "asciidocRootFolder=src/it/resources/default",
                    "skipSslVerification=true",
                    "proxyScheme=https",
                    "proxyHost=localhost",
                    "proxyPort=" + proxyPort
            };

            // act
            AsciidocConfluencePublisherCommandLineClient.main(args);
        });

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor("327706"))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_proxySchemeHostPortAndCredentialsProvided_publishesDocumentationViaProxyToConfluence() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, "proxy-user", "proxy-password", (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=http://host.testcontainers.internal:8090",
                    "username=confluence-publisher-it",
                    "password=1234",
                    "spaceKey=CPI",
                    "ancestorId=327706",
                    "asciidocRootFolder=src/it/resources/default",
                    "skipSslVerification=true",
                    "proxyScheme=https",
                    "proxyHost=localhost",
                    "proxyPort=" + proxyPort,
                    "proxyUsername=proxy-user",
                    "proxyPassword=proxy-password"
            };

            // act
            AsciidocConfluencePublisherCommandLineClient.main(args);
        });

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor("327706"))
                .then().body("results.title", hasItem("Index"));
    }

    private static String pageIdBy(String title) {
        return givenAuthenticatedAsPublisher()
                .when().get(childPagesFor("327706"))
                .then().extract().jsonPath().getString("results.find({it.title == '" + title + "'}).id");
    }

    private static String childPagesFor(String pageId) {
        return "http://localhost:8090/rest/api/content/" + pageId + "/child/page";
    }

    private String contentFor(String pageId) {
        return "http://localhost:8090/rest/api/content/" + pageId + "?expand=body.storage";
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

    private static void withReverseProxyEnabled(String proxyHost, int proxyPort, String targetHost, int targetPort, PortAwareRunnable runnable) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("TARGET_HOST", targetHost);
        env.put("TARGET_PORT", valueOf(targetPort));

        startProxy("confluencepublisher/reverse-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void withForwardProxyEnabled(String proxyHost, int proxyPort, PortAwareRunnable runnable) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("BASIC_AUTH", "off");

        startProxy("confluencepublisher/forward-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void withForwardProxyEnabled(String proxyHost, int proxyPort, String proxyUsername, String proxyPassword, PortAwareRunnable runnable) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("BASIC_AUTH", "on");
        env.put("BASIC_USERNAME", proxyUsername);
        env.put("BASIC_PASSWORD", proxyPassword);

        startProxy("confluencepublisher/forward-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void startProxy(String dockerImageName, String proxyHost, int proxyPort, Map<String, String> env, PortAwareRunnable runnable) throws Exception {
        try (GenericContainer proxy = new GenericContainer(dockerImageName)
                .withEnv(env)
                .withNetwork(SHARED)
                .withNetworkAliases(proxyHost)
                .withExposedPorts(proxyPort)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(AsciidocConfluencePublisherCommandLineClientIntegrationTest.class)))
                .waitingFor(forListeningPort())) {

            proxy.start();
            runnable.run(proxy.getMappedPort(proxyPort));
        }
    }


    @FunctionalInterface
    private interface PortAwareRunnable {

        void run(int port) throws Exception;

    }

}