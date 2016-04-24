package org.sahli.confluence.publisher.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.io.IOException;

/**
 * @author Alain Sahli
 * @since 1.1
 */
public class SameJsonAsMatcher extends BaseMatcher<String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode expectedJson;

    private SameJsonAsMatcher(String expectedJson) {
        try {
            this.expectedJson = this.objectMapper.readTree(expectedJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean matches(Object actualValue) {
        if (actualValue instanceof String) {
            String actualStringValue = (String) actualValue;
            try {
                JsonNode actualJson = this.objectMapper.readTree(actualStringValue);
                return this.expectedJson.equals(actualJson);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(this.expectedJson);
    }

    public static SameJsonAsMatcher isSameJsonAs(String expectedJson) {
        return new SameJsonAsMatcher(expectedJson);
    }

}
