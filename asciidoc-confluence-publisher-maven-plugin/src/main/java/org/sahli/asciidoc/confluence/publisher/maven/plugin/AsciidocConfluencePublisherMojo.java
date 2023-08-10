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

package org.sahli.asciidoc.confluence.publisher.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisherListener;
import org.sahli.asciidoc.confluence.publisher.client.OrphanRemovalStrategy;
import org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient.ProxyConfiguration;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.FolderBasedAsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.PageTitlePostProcessor;
import org.sahli.asciidoc.confluence.publisher.converter.PrefixAndSuffixPageTitlePostProcessor;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
@Mojo(name = "publish")
public class AsciidocConfluencePublisherMojo extends AbstractMojo {

    static final String PREFIX = "asciidoc-confluence-publisher.";

    @Parameter(defaultValue = "${project.build.directory}/asciidoc-confluence-publisher", readonly = true)
    private File confluencePublisherBuildFolder;

    @Parameter(property = PREFIX + "asciidocRootFolder")
    private File asciidocRootFolder;

    @Parameter(property = PREFIX + "sourceEncoding", defaultValue = "UTF-8")
    private String sourceEncoding;

    @Parameter(property = PREFIX + "rootConfluenceUrl")
    private String rootConfluenceUrl;

    @Parameter(property = PREFIX + "skipSslVerification", defaultValue = "false")
    private boolean skipSslVerification;

    @Parameter(property = PREFIX + "enableHttpClientSystemProperties", defaultValue = "false")
    private boolean enableHttpClientSystemProperties;

    @Parameter(property = PREFIX + "maxRequestsPerSecond")
    private Double maxRequestsPerSecond;

    @Parameter(property = PREFIX + "connectionTimeToLive")
    private Integer connectionTimeToLive;

    @Parameter(property = PREFIX + "spaceKey", required = true)
    private String spaceKey;

    @Parameter(property = PREFIX + "ancestorId", required = true)
    private String ancestorId;

    @Parameter(property = PREFIX + "publishingStrategy", defaultValue = "APPEND_TO_ANCESTOR")
    private PublishingStrategy publishingStrategy;

    @Parameter(property = PREFIX + "orphanRemovalStrategy", defaultValue = "REMOVE_ORPHANS")
    private OrphanRemovalStrategy orphanRemovalStrategy;

    @Parameter(property = PREFIX + "versionMessage")
    private String versionMessage;

    @Parameter(property = PREFIX + "username")
    private String username;

    @Parameter(property = PREFIX + "password")
    private String password;

    @Parameter(readonly = true, property = "settings")
    protected Settings mavenSettings;

    @Parameter(property = PREFIX + "serverId")
    private String serverId;

    @Parameter(property = PREFIX + "pageTitlePrefix")
    private String pageTitlePrefix;

    @Parameter(property = PREFIX + "pageTitleSuffix")
    private String pageTitleSuffix;

    @Parameter(property = PREFIX + "skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = PREFIX + "convertOnly", defaultValue = "false")
    private boolean convertOnly;

    @Parameter(property = PREFIX + "proxyScheme")
    private String proxyScheme;

    @Parameter(property = PREFIX + "proxyHost")
    private String proxyHost;

    @Parameter(property = PREFIX + "proxyPort")
    private Integer proxyPort;

    @Parameter(property = PREFIX + "proxyUsername")
    private String proxyUsername;

    @Parameter(property = PREFIX + "proxyPassword")
    private String proxyPassword;

    @Parameter(property = PREFIX + "notifyWatchers")
    private boolean notifyWatchers;

    @Parameter
    private Map<String, Object> attributes;

    @Component(role = SecDispatcher.class, hint = "default")
    private DefaultSecDispatcher securityDispatcher;

    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            getLog().info("Publishing to Confluence skipped ('skip' is enabled)");
            return;
        }

        try {
            PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(this.pageTitlePrefix, this.pageTitleSuffix);

            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(this.asciidocRootFolder.toPath(), Charset.forName(this.sourceEncoding));

            AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter(this.spaceKey, this.ancestorId);
            Map<String, Object> attributes = this.attributes != null ? this.attributes : emptyMap();
            ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(asciidocPagesStructureProvider, pageTitlePostProcessor, this.confluencePublisherBuildFolder.toPath(), attributes);

            if ((this.password == null)) {
                applyUsernameAndPasswordFromSettings();
            }

            if (this.convertOnly) {
                getLog().info("Publishing to Confluence skipped ('convert only' is enabled)");
            } else {
                ProxyConfiguration proxyConfiguration = new ProxyConfiguration(this.proxyScheme, this.proxyHost, this.proxyPort, this.proxyUsername, this.proxyPassword);
                ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(this.rootConfluenceUrl, proxyConfiguration, this.skipSslVerification, this.enableHttpClientSystemProperties, this.maxRequestsPerSecond, this.connectionTimeToLive, this.username, this.password);
                ConfluencePublisherListener confluencePublisherListener = new LoggingConfluencePublisherListener(getLog());

                ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, this.publishingStrategy, this.orphanRemovalStrategy, confluenceRestClient, confluencePublisherListener, this.versionMessage, this.notifyWatchers);
                confluencePublisher.publish();
            }
        } catch (Exception e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Publishing to Confluence failed", e);
            } else {
                getLog().error("Publishing to Confluence failed: " + e.getMessage());
            }

            throw new MojoExecutionException("Publishing to Confluence failed", e);
        }
    }

    private void applyUsernameAndPasswordFromSettings() throws MojoExecutionException {
        if (this.serverId == null) {
            throw new MojoExecutionException("'serverId' must be set, if username/password are not provided via configuration properties");
        }

        Server server = this.mavenSettings.getServer(this.serverId);

        if (server == null) {
            throw new MojoExecutionException("server with id '" + this.serverId + "' not found in settings.xml");
        }

        if (this.username == null) {
            if (server.getUsername() == null) {
                throw new MojoExecutionException("'username' neither defined by server '" + this.serverId + "' nor provided via configuration properties");
            }

            this.username = server.getUsername();
        }

        try {
            if (this.password == null) {
                if (server.getPassword() == null) {
                    throw new MojoExecutionException("'password' neither defined by server '" + this.serverId + "' nor provided via configuration properties");
                }

                this.securityDispatcher.setConfigurationFile(System.getProperty("user.home") + "/.m2/settings-security.xml");
                this.password = this.securityDispatcher.decrypt(server.getPassword());
            }
        } catch (SecDispatcherException ex) {
            throw new MojoExecutionException(ex.getMessage());
        }
    }


    private static class LoggingConfluencePublisherListener implements ConfluencePublisherListener {

        private Log log;

        LoggingConfluencePublisherListener(Log log) {
            this.log = log;
        }

        @Override
        public void pageAdded(ConfluencePage addedPage) {
            this.log.info("Added page '" + addedPage.getTitle() + "' (id " + addedPage.getContentId() + ")");
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
            this.log.info("Updated page '" + updatedPage.getTitle() + "' (id " + updatedPage.getContentId() + ", version " + existingPage.getVersion() + " -> " + updatedPage.getVersion() + ")");
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
            this.log.info("Deleted page '" + deletedPage.getTitle() + "' (id " + deletedPage.getContentId() + ")");
        }

        @Override
        public void attachmentAdded(String attachmentFileName, String contentId) {
            this.log.info("Added attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentUpdated(String attachmentFileName, String contentId) {
            this.log.info("Updated attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentDeleted(String attachmentFileName, String contentId) {
            this.log.info("Deleted attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void publishCompleted() {
        }

    }

}
