package org.sahli.confluence.publisher.http.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Space {

    private String key;

    @RuntimeUse
    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
