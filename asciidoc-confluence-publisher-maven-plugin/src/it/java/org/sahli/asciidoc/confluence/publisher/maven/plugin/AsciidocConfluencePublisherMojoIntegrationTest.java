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
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sahli.asciidoc.confluence.publisher.maven.plugin.testutils.ConfluenceServer;
import org.sahli.asciidoc.confluence.publisher.maven.plugin.testutils.ConfluenceServerSetup;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static org.apache.maven.it.util.ResourceExtractor.extractResourcePath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.containers.Network.SHARED;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@RunWith(Parameterized.class)
public class AsciidocConfluencePublisherMojoIntegrationTest {

    private static final String POM_PROPERTIES = "pomProperties";
    private static final String COMMAND_LINE_ARGUMENTS = "commandLineArguments";

    private static final ConfluenceServer confluenceServer = ConfluenceServerSetup.setupContainer();

    @Parameters(name = "{0}")
    public static Object[] parameters() {
        return new Object[]{POM_PROPERTIES, COMMAND_LINE_ARGUMENTS};
    }

    @Parameter
    public String propertiesMode;

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
    public void publish_withKeepOrphansRemovalStrategy_doesNotRemoveOrphans() {
        // arrange
        Map<String, String> env = mandatoryProperties();
        env.put("orphanRemovalStrategy", "KEEP_ORPHANS");

        publishAndVerify("default", env, () -> {
        });

        // act
        publishAndVerify("keep-orphans", env, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItems("Index", "Keep Orphans"));
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
        withReverseProxyEnabled("localhost", 8443, "host.testcontainers.internal", confluenceServer.port, (proxyPort) -> {
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
    public void publish_withMaxRequestsPerSecond() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("maxRequestsPerSecond", "1.5");

        // act
        publishAndVerify("default", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    @Test
    public void publish_withProxySchemeHostAndPort_allowsPublishingViaProxy() throws Exception {
        // arrange
        withForwardProxyEnabled("localhost", 8443, (proxyPort) -> {
            Map<String, String> properties = mandatoryProperties();
            properties.put("rootConfluenceUrl", "http://host.testcontainers.internal:" + confluenceServer.port);
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
            properties.put("rootConfluenceUrl", "http://host.testcontainers.internal:" + confluenceServer.port);
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

    @Test
    public void publish_withConvertOnly_doesNotPublishPages() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("convertOnly", "true");

        // act
        publishAndVerify("default", properties, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results", hasSize(0));
        });
    }

    @Test
    public void publish_withUsernameAndPlainTextPasswordInSettings_publishesDocumentationToConfluence() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.remove("username");
        properties.remove("password");
        properties.put("serverId", "usernameAndPasswordServer");

        Map<String, String> serverProperties = new HashMap<>();
        serverProperties.put("id", "usernameAndPasswordServer");
        serverProperties.put("username", "confluence-publisher-it");
        serverProperties.put("password", "1234");

        // act
        publishAndVerify("default", properties, serverProperties, null, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    @Test
    public void publish_withUsernameAndEncryptedPasswordInSettings_publishesDocumentationToConfluence() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.remove("username");
        properties.remove("password");
        properties.put("serverId", "usernameAndPasswordServer");

        Map<String, String> serverProperties = new HashMap<>();
        serverProperties.put("id", "usernameAndPasswordServer");
        serverProperties.put("username", "confluence-publisher-it");
        serverProperties.put("password", "{3ddzJwZi04gHy8WJMQ0C9N4FgT5VzpFW975lyFXLenY=}");

        String encryptedMasterPassword = "{dXtQ9/Xk/ZwHrozpmlDcatoR8eOZrTB9JRaIUaCI0hM=}";

        // act
        publishAndVerify("default", properties, serverProperties, encryptedMasterPassword, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    @Test
    public void publish_withUsernameAndPasswordBothAsConfigurationPropertiesAndInSettings_usesUsernameAndPasswordFromConfigurationProperties() {
        // arrange
        Map<String, String> properties = mandatoryProperties();
        properties.put("serverId", "usernameAndPasswordServer");

        Map<String, String> serverProperties = new HashMap<>();
        serverProperties.put("id", "usernameAndPasswordServer");
        serverProperties.put("username", "wrong-user-name");
        serverProperties.put("password", "wrong-password");

        // act
        publishAndVerify("default", properties, serverProperties, null, () -> {
            // assert
            givenAuthenticatedAsPublisher()
                    .when().get(childPages())
                    .then().body("results.title", hasItem("Index"));
        });
    }

    private void publish(String pathToContent, Map<String, String> properties) {
        publishAndVerify(pathToContent, properties, () -> {
        });
    }

    private void publishAndVerify(String pathToContent, Map<String, String> pomProperties, Runnable runnable) {
        publishAndVerify(pathToContent, pomProperties, emptyMap(), null, runnable);
    }

    private void publishAndVerify(String pathToContent, Map<String, String> pomProperties, Map<String, String> serverProperties, String encryptedMasterPassword, Runnable runnable) {
        boolean useCommandLineArguments = this.propertiesMode.equals(COMMAND_LINE_ARGUMENTS);

        try {
            File projectDir = extractResourcePath(getClass(), "/" + pathToContent, TEMPORARY_FOLDER.newFolder(), true);
            publishAndVerify(projectDir, pomProperties, serverProperties, encryptedMasterPassword, useCommandLineArguments, runnable);
        } catch (Exception e) {
            throw new IllegalStateException("publishing failed", e);
        }
    }

    private static void publishAndVerify(File projectDir, Map<String, String> mavenProperties, Map<String, String> serverProperties, String encryptedMasterPassword, boolean useCommandLineArguments, Runnable runnable) throws IOException, VerificationException {
        Map<String, String> pomProperties = useCommandLineArguments ? emptyMap() : mavenProperties;

        Path pomPath = projectDir.toPath().resolve("pom.xml");
        Files.write(pomPath, generatePom(pomProperties).getBytes(UTF_8));

        Path settingsPath = projectDir.toPath().resolve("settings.xml");
        Files.write(settingsPath, generateSettings(serverProperties).getBytes(UTF_8));

        Verifier verifier = new Verifier(projectDir.getAbsolutePath());

        if (encryptedMasterPassword != null) {
            Path mavenSettingsDirectoryPath = projectDir.toPath().resolve(".m2");
            Files.createDirectories(mavenSettingsDirectoryPath);

            Path securitySettingsPath = mavenSettingsDirectoryPath.resolve("settings-security.xml");
            Files.write(securitySettingsPath, generateSecuritySettings(encryptedMasterPassword).getBytes(UTF_8));

            verifier.addCliOption("-Duser.home=" + projectDir.getAbsolutePath());
        }

        try {
            if (useCommandLineArguments) {
                mavenProperties.forEach((key, value) -> {
                    if (value.contains("//")) {
                        // maven verifier cli options parsing replaces // with /
                        value = value.replaceAll("//", "////");
                    }

                    if (value.contains(" ")) {
                        value = "'" + value + "'";
                    }

                    verifier.addCliOption("-Dasciidoc-confluence-publisher." + key + "=" + value);
                });
            }

            verifier.addCliOption("-s " + settingsPath.toAbsolutePath().toString());
            verifier.executeGoal("org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-maven-plugin:publish");

            verifier.verifyErrorFreeLog();
        } finally {
            verifier.resetStreams();
            displayMavenLog(verifier);
        }

        runnable.run();
    }

    private static void displayMavenLog(Verifier verifier) throws IOException {
        File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
        Files.readAllLines(logFile.toPath()).forEach((line) -> System.out.println(line));
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
        return "http://localhost:" + confluenceServer.port + "/rest/api/content/" + pageId + "?expand=body.view,history.lastUpdated";
    }

    private static String childPages() {
        return "http://localhost:" + confluenceServer.port + "/rest/api/content/327706/child/page";
    }

    private static String pageIdBy(String title) {
        return givenAuthenticatedAsPublisher()
                .when().get(childPages())
                .then().extract().jsonPath().getString("results.find({it.title == '" + title + "'}).id");
    }

    private static Map<String, String> mandatoryProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("rootConfluenceUrl", "http://localhost:" + confluenceServer.port);
        properties.put("spaceKey", "CPI");
        properties.put("ancestorId", "327706");
        properties.put("username", "confluence-publisher-it");
        properties.put("password", "1234");
        properties.put("asciidocRootFolder", ".");

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

    private static String generateSettings(Map<String, String> properties) {
        return "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "  xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                "  <servers>\n" +
                "    <server>\n" +

                properties.entrySet().stream()
                        .map((property) -> "<" + property.getKey() + ">" + property.getValue() + "</" + property.getKey() + ">")
                        .collect(joining("")) +

                "    </server>\n" +
                "  </servers>\n" +
                "</settings>";
    }

    private static String generateSecuritySettings(String encryptedMasterPassword) {
        return "<settingsSecurity>\n" +
                "  <master>" + encryptedMasterPassword + "</master>\n" +
                "</settingsSecurity>";
    }


    @FunctionalInterface
    private interface PortAwareRunnable {

        void run(int port) throws Exception;

    }

}
