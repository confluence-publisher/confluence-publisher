package org.sahli.confluence.publisher.converter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public class IoUtils {


    public static String extension(Path path) {
        return extension(path.getFileName().toString()).get();
    }

    public static Optional<String> extension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    public static Path removeExtension(Path path) {
        return Paths.get(path.toString().substring(0, path.toString().lastIndexOf('.')));
    }

    public static void createDirectories(Path directoryPath) {
        try {
            Files.createDirectories(directoryPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory '" + directoryPath.toAbsolutePath() + "'", e);
        }
    }


    public static Path replaceExtension(Path path, String existingExtension, String newExtension) {
        return Paths.get(path.toString().replace(existingExtension, newExtension));
    }


    public void toFile(StringBuilder buffer, String fileName) throws IOException {

        OutputStreamWriter bwr = new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8);

        toFile(buffer.toString(), bwr);
    }

    public static void toFile(String buffer, File file) throws IOException {

        OutputStreamWriter bwr = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);

        toFile(buffer, bwr);
    }

    private static void toFile(String buffer, OutputStreamWriter bwr) throws IOException {
        //write contents of StringBuilder to a file
        bwr.write(buffer);

        //flush the stream
        bwr.flush();

        //close the stream
        bwr.close();
    }

    public static String readIntoString(InputStream input, Charset charset) {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, charset))) {
                return buffer.lines().collect(joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read file content", e);
        }
    }
}
