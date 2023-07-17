package org.sahli.markdown.visitor;


import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TitleVisitorTest {


    @Test
    public void findTitle(){

        DataHolder NO_OPTIONS = new MutableDataSet();
        Parser PARSER = Parser.builder(NO_OPTIONS).build();

        String markdown = "# Title";
        Document document = PARSER.parse(markdown);

        TitleVisitor titleVisitor = new TitleVisitor();

        titleVisitor.visit(document);
        assertThat(titleVisitor.title()).isEqualTo("Title");
    }

}
