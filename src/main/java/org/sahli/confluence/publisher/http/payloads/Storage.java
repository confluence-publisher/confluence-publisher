package org.sahli.confluence.publisher.http.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Storage {

    private String value;

    @RuntimeUse
    public String getRepresentation() {
        return "storage";
    }

    @RuntimeUse
    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
