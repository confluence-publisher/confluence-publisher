package org.sahli.confluence.publisher.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Ancestor {

    private String id;

    @RuntimeUse
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
