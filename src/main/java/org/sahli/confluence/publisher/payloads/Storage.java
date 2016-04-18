package org.sahli.confluence.publisher.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Storage {

    private String value;
    private String representation;

    @RuntimeUse
    public String getRepresentation() {
        return this.representation;
    }

    public void setRepresentation(String representation) {
        this.representation = representation;
    }

    @RuntimeUse
    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
