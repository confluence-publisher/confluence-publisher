package org.sahli.confluence.publisher.utils;

public final class AssertUtils {

    private AssertUtils() {
        throw new UnsupportedOperationException("Utils class cannot be instantiated");
    }

    public static void assertMandatoryParameter(boolean assertion, String parameterName) {
        if (!assertion) {
            throw new IllegalArgumentException(parameterName + " must be set");
        }
    }
}
