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

package org.sahli.asciidoc.confluence.publisher.docker;

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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.containers.BindMode.READ_ONLY;
import static org.testcontainers.containers.Network.SHARED;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;
import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

public class DockerBasedPublishingIntegrationTest {

    @BeforeClass
    public static void exposeConfluenceServerPortOnHost() {
        Testcontainers.exposeHostPorts(8090);
    }

    @Before
    public void deleteAllPages() {
        publish("empty", mandatoryEnvVars());
    }

    @Test
    public void publish_withOnlyMandatoryEnvVars_publishesDocumentationToConfluence() {
        // arrange
        Map<String, String> env = mandatoryEnvVars();

        // act
        publishAndVerify("default", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    @Test
    public void publish_withSourceEncoding_usesSourceEncodingForPublishing() {
        // arrange
        Map<String, String> env = mandatoryEnvVars();
        env.put("SOURCE_ENCODING", "ISO-8859-1");

        // act
        publishAndVerify("source-encoding", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(page(pageIdBy("ISO-8859-1")))
                    .then().body("body.view.value", containsString("ß"));
        });
    }

    @Test
    public void publish_withPrefix_prependsPrefixToTitle() {
        // arrange
        Map<String, String> env = mandatoryEnvVars();
        env.put("PAGE_TITLE_PREFIX", "Prefix - ");

        // act
        publishAndVerify("default", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Prefix - Index"));
        });
    }

    @Test
    public void publish_withSuffix_appendsSuffixToTitle() {
        // arrange
        Map<String, String> env = mandatoryEnvVars();
        env.put("PAGE_TITLE_SUFFIX", " - Suffix");

        // act
        publishAndVerify("default", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index - Suffix"));
        });
    }

    @Test
    public void publish_withReplaceAncestorStrategy_replacesAncestorTitleAndContent() {
        // arrange
        Map<String, String> env = mandatoryEnvVars();
        env.put("PUBLISHING_STRATEGY", "REPLACE_ANCESTOR");

        // act
        publishAndVerify("replace-ancestor", env, () -> {
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
        publish("version-message", mandatoryEnvVars());

        Map<String, String> env = mandatoryEnvVars();
        env.put("VERSION_MESSAGE", "Updated Version");

        // act
        publishAndVerify("version-message", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(page(pageIdBy("AlwaysModified")))
                    .then().body("history.lastUpdated.message", is("Updated Version"));
        });
    }

    @Test
    public void publish_withSkipSslVerificationTrue_allowsPublishingViaSslAndUntrustedCertificate() {
        // arrange
        withReverseProxyEnabled("proxy", 8443, "host.testcontainers.internal", 8090, () -> {
            Map<String, String> env = mandatoryEnvVars();
            env.put("ROOT_CONFLUENCE_URL", "https://proxy:8443");
            env.put("SKIP_SSL_VERIFICATION", "true");

            // act
            publishAndVerify("default", env, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    @Test
    public void publish_withProxySchemeHostAndPort_allowsPublishingViaProxy() {
        // arrange
        withForwardProxyEnabled("proxy", 8443, () -> {
            Map<String, String> env = mandatoryEnvVars();
            env.put("ROOT_CONFLUENCE_URL", "http://host.testcontainers.internal:8090");
            env.put("SKIP_SSL_VERIFICATION", "true");
            env.put("PROXY_SCHEME", "https");
            env.put("PROXY_HOST", "proxy");
            env.put("PROXY_PORT", "8443");

            // act
            publishAndVerify("default", env, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    @Test
    public void publish_withProxySchemeHostPortUsernameAndPassword_allowsPublishingViaProxy() {
        // arrange
        withForwardProxyEnabled("proxy", 8443, "proxy-user", "proxy-password", () -> {
            Map<String, String> env = mandatoryEnvVars();
            env.put("ROOT_CONFLUENCE_URL", "http://host.testcontainers.internal:8090");
            env.put("SKIP_SSL_VERIFICATION", "true");
            env.put("PROXY_SCHEME", "https");
            env.put("PROXY_HOST", "proxy");
            env.put("PROXY_PORT", "8443");
            env.put("PROXY_USERNAME", "proxy-user");
            env.put("PROXY_PASSWORD", "proxy-password");

            // act
            publishAndVerify("default", env, () -> {
                // assert
                givenAuthenticatedAsPublisher()
                        .when().get(childPages())
                        .then().body("results.title", hasItem("Index"));
            });
        });
    }

    private static void publish(String pathToContent, Map<String, String> env) {
        publishAndVerify(pathToContent, env, () -> {
        });
    }

    private static void publishAndVerify(String pathToContent, Map<String, String> env, Runnable runnable) {
        try (GenericContainer<?> publisher = new GenericContainer<>("confluencepublisher/confluence-publisher:0.0.0-SNAPSHOT")
                .withEnv(env)
                .withNetwork(SHARED)
                .withClasspathResourceMapping("/" + pathToContent, "/var/asciidoc-root-folder", READ_ONLY)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(DockerBasedPublishingIntegrationTest.class)))
                .waitingFor(forLogMessage(".*Documentation successfully published to Confluence.*", 1))) {

            publisher.start();
            runnable.run();
        }
    }

    private static void withReverseProxyEnabled(String proxyHost, int proxyPort, String targetHost, int targetPort, Runnable runnable) {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("TARGET_HOST", targetHost);
        env.put("TARGET_PORT", valueOf(targetPort));

        startProxy("confluencepublisher/reverse-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void withForwardProxyEnabled(String proxyHost, int proxyPort, Runnable runnable) {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("BASIC_AUTH", "off");

        startProxy("confluencepublisher/forward-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void withForwardProxyEnabled(String proxyHost, int proxyPort, String proxyUsername, String proxyPassword, Runnable runnable) {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("BASIC_AUTH", "on");
        env.put("BASIC_USERNAME", proxyUsername);
        env.put("BASIC_PASSWORD", proxyPassword);

        startProxy("confluencepublisher/forward-proxy-it:1.0.0", proxyHost, proxyPort, env, runnable);
    }

    private static void startProxy(String dockerImageName, String proxyHost, int proxyPort, Map<String, String> env, Runnable runnable) {
        try (GenericContainer<?> proxy = new GenericContainer<>(dockerImageName)
                .withEnv(env)
                .withNetwork(SHARED)
                .withNetworkAliases(proxyHost)
                .withExposedPorts(proxyPort)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(DockerBasedPublishingIntegrationTest.class)))
                .waitingFor(forListeningPort())) {

            proxy.start();
            runnable.run();
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

    private static Map<String, String> mandatoryEnvVars() {
        Map<String, String> env = new HashMap<>();
        env.put("ROOT_CONFLUENCE_URL", "http://host.testcontainers.internal:8090");
        env.put("SPACE_KEY", "CPI");
        env.put("ANCESTOR_ID", "327706");
        env.put("USERNAME", "confluence-publisher-it");
        env.put("PASSWORD", "1234");

        return env;
    }

}
