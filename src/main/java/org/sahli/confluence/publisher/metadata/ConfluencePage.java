package org.sahli.confluence.publisher.metadata;

import org.sahli.confluence.publisher.support.RuntimeUse;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePage {

    private String title;
    private String contentFilePath;
    private String sha256ContentHash;
    private List<ConfluencePage> children;
    private List<String> attachments;

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

    public String getSha256ContentHash() {
        return this.sha256ContentHash;
    }

    @RuntimeUse
    public void setSha256ContentHash(String sha256ContentHash) {
        this.sha256ContentHash = sha256ContentHash;
    }

    public List<ConfluencePage> getChildren() {
        if (this.children == null) {
            return emptyList();
        } else {
            return this.children;
        }
    }

    @RuntimeUse
    public void setChildren(List<ConfluencePage> children) {
        this.children = children;
    }

    public List<String> getAttachments() {
        if (this.attachments == null) {
            return emptyList();
        } else {
            return this.attachments;
        }
    }

    @RuntimeUse
    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }
}
