package org.sahli.asciidoc.confluence.publisher.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.stream;

public class ArgumentsParser {

    private static final TypeReference<HashMap<String, Object>> typeRef =
            new TypeReference<HashMap<String, Object>>() {
            };
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<Map<String, Object>> optionalJsonArgument(String key, String[] args) {
        return optionalArgument(key, args)
                .map(attributes -> {
                    try {
                        return objectMapper.readValue(attributes, typeRef);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("failed to parse argument '" + key + "' to map due to error", e);
                    }
                });
    }

    public String mandatoryArgument(String key, String[] args) {
        return optionalArgument(key, args)
                .orElseThrow(() -> new IllegalArgumentException("mandatory argument '" + key + "' is missing"));
    }

    public Optional<String> optionalArgument(String key, String[] args) {
        return stream(args)
                .filter((keyAndValue) -> keyAndValue.startsWith(key + "="))
                .map((keyAndValue) -> keyAndValue.substring(keyAndValue.indexOf('=') + 1))
                .filter((value) -> !value.isEmpty())
                .findFirst();
    }
}
