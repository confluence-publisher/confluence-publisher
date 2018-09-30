/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.converter;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Preprocessor;
import org.asciidoctor.extension.PreprocessorReader;
import org.asciidoctor.internal.JRubyRuntimeContext;
import org.jruby.Ruby;
import org.jruby.java.proxies.RubyObjectHolderProxy;
import org.jruby.runtime.builtin.IRubyObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readAllBytes;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.asciidoctor.Asciidoctor.STRUCTURE_MAX_LEVEL;
import static org.asciidoctor.OptionsBuilder.options;
import static org.asciidoctor.SafeMode.UNSAFE;

public class HeadingBasedAsciidocPagesStructureProvider implements AsciidocPagesStructureProvider {

    private final Path rootFile;
    private final Charset sourceEncoding;
    private final Path buildFolder;

    public HeadingBasedAsciidocPagesStructureProvider(Path rootFile, Charset sourceEncoding, Path buildFolder) {
        this.rootFile = rootFile;
        this.sourceEncoding = sourceEncoding;
        this.buildFolder = buildFolder;
    }

    @Override
    public AsciidocPagesStructure structure() {
        try {
            Path headingBasedStructureTargetFolder = this.buildFolder.resolve("heading-based-structure").toAbsolutePath();
            createDirectories(headingBasedStructureTargetFolder);

            AsciiDocument asciiDocument = new AsciiDocument(this.rootFile, this.sourceEncoding, headingBasedStructureTargetFolder);
            List<Section> sections = asciiDocument.sections();
            List<AsciidocPage> asciidocPages = buildPages(new ArrayList<>(sections), headingBasedStructureTargetFolder, this.sourceEncoding);

            return () -> asciidocPages;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create asciidoc pages structure", e);
        }
    }

    private List<AsciidocPage> buildPages(List<Section> sections, Path targetFolder, Charset sourceEncoding) {
        if (sections.isEmpty()) {
            return emptyList();
        }

        List<AsciidocPage> pages = new ArrayList<>();

        Section currentSection = sections.get(0);
        DefaultAsciidocPage page = new DefaultAsciidocPage(targetFolder.resolve(randomUUID().toString() + ".adoc"), this.rootFile.getParent());
        writeSectionContentTo(page.path(), currentSection, sourceEncoding);
        pages.add(page);

        sections.remove(0);

        while (sections.size() > 0) {
            Section nextSection = sections.get(0);

            if (nextSection.level() == currentSection.level()) {
                page = new DefaultAsciidocPage(targetFolder.resolve(randomUUID().toString() + ".adoc"), this.rootFile.getParent());
                writeSectionContentTo(page.path(), nextSection, sourceEncoding);
                pages.add(page);

                sections.remove(0);
            } else if (nextSection.level() > currentSection.level()) {
                List<AsciidocPage> childPages = buildPages(sections, targetFolder, sourceEncoding);
                page.addChildren(childPages);
            } else {
                return pages;
            }
        }

        return pages;
    }

    private static void writeSectionContentTo(Path path, Section section, Charset sourceEncoding) {
        try {
            Files.write(path, pageContent(section).getBytes(sourceEncoding));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write content to path '" + path + "'", e);
        }
    }

    private static String pageContent(Section section) {
        return ":title: " + section.title() + "\n" +
                ":leveloffset: -" + section.level() + "\n" +
                section.content() + "\n\n" + "" +
                ":leveloffset!:";
    }

    @Override
    public Charset sourceEncoding() {
        return this.sourceEncoding;
    }


    static class AsciiDocument {

        private final Path rootFilePath;
        private final Charset sourceEncoding;
        private final Path targetFolder;
        private final List<SectionStart> sectionStarts;

        AsciiDocument(Path rootFilePath, Charset sourceEncoding, Path targetFolder) {
            this.rootFilePath = rootFilePath;
            this.sourceEncoding = sourceEncoding;
            this.targetFolder = targetFolder;
            this.sectionStarts = new ArrayList<>();
        }

        List<Section> sections() {
            String mergedContent = getMergedContent(this.rootFilePath, this.sourceEncoding);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("imagesoutdir", this.targetFolder.toString());
            attributes.put("outdir", this.targetFolder.toString());

            Options options = options()
                    .safe(UNSAFE)
                    .baseDir(this.rootFilePath.getParent().toFile())
                    .option("sourcemap", true)
                    .option(STRUCTURE_MAX_LEVEL, 999)
                    .attributes(attributes)
                    .get();

            Document document = loadDocument(mergedContent, options.map());
            visitBlocks(document.blocks());

            return this.sectionStarts.stream()
                    .map((sectionStart) -> {
                        SectionStart nextSectionStart = optionalNextSectionStart(sectionStart);

                        int startLineNumber = sectionStart.lineNumber();
                        int endLineNumber = endLineNumber(nextSectionStart);

                        String sectionContent = getSectionContent(mergedContent, startLineNumber, endLineNumber);

                        return new Section(sectionStart.title(), sectionContent, sectionStart.level());
                    })
                    .collect(toList());
        }

        private Document loadDocument(String content, Map<String, Object> options) {
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            asciidoctor.unregisterAllExtensions();
            asciidoctor.requireLibrary("asciidoctor-diagram");

            Ruby rubyRuntime = JRubyRuntimeContext.get();
            String currentDirectory = rubyRuntime.getCurrentDirectory();

            try {
                rubyRuntime.setCurrentDirectory(options.get("base_dir").toString());

                return asciidoctor.load(content, options);
            } finally {
                rubyRuntime.setCurrentDirectory(currentDirectory);
            }
        }

        private static String getSectionContent(String mergedContent, int startLineNumber, int endLineNumber) {
            if (endLineNumber != -1) {
                return lines(mergedContent)
                        .skip(startLineNumber)
                        .limit(endLineNumber - startLineNumber)
                        .collect(joining("\n"));
            } else {
                return lines(mergedContent)
                        .skip(startLineNumber - 1)
                        .collect(joining("\n"));
            }
        }

        private static Stream<String> lines(String content) {
            return new BufferedReader(new StringReader(content)).lines();
        }

        private static String getMergedContent(Path contentFilePath, Charset sourceEncoding) {
            Map<String, Object> options = new HashMap<>();
            options.put("safe", 0);
            options.put("base_dir", contentFilePath.getParent().toString());

            String rootFileContent = readContent(contentFilePath, sourceEncoding);

            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            ContentRecordingPreprocessor contentRecordingPreprocessor = new ContentRecordingPreprocessor();
            asciidoctor.javaExtensionRegistry().preprocessor(contentRecordingPreprocessor);
            asciidoctor.load(rootFileContent, options);

            return contentRecordingPreprocessor.content();
        }

        private void visitBlocks(List<AbstractBlock> blocks) {
            blocks.forEach((block) -> {
                if (block.getContext().equals("section")) {
                    IRubyObject sourceLocationVariable = ((RubyObjectHolderProxy) block.delegate()).__ruby_object().getInstanceVariables().getInstanceVariable("@source_location");
                    String sourceLocationValue = sourceLocationVariable.asString().asJavaString();
                    String[] fileNameAndLineNumber = sourceLocationValue.split(":");

                    int lineNumber = parseInt(fileNameAndLineNumber[1].substring("line ".length() + 1));

                    this.sectionStarts.add(new SectionStart(block.getTitle(), lineNumber, block.getLevel()));

                    visitBlocks(block.getBlocks());
                }
            });
        }

        private static String readContent(Path contentFilePath, Charset sourceEncoding) {
            try {
                return new String(readAllBytes(contentFilePath), sourceEncoding);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read content from path '" + contentFilePath + "'", e);
            }
        }

        private int endLineNumber(SectionStart nextSectionStart) {
            if (nextSectionStart == null) {
                return -1;
            }

            return nextSectionStart.lineNumber - 1;
        }

        private SectionStart optionalNextSectionStart(SectionStart currentSectionStart) {
            return this.sectionStarts.indexOf(currentSectionStart) < this.sectionStarts.size() - 1 ? this.sectionStarts.get(this.sectionStarts.indexOf(currentSectionStart) + 1) : null;
        }


        static class SectionStart {

            private final String title;
            private final int lineNumber;
            private final int level;

            SectionStart(String title, int lineNumber, int level) {
                this.title = title;
                this.lineNumber = lineNumber;
                this.level = level;
            }

            String title() {
                return this.title;
            }

            int lineNumber() {
                return this.lineNumber;
            }

            int level() {
                return this.level;
            }

        }


        private static class ContentRecordingPreprocessor extends Preprocessor {

            private String content;

            String content() {
                return this.content;
            }

            @Override
            public PreprocessorReader process(Document document, PreprocessorReader reader) {
                this.content = lines(reader.read())
                        .map((line) -> line.replaceAll("^include::", "\\\\include::"))
                        .collect(joining("\n"));

                return reader;
            }

            private static Stream<String> lines(String content) {
                return new BufferedReader(new StringReader(content)).lines();
            }

        }

    }


    static class Section {

        private final String title;
        private final String content;
        private final int level;

        Section(String title, String content, int level) {
            this.title = title;
            this.content = content;
            this.level = level;
        }

        String title() {
            return this.title;
        }

        String content() {
            return this.content;
        }

        int level() {
            return this.level;
        }

    }


    static class DefaultAsciidocPage implements AsciidocPage {

        private final Path path;
        private final Path baseDir;
        private final List<AsciidocPage> children;

        DefaultAsciidocPage(Path path, Path baseDir) {
            this.path = path;
            this.baseDir = baseDir;
            this.children = new ArrayList<>();
        }

        @Override
        public Path path() {
            return this.path;
        }

        @Override
        public Path baseDir() {
            return this.baseDir;
        }

        @Override
        public List<AsciidocPage> children() {
            return this.children;
        }

        void addChildren(List<AsciidocPage> children) {
            this.children.addAll(children);
        }

    }


}

