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

package org.sahli.asciidoc.confluence.publisher.maven.plugin;

import io.restassured.specification.RequestSpecification;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.containers.Network.SHARED;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

public class AsciidocConfluencePublisherMojoIntegrationTest {

    @BeforeClass
    public static void exposeConfluenceServerPortOnHost() {
        Testcontainers.exposeHostPorts(8090);
    }

    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    @Before
    public void deleteAllPages() {
        publish("empty", mandatoryProperties());
    }

    @Test
    public void publish_withOnlyMandatoryEnvVars_publishesDocumentationToConfluence() {
        // arrange
        Map<String, String> properties = mandatoryProperties();

        // act
        publishAndVerify("default", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    @Test
    public void publish_withSourceEncoding_usesSourceEncodingForPublishing() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("sourceEncoding", "ISO-8859-1");

        // act
        publishAndVerify("source-encoding", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(page(pageIdBy("ISO-8859-1")))
                    .then().body("body.view.value", containsString("ß"));
        });
    }

    @Test
    public void publish_withPrefix_prependsPrefixToTitle() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("pageTitlePrefix", "Prefix - ");

        // act
        publishAndVerify("default", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Prefix - Index"));
        });
    }

    @Test
    public void publish_withSuffix_appendsSuffixToTitle() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("pageTitleSuffix", " - Suffix");

        // act
        publishAndVerify("default", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index - Suffix"));
        });
    }

    @Test
    public void publish_withReplaceAncestorStrategy_replacesAncestorTitleAndContent() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("publishingStrategy", "REPLACE_ANCESTOR");

        // act
        publishAndVerify("replace-ancestor", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(rootPage())
                    .then().body("title", is("ReplaceAncestor"))
                    .and().body("body.view.value", containsString("Content of ReplaceAncestor"));
        });
    }

    @Test
    public void publish_withVersionMessage_addsVersionMessageToConfluencePage() {
        // arrange
        publish("version-message", mandatoryProperties());

        Map<String, String> properties = mandatoryProperties();
        properties.put("versionMessage", "Updated Version");

        // act
        publishAndVerify("version-message", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(page(pageIdBy("AlwaysModified")))
                    .then().body("history.lastUpdated.message", is("Updated Version"));
        });
    }

    @Test
    public void publish_withSkipSslVerificationTrue_allowsPublishingViaSslAndUntrustedCertificate() throws Exception {
        // arrange
        withReverseProxyEnabled("localhost", 8443, "host.testcontainers.internal", 8090, (proxyPort) -> {
            Map<String, String> properties = mandatoryProperties();
            properties.put("rootConfluenceUrl", "https://localhost:" + proxyPort);
            properties.put("skipSslVerification", "true");

            // act
            publishAndVerify("default", properties, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    @Test
    public void publish_withProxySchemeHostAndPort_allowsPublishingViaProxy() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, (proxyPort) -> {
            Map<String, String> properties = mandatoryProperties();
            properties.put("rootConfluenceUrl", "http://host.testcontainers.internal:8090");
            properties.put("skipSslVerification", "true");
            properties.put("proxyScheme", "https");
            properties.put("proxyHost", "localhost");
            properties.put("proxyPort", valueOf(proxyPort));

            // act
            publishAndVerify("default", properties, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    @Test
    public void publish_withProxySchemeHostPortUsernameAndPassword_allowsPublishingViaProxy() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, "proxy-user", "proxy-password", (proxyPort) -> {
            Map<String, String> properties = mandatoryProperties();
            properties.put("rootConfluenceUrl", "http://host.testcontainers.internal:8090");
            properties.put("skipSslVerification", "true");
            properties.put("proxyScheme", "https");
            properties.put("proxyHost", "localhost");
            properties.put("proxyPort", valueOf(proxyPort));
            properties.put("proxyUsername", "proxy-user");
            properties.put("proxyPassword", "proxy-password");

            // act
            publishAndVerify("default", properties, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    private static void publish(String pathToContent, Map<String, String> properties) {
        publishAndVerify(pathToContent, properties, () -> {
        });
    }

    private static void publishAndVerify(String pathToContent, Map<String, String> properties, Runnable runnable) {
        try {
            File projectDir = ResourceExtractor.extractResourcePath("/" + pathToContent, TEMPORARY_FOLDER.newFolder());
            Files.write(projectDir.toPath().resolve("pom.xml"), generatePom(properties).getBytes(UTF_8));

            Verifier verifier = new Verifier(projectDir.getAbsolutePath());
            verifier.executeGoal("org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-maven-plugin:publish");

            verifier.verifyErrorFreeLog();
            verifier.displayStreamBuffers();

            runnable.run();
        } catch (Exception e) {
            throw new IllegalStateException("publishing failed", e);
        }
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
        try (GenericContainer<?> proxy = new GenericContainer<>(dockerImageName)
                .withEnv(env)
                .withNetwork(SHARED)
                .withNetworkAliases(proxyHost)
                .withExposedPorts(proxyPort)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(AsciidocConfluencePublisherMojoIntegrationTest.class)))
                .waitingFor(forListeningPort())) {

            proxy.start();
            runnable.run(proxy.getMappedPort(proxyPort));
        }
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

    private static String rootPage() {
        return page("327706");
    }

    private static String page(String pageId) {
        return "http://localhost:8090/rest/api/content/" + pageId + "?expand=body.view,history.lastUpdated";
    }

    private static String childPages() {
        return "http://localhost:8090/rest/api/content/327706/child/page";
    }

    private static String pageIdBy(String title) {
        return givenAuthenticatedAsPublisher()
                .when().get(childPages())
                .then().extract().jsonPath().getString("results.find({it.title == '" + title + "'}).id");
    }

    private static Map<String, String> mandatoryProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("rootConfluenceUrl", "http://localhost:8090");
        properties.put("spaceKey", "CPI");
        properties.put("ancestorId", "327706");
        properties.put("username", "confluence-publisher-it");
        properties.put("password", "1234");

        return properties;
    }

    private static String generatePom(Map<String, String> properties) {
        String pluginVersion = System.getProperty("confluence-publisher.version", "0.0.0-SNAPSHOT");

        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                "    <modelVersion>4.0.0</modelVersion>" +
                "" +
                "    <groupId>org.sahli.asciidoc.confluence.publisher.</groupId>" +
                "    <artifactId>confluence-publisher-it</artifactId>" +
                "    <version>0.0.0-SNAPSHOT</version>" +
                "    <packaging>pom</packaging>" +
                "" +
                "    <build>" +
                "        <plugins>" +
                "            <plugin>" +
                "                <groupId>org.sahli.asciidoc.confluence.publisher</groupId>" +
                "                <artifactId>asciidoc-confluence-publisher-maven-plugin</artifactId>" +
                "                <version>" + pluginVersion + "</version>" +
                "                <configuration>" +
                "                    <asciidocRootFolder>.</asciidocRootFolder>" +

                properties.entrySet().stream()
                        .map((property) -> "<" + property.getKey() + "  xml:space=\"preserve\">" + property.getValue() + "</" + property.getKey() + ">")
                        .collect(joining("")) +

                "                </configuration>" +
                "            </plugin>" +
                "        </plugins>" +
                "    </build>" +
                "" +
                "</project>";
    }


    @FunctionalInterface
    private interface PortAwareRunnable {

        void run(int port) throws Exception;

    }

}
