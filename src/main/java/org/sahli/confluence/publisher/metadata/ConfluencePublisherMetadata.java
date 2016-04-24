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
    private String ancestorId;
    private List<ConfluencePage> pages = new ArrayList<>();

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

    public List<ConfluencePage> getPages() {
        return this.pages;
    }

    @RuntimeUse
    public void setPages(List<ConfluencePage> pages) {
        this.pages = pages;
    }

}
