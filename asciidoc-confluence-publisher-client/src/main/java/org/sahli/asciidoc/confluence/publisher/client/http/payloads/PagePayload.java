/*
 * Copyright 2016 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client.http.payloads;

import org.sahli.asciidoc.confluence.publisher.client.support.RuntimeUse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alain Sahli
 * @since 1.0
 */

public class PagePayload {

    private String title;
    private Space space;
    private Body body;
    private final List<Ancestor> ancestors = new ArrayList<>();
    private Version version;

    public void addAncestor(Ancestor ancestor) {
        this.ancestors.add(ancestor);
    }

    @RuntimeUse
    public String getType() {
        return "page";
    }

    @RuntimeUse
    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @RuntimeUse
    public Space getSpace() {
        return this.space;
    }

    public void setSpace(Space space) {
        this.space = space;
    }

    @RuntimeUse
    public Body getBody() {
        return this.body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    @RuntimeUse
    public List<Ancestor> getAncestors() {
        return this.ancestors;
    }

    @RuntimeUse
    public Version getVersion() {
        return this.version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }
}
