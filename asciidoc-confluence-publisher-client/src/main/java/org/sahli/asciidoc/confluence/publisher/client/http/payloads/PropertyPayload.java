package org.sahli.asciidoc.confluence.publisher.client.http.payloads;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 * @since 1.0
 */
public class PropertyPayload {

    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
