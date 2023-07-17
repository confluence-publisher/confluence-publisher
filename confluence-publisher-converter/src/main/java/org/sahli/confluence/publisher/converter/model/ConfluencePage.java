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

package org.sahli.confluence.publisher.converter.model;

import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePage {

    private final String pageTitle;
    private final String htmlContent;
    private final Map<String, String> attachments;
    private final List<String> keywords;

    public ConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments, List<String> keywords) {
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.attachments = attachments;
        this.keywords = keywords;
    }

    public String content() {
        return this.htmlContent;
    }

    public String pageTitle() {
        return this.pageTitle;
    }

    public Map<String, String> attachments() {
        return unmodifiableMap(this.attachments);
    }

    public List<String> keywords() {
        return unmodifiableList(this.keywords);
    }



}
