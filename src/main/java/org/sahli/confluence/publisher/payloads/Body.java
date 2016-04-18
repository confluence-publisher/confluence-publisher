package org.sahli.confluence.publisher.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Body {

    private Storage storage;

    @RuntimeUse
    public Storage getStorage() {
        return this.storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }
}
