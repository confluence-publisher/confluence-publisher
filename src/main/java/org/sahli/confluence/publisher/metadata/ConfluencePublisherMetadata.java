package org.sahli.confluence.publisher.metadata;

import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisherMetadata {

    private String spaceKey;
    private String parentContentId;
    private List<ConfluencePage> pages;

    public void validate() {
        if (isNotBlank(this.spaceKey) && isNotBlank(this.parentContentId)) {
            throw new IllegalStateException("spaceKey and parentContentId cannot both be set");
        }
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getParentContentId() {
        return parentContentId;
    }

    public void setParentContentId(String parentContentId) {
        this.parentContentId = parentContentId;
    }

    public List<ConfluencePage> getPages() {
        return pages;
    }

    public void setPages(List<ConfluencePage> pages) {
        this.pages = pages;
    }
}
