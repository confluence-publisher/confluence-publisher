package org.sahli.confluence.publisher.metadata;

import java.util.List;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisherMetadata {

    private String spaceKey;
    private String parentContentId;
    private List<ConfluencePage> pages;

    public String getSpaceKey() {
        return this.spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getParentContentId() {
        return this.parentContentId;
    }

    public void setParentContentId(String parentContentId) {
        this.parentContentId = parentContentId;
    }

    public List<ConfluencePage> getPages() {
        return this.pages;
    }

    public void setPages(List<ConfluencePage> pages) {
        this.pages = pages;
    }
}
