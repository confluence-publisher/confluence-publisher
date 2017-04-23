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
import java.util.List;

/**
 * @author Alain Sahli
 */
public class ConfluencePublisherMetadata {

    private String spaceKey;
    private String ancestorId;
    private List<ConfluencePageMetadata> pages = new ArrayList<>();

    public String getSpaceKey() {
        return this.spaceKey;
    }

    @RuntimeUse
    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getAncestorId() {
        return this.ancestorId;
    }

    @RuntimeUse
    public void setAncestorId(String ancestorId) {
        this.ancestorId = ancestorId;
    }

    public List<ConfluencePageMetadata> getPages() {
        return this.pages;
    }

    @RuntimeUse
    public void setPages(List<ConfluencePageMetadata> pages) {
        this.pages = pages;
    }

}
