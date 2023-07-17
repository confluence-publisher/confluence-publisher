package org.sahli.asciidoc.confluence.publisher.converter;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class Helper {

    public final static Map<String, Object> RICK_MORTY = ImmutableMap.of(
            "name", "Rick and Morty",
            "genre", "science fiction"
    );

    public static final String SPACE_KEY = "~personalSpace";
    public static final String ANCESTOR_ID = "1234";
}
