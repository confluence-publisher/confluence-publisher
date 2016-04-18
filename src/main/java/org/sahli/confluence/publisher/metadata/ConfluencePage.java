package org.sahli.confluence.publisher.metadata;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePage {

    private String title;
    private String contentFilePath;
    private String sha256ContentHash;

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentFilePath() {
        return this.contentFilePath;
    }

    public void setContentFilePath(String contentFilePath) {
        this.contentFilePath = contentFilePath;
    }

    public String getSha256ContentHash() {
        return this.sha256ContentHash;
    }

    public void setSha256ContentHash(String sha256ContentHash) {
        this.sha256ContentHash = sha256ContentHash;
    }
}
