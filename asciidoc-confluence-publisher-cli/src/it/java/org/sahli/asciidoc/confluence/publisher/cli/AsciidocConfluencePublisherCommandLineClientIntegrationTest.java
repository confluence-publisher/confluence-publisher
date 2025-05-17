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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.String.valueOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.Network.SHARED;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

public class AsciidocConfluencePublisherCommandLineClientIntegrationTest {

    private static final String CONFLUENCE_ROOT_URL = System.getenv("CPI_ROOT_URL");
    private static final String SPACE_KEY = System.getenv("CPI_SPACE_KEY");
    private static final String ANCESTOR_ID = System.getenv("CPI_ANCESTOR_ID");
    private static final String REST_API_VERSION = System.getenv("CPI_REST_API_VERSION");
    private static final String USERNAME = System.getenv("CPI_USERNAME");
    private static final String PASSWORD = System.getenv("CPI_PASSWORD");

    @BeforeClass
    public static void exposeConfluenceServerPortOnHost() {
        Testcontainers.exposeHostPorts(8090);
    }

    @Before
    public void deleteAllPages() throws Exception {
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/empty"
        };

        AsciidocConfluencePublisherCommandLineClient.main(args);
    }

    @Test
    public void publish_mandatoryArgumentsProvided_publishesDocumentationToConfluence() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/default"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results.title", hasItem("Index"));

        givenAuthenticatedAsPublisher()
                .when().get(contentFor(pageIdBy("Index")))
                .then().body("body.storage.value", is("<p>Content of Index</p>"));
    }

    @Test
    public void publish_customAttributesProvided_replacesAttributesInContent() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
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
    public void publish_labelsProvided_setsLabelsToPage() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/labels"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(labelsFor(pageIdBy("Index")))
                .then().body("results", hasSize(2))
                .and().body("results.name", hasItems("label-one", "label-two"));
    }

    @Test
    public void publish_withSkipSslVerificationTrue_allowsPublishingViaSslAndUntrustedCertificate() throws Exception {
        // arrange
        withReverseProxyEnabled("localhost", 8443, schemeIn(CONFLUENCE_ROOT_URL), toDockerInternalHostIfNeed(hostIn(CONFLUENCE_ROOT_URL)), portIn(CONFLUENCE_ROOT_URL), (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=https://localhost:" + proxyPort + pathIn(CONFLUENCE_ROOT_URL),
                    "username=" + USERNAME,
                    "password=" + PASSWORD,
                    "spaceKey=" + SPACE_KEY,
                    "ancestorId=" + ANCESTOR_ID,
                    "restApiVersion=" + REST_API_VERSION,
                    "asciidocRootFolder=src/it/resources/default",
                    "skipSslVerification=true"
            };

            // act
            AsciidocConfluencePublisherCommandLineClient.main(args);
        });

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_withMaxRequestsPerSecond() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/default",
                "maxRequestsPerSecond=1.5"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_proxySchemeHostAndPortProvided_publishesDocumentationViaProxyToConfluence() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=" + toDockerInternalHostIfNeed(CONFLUENCE_ROOT_URL),
                    "username=" + USERNAME,
                    "password=" + PASSWORD,
                    "spaceKey=" + SPACE_KEY,
                    "ancestorId=" + ANCESTOR_ID,
                    "restApiVersion=" + REST_API_VERSION,
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
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_proxySchemeHostPortAndCredentialsProvided_publishesDocumentationViaProxyToConfluence() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, "proxy-user", "proxy-password", (proxyPort) -> {
            String[] args = {
                    "rootConfluenceUrl=" + toDockerInternalHostIfNeed(CONFLUENCE_ROOT_URL),
                    "username=" + USERNAME,
                    "password=" + PASSWORD,
                    "spaceKey=" + SPACE_KEY,
                    "ancestorId=" + ANCESTOR_ID,
                    "restApiVersion=" + REST_API_VERSION,
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
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results.title", hasItem("Index"));
    }

    @Test
    public void publish_withConvertOnly_doesNotPublishPages() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/default",
                "convertOnly=true"
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().body("results", hasSize(0));
    }

    @Rule
    public TemporaryFolder buildDirectory = new TemporaryFolder();

    @Test
    public void publish_withConvertOnlyAndBuildDirectory_doesNotDeleteTheBuildDirectory() throws Exception {
        // arrange
        String[] args = {
                "rootConfluenceUrl=" + CONFLUENCE_ROOT_URL,
                "username=" + USERNAME,
                "password=" + PASSWORD,
                "spaceKey=" + SPACE_KEY,
                "ancestorId=" + ANCESTOR_ID,
                "restApiVersion=" + REST_API_VERSION,
                "asciidocRootFolder=src/it/resources/default",
                "convertOnly=true",
                "asciidocBuildFolder=" + this.buildDirectory.getRoot().getAbsolutePath()
        };

        // act
        AsciidocConfluencePublisherCommandLineClient.main(args);

        // assert
        assertTrue("Build directory was deleted", this.buildDirectory.getRoot().exists());
    }

    private static String pageIdBy(String title) {
        return givenAuthenticatedAsPublisher()
                .when().get(childPagesFor(ANCESTOR_ID))
                .then().extract().jsonPath().getString("results.find({it.title == '" + title + "'}).id");
    }

    private static String childPagesFor(String pageId) {
        return CONFLUENCE_ROOT_URL + "/rest/api/content/" + pageId + "/child/page";
    }

    private String contentFor(String pageId) {
        return CONFLUENCE_ROOT_URL + "/rest/api/content/" + pageId + "?expand=body.storage";
    }

    private String labelsFor(String pageId) {
        return CONFLUENCE_ROOT_URL + "/rest/api/content/" + pageId + "/label";
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic(USERNAME, PASSWORD);
    }

    private static void withReverseProxyEnabled(String proxyHost, int proxyPort, String targetScheme, String targetHost, int targetPort, PortAwareRunnable runnable) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("PROXY_HOST", proxyHost);
        env.put("PROXY_PORT", valueOf(proxyPort));
        env.put("TARGET_SCHEME", targetScheme);
        env.put("TARGET_HOST", targetHost);
        env.put("TARGET_PORT", valueOf(targetPort));

        startProxy("confluencepublisher/reverse-proxy-it:1.3.0", proxyHost, proxyPort, env, runnable);
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
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(AsciidocConfluencePublisherCommandLineClientIntegrationTest.class)))
                .waitingFor(forListeningPort())) {

            proxy.start();
            runnable.run(proxy.getMappedPort(proxyPort));
        }
    }

    private static String toDockerInternalHostIfNeed(String url) {
        return url.replaceAll("localhost", "host.testcontainers.internal");
    }

    private static String schemeIn(String confluenceRootUrl) {
        return uri(confluenceRootUrl).getScheme();
    }

    private static String hostIn(String confluenceRootUrl) {
        return uri(confluenceRootUrl).getHost();
    }

    private static int portIn(String confluenceRootUrl) {
        URI uri = uri(confluenceRootUrl);
        return uri.getPort() != -1 ? uri.getPort() : uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static String pathIn(String confluenceRootUrl) {
        return uri(confluenceRootUrl).getPath();
    }

    private static URI uri(String confluenceRootUrl) {
        try {
            return new URI(confluenceRootUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid uri '" + confluenceRootUrl + "'", e);
        }
    }

    @FunctionalInterface
    private interface PortAwareRunnable {

        void run(int port) throws Exception;

    }

}
