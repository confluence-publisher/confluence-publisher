package org.sahli.markdown.visitor;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

public class TitleVisitor {
    private NodeVisitor visitor = new NodeVisitor(
            new VisitHandler<>(Heading.class, this::visit)
    );

    public String title() {
        return title;
    }

    private String title;

    private void visit(Heading heading) {

        if (heading.getLevel() == 1){
            title = heading.getText().toString();
        } else {
            visitor.visitChildren(heading);
        }

    }

    public void visit(Document document){
        this.visitor.visit(document);
    }
}
