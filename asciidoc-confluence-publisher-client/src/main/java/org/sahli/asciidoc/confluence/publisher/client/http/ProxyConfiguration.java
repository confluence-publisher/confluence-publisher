/*
 * Copyright 2025 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client.http;

public class ProxyConfiguration {

    private final String proxyScheme;
    private final String proxyHost;
    private final Integer proxyPort;
    private final String proxyUsername;
    private final String proxyPassword;

    public ProxyConfiguration(String proxyScheme, String proxyHost, Integer proxyPort, String proxyUsername, String proxyPassword) {
        this.proxyScheme = proxyScheme;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
    }

    public String proxyScheme() {
        return this.proxyScheme;
    }

    public String proxyHost() {
        return this.proxyHost;
    }

    public Integer proxyPort() {
        return this.proxyPort;
    }

    public String proxyUsername() {
        return this.proxyUsername;
    }

    public String proxyPassword() {
        return this.proxyPassword;
    }

}
