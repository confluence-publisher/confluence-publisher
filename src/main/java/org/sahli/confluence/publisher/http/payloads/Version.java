package org.sahli.confluence.publisher.http.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class Version {

    private int number;

    @RuntimeUse
    public int getNumber() {
        return this.number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
