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

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.FolderBasedAsciidocPagesStructureProvider;

import java.io.File;

import static org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter.convertAndBuildConfluencePages;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
@Mojo(name = "publish")
public class AsciidocConfluencePublisherMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/asciidoc-confluence-publisher", readonly = true)
    private File confluencePublisherBuildFolder;

    @Parameter
    private File asciidocRootFolder;

    @Parameter
    private String rootConfluenceUrl;

    @Parameter(required = true)
    private String spaceKey;

    @Parameter(required = true)
    private String ancestorId;

    @Parameter
    private String username;

    @Parameter
    private String password;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(this.asciidocRootFolder.toPath());
            ConfluencePublisherMetadata confluencePublisherMetadata = convertAndBuildConfluencePages(this.spaceKey, this.ancestorId, this.confluencePublisherBuildFolder.toPath(), asciidocPagesStructureProvider);

            publish(confluencePublisherMetadata);
        } catch (Exception e) {
            getLog().error("Publishing to Confluence failed: " + e.getMessage());
            throw new MojoExecutionException("Publishing to Confluence failed", e);
        }
    }

    private void publish(ConfluencePublisherMetadata confluencePublisherMetadata) {
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(this.rootConfluenceUrl, httpClient(), this.username, this.password);
        ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, confluenceRestClient);
        confluencePublisher.publish();
    }

    private static CloseableHttpClient httpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(20 * 1000)
                .setConnectTimeout(20 * 1000)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

}
