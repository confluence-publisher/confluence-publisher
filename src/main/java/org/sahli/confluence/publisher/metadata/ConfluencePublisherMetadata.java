package org.sahli.confluence.publisher.metadata;

import org.sahli.confluence.publisher.support.RuntimeUse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisherMetadata {

    private String spaceKey;
    private String parentContentId;
    private List<ConfluencePage> pages = new ArrayList<>();

    public String getSpaceKey() {
        return this.spaceKey;
    }

    @RuntimeUse
    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getParentContentId() {
        return this.parentContentId;
    }

    @RuntimeUse
    public void setParentContentId(String parentContentId) {
        this.parentContentId = parentContentId;
    }

    public List<ConfluencePage> getPages() {
        return this.pages;
    }

    @RuntimeUse
    public void setPages(List<ConfluencePage> pages) {
        this.pages = pages;
    }

}
