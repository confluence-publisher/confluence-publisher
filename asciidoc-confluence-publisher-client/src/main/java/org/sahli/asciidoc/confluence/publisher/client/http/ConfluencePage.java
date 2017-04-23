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

package org.sahli.asciidoc.confluence.publisher.client.http;

/**
 * @author Alain Sahli
 */
public class ConfluencePage {

    private final String contentId;
    private final String title;
    private final String content;
    private final int version;

    public ConfluencePage(String contentId, String title, int version) {
        this(contentId, title, null, version);
    }

    public ConfluencePage(String contentId, String title, String content, int version) {
        this.contentId = contentId;
        this.title = title;
        this.content = content;
        this.version = version;
    }

    public String getContentId() {
        return this.contentId;
    }

    public String getTitle() {
        return this.title;
    }

    public String getContent() {
        return this.content;
    }

    public int getVersion() {
        return this.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfluencePage that = (ConfluencePage) o;

        if (this.version != that.version) return false;
        if (!this.contentId.equals(that.contentId)) return false;
        //noinspection SimplifiableIfStatement
        if (!this.title.equals(that.title)) return false;
        return this.content != null ? this.content.equals(that.content) : that.content == null;

    }

    @Override
    public int hashCode() {
        int result = this.contentId.hashCode();
        result = 31 * result + this.title.hashCode();
        result = 31 * result + (this.content != null ? this.content.hashCode() : 0);
        result = 31 * result + this.version;
        return result;
    }

    @Override
    public String toString() {
        return "ConfluencePage{" +
                "contentId='" + this.contentId + '\'' +
                ", title='" + this.title + '\'' +
                ", content='" + this.content + '\'' +
                ", version=" + this.version +
                '}';
    }

}
