package org.sahli.asciidoc.confluence.publisher.converter.macro;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.extension.RubyExtensionRegistry;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

public class Registry implements ExtensionRegistry {

    public static final String MACRO_DIR = "/org/sahli/asciidoc/confluence/publisher/converter/macro";

    @Override
    public void register(Asciidoctor asciidoctor) {
        RubyExtensionRegistry registry = asciidoctor.rubyExtensionRegistry();
        registry.loadClass(Registry.class.getResourceAsStream(MACRO_DIR + "/JiraIssue.rb")).inlineMacro("jira", "JiraIssue");
    }
}
