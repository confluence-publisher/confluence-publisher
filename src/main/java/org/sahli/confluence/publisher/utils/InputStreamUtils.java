package org.sahli.confluence.publisher.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public final class InputStreamUtils {

    private InputStreamUtils() {
        throw new UnsupportedOperationException("Utils class cannot be instantiated");
    }

    public static String inputStreamAsString(InputStream is) {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Could not convert InputStream to String ", e);
        }
    }

    public static String fileContent(String filePath) {
        try {
            return inputStreamAsString(new FileInputStream(new File(filePath)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not read file", e);
        }
    }

}
