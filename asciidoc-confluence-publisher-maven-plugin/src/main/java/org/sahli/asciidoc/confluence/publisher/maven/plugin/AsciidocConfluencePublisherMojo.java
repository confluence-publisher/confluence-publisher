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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.sahli.asciidoc.confluence.publisher.maven.plugin.AsciidocConfluenceConverter.convertAndBuildConfluencePages;

/**
 * @author Alain Sahli
 * @since 1.0
 */
@Mojo(name = "publish")
public class AsciidocConfluencePublisherMojo extends AbstractMojo {

    private static final String TEMPLATES_CLASSPATH_PATTERN = "org/sahli/asciidoc/confluence/publisher/converter/templates/*";

    @Parameter(defaultValue = "${project.build.directory}/confluence-publisher")
    private File generatedDocOutputPath;

    @Parameter(defaultValue = "${project.build.directory}/asciidoc2confluence-templates", readonly = true)
    private File asciidocConfluenceTemplates;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
    private Map<String, Artifact> pluginArtifactMap;

    @Parameter
    private File asciidocRootFolder;

    @Parameter
    private String rootConfluenceUrl;

    @Parameter(required = true)
    private String spaceKey;

    @Parameter
    private String ancestorId;

    @Parameter
    private String username;

    @Parameter
    private String password;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            extractTemplatesFromJar();
            this.generatedDocOutputPath.mkdirs();

            ConfluencePublisherMetadata confluencePublisherMetadata = convertAndBuildConfluencePages(this.asciidocRootFolder.getAbsolutePath(),
                    this.generatedDocOutputPath.getAbsolutePath(), this.asciidocConfluenceTemplates.getAbsolutePath(), this.spaceKey, this.ancestorId);

            publish(confluencePublisherMetadata);
        } catch (Exception e) {
            getLog().error("Publishing to Confluence failed: " + e.getMessage());
            throw new MojoExecutionException("Publishing to Confluence failed", e);
        }
    }

    private void publish(ConfluencePublisherMetadata confluencePublisherMetadata) {
        ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(this.rootConfluenceUrl, httpClient(), this.username, this.password);
        ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, confluenceRestClient, this.generatedDocOutputPath.getAbsolutePath());
        confluencePublisher.publish();
    }

    private void extractTemplatesFromJar() {
        try {
            createTemplatesTargetFolder();
            copyTemplatesToTarget(templateResources());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Resource> templateResources() throws IOException {
        Artifact artifact = this.pluginArtifactMap.get("org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-converter");
        URLClassLoader templateClassLoader = new URLClassLoader(new URL[]{artifact.getFile().toURI().toURL()});
        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver(new DefaultResourceLoader(templateClassLoader));

        return asList(pathMatchingResourcePatternResolver.getResources(TEMPLATES_CLASSPATH_PATTERN));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createTemplatesTargetFolder() {
        this.asciidocConfluenceTemplates.mkdir();
    }

    private void copyTemplatesToTarget(List<Resource> resources) {
        resources.forEach(templateResource -> {
            try {
                copyInputStreamToFile(templateResource.getInputStream(), new File(this.asciidocConfluenceTemplates, templateResource.getFilename()));
            } catch (IOException e) {
                throw new RuntimeException("Could not write template to target file", e);
            }
        });
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
