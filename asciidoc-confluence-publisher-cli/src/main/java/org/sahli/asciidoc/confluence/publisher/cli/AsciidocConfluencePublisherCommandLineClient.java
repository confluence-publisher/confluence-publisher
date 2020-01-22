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

import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisherListener;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.walkFileTree;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.APPEND_TO_ANCESTOR;

public class AsciidocConfluencePublisherCommandLineClient {

    public static void main(String[] args) throws Exception {
        ArgumentsParser argumentsParser = new ArgumentsParser();
        String rootConfluenceUrl = argumentsParser.mandatoryArgument("rootConfluenceUrl", args);
        boolean skipSslVerification = argumentsParser.optionalBooleanArgument("skipSslVerification", args).orElse(false);
        String username = argumentsParser.mandatoryArgument("username", args);
        String password = argumentsParser.mandatoryArgument("password", args);
        String spaceKey = argumentsParser.mandatoryArgument("spaceKey", args);
        String ancestorId = argumentsParser.mandatoryArgument("ancestorId", args);
        String versionMessage = argumentsParser.optionalArgument("versionMessage", args).orElse(null);
        PublishingStrategy publishingStrategy = PublishingStrategy.valueOf(argumentsParser.optionalArgument("publishingStrategy", args).orElse(APPEND_TO_ANCESTOR.name()));

        Path documentationRootFolder = Paths.get(argumentsParser.mandatoryArgument("asciidocRootFolder", args));
        Path buildFolder = createTempDirectory("confluence-publisher");

        Charset sourceEncoding = Charset.forName(argumentsParser.optionalArgument("sourceEncoding", args).orElse("UTF-8"));
        String prefix = argumentsParser.optionalArgument("pageTitlePrefix", args).orElse(null);
        String suffix = argumentsParser.optionalArgument("pageTitleSuffix", args).orElse(null);
        Map<String, Object> attributes = argumentsParser.optionalJsonArgument("attributes", args).orElseGet(Collections::emptyMap);

        String proxyScheme = argumentsParser.optionalArgument("proxyScheme", args).orElse(null);
        String proxyHost = argumentsParser.optionalArgument("proxyHost", args).orElse(null);
        Integer proxyPort = argumentsParser.optionalArgument("proxyPort", args).map((value) -> parseInt(value)).orElse(null);
        String proxyUsername = argumentsParser.optionalArgument("proxyUsername", args).orElse(null);
        String proxyPassword = argumentsParser.optionalArgument("proxyPassword", args).orElse(null);

        try {
            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, sourceEncoding);
            PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(prefix, suffix);

            AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter(spaceKey, ancestorId);
            ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(asciidocPagesStructureProvider, pageTitlePostProcessor, buildFolder, attributes);

            ProxyConfiguration proxyConfiguration = new ProxyConfiguration(proxyScheme, proxyHost, proxyPort, proxyUsername, proxyPassword);

            ConfluenceRestClient confluenceClient = new ConfluenceRestClient(rootConfluenceUrl, proxyConfiguration, skipSslVerification, username, password);
            ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, publishingStrategy, confluenceClient, new SystemOutLoggingConfluencePublisherListener(), versionMessage);
            confluencePublisher.publish();
        } finally {
            deleteDirectory(buildFolder);
        }
    }

    private static void deleteDirectory(Path buildFolder) throws IOException {
        walkFileTree(buildFolder, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                delete(path);

                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                delete(path);

                return CONTINUE;
            }

        });
    }


    private static class SystemOutLoggingConfluencePublisherListener implements ConfluencePublisherListener {

        @Override
        public void pageAdded(ConfluencePage addedPage) {
            System.out.println("Added page '" + addedPage.getTitle() + "' (id " + addedPage.getContentId() + ")");
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
            System.out.println("Updated page '" + updatedPage.getTitle() + "' (id " + updatedPage.getContentId() + ", version " + existingPage.getVersion() + " -> " + updatedPage.getVersion() + ")");
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
            System.out.println("Deleted page '" + deletedPage.getTitle() + "' (id " + deletedPage.getContentId() + ")");
        }

        @Override
        public void attachmentAdded(String attachmentFileName, String contentId) {
            System.out.println("Added attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentUpdated(String attachmentFileName, String contentId) {
            System.out.println("Updated attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void attachmentDeleted(String attachmentFileName, String contentId) {
            System.out.println("Deleted attachment '" + attachmentFileName + "' (page id " + contentId + ")");
        }

        @Override
        public void publishCompleted() {
            System.out.println("Documentation successfully published to Confluence");
        }

    }

}
