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
public class ConfluenceAttachment {

    private final String id;
    private final String title;
    private final String relativeDownloadLink;
    private final int version;

    public ConfluenceAttachment(String id, String title, String relativeDownloadLink, int version) {
        this.id = id;
        this.title = title;
        this.relativeDownloadLink = relativeDownloadLink;
        this.version = version;
    }

    public String getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public String getRelativeDownloadLink() {
        return this.relativeDownloadLink;
    }

    public int getVersion() {
        return this.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfluenceAttachment that = (ConfluenceAttachment) o;

        if (this.version != that.version) return false;
        if (!this.id.equals(that.id)) return false;
        //noinspection SimplifiableIfStatement
        if (!this.title.equals(that.title)) return false;
        return this.relativeDownloadLink.equals(that.relativeDownloadLink);

    }

    @Override
    public int hashCode() {
        int result = this.id.hashCode();
        result = 31 * result + this.title.hashCode();
        result = 31 * result + this.relativeDownloadLink.hashCode();
        result = 31 * result + this.version;
        return result;
    }

    @Override
    public String toString() {
        return "ConfluenceAttachment{" +
                "id='" + this.id + '\'' +
                ", title='" + this.title + '\'' +
                ", relativeDownloadLink='" + this.relativeDownloadLink + '\'' +
                ", version=" + this.version +
                '}';
    }

}
