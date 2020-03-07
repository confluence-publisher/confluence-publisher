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

package org.sahli.asciidoc.confluence.publisher.client.metadata;

import org.sahli.asciidoc.confluence.publisher.client.support.RuntimeUse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * @author Alain Sahli
 */
public class ConfluencePageMetadata {

    private String title;
    private String contentFilePath;
    private List<ConfluencePageMetadata> children = new ArrayList<>();
    private Map<String, String> attachments = new HashMap<>();
    private List<String> labels = new ArrayList<>();

    public String getTitle() {
        return this.title;
    }

    @RuntimeUse
    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentFilePath() {
        return this.contentFilePath;
    }

    @RuntimeUse
    public void setContentFilePath(String contentFilePath) {
        this.contentFilePath = contentFilePath;
    }

    public List<ConfluencePageMetadata> getChildren() {
        if (this.children == null) {
            return emptyList();
        } else {
            return this.children;
        }
    }

    @RuntimeUse
    public void setChildren(List<ConfluencePageMetadata> children) {
        this.children = children;
    }

    public Map<String, String> getAttachments() {
        return this.attachments;
    }

    @RuntimeUse
    public void setAttachments(Map<String, String> attachments) {
        this.attachments = attachments;
    }

    public List<String> getLabels() {
        return this.labels;
    }

    @RuntimeUse
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

}
