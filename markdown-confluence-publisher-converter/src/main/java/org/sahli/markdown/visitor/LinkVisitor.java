package org.sahli.markdown.visitor;

import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.sequence.BasedSequence;

public class LinkVisitor {
    NodeVisitor visitor = new NodeVisitor(
            new VisitHandler<>(Link.class, this::visitLink)
    );

    public void visitLink(Link link) {

        //Replace all sources link by empty link to avoid asciidoc import issues
        //TODO Handle link propertly to map to git.scm
        if (link.getUrl().startsWith("http")){
            //Do Nothing
        } else {
            link.setUrl(BasedSequence.of(""));
        }
        // Descending into children
        visitor.visitChildren(link);

    }

    public void visit(Document document){
        this.visitor.visit(document);
    }
}
