package org.sahli.asciidoc.confluence.publisher.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.stream;

class ArgumentsParser {

    private final TypeReference<Map<String, Object>> typeReference;
    private final ObjectMapper objectMapper;

    ArgumentsParser() {
        this.typeReference = mapOfStringToObjectTypeReference();
        this.objectMapper = new ObjectMapper();
    }

    Optional<Map<String, Object>> optionalJsonArgument(String key, String[] args) {
        return optionalArgument(key, args)
                .map(attributes -> {
                    try {
                        return this.objectMapper.readValue(attributes, this.typeReference);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("failed to parse argument '" + key + "' to map due to error", e);
                    }
                });
    }

    String mandatoryArgument(String key, String[] args) {
        return optionalArgument(key, args)
                .orElseThrow(() -> new IllegalArgumentException("mandatory argument '" + key + "' is missing"));
    }

    Optional<Boolean> optionalBooleanArgument(String key, String[] args) {
        Optional<String> explicitValue = optionalArgument(key, args);
        if (explicitValue.isPresent()) {
            return explicitValue.map((value) -> value.equalsIgnoreCase("true"));
        }

        return stream(args)
                .filter((keyAndValue) -> keyAndValue.equals(key))
                .findFirst()
                .map((keyAndValue) -> true);
    }

    Optional<String> optionalArgument(String key, String[] args) {
        return stream(args)
                .filter((keyAndValue) -> keyAndValue.startsWith(key + "="))
                .map((keyAndValue) -> keyAndValue.substring(keyAndValue.indexOf('=') + 1))
                .filter((value) -> !value.isEmpty())
                .findFirst();
    }

    private static TypeReference<Map<String, Object>> mapOfStringToObjectTypeReference() {
        return new TypeReference<Map<String, Object>>() {
        };
    }

}
