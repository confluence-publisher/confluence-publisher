package org.sahli.confluence.publisher.payloads;

import org.sahli.confluence.publisher.support.RuntimeUse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alain Sahli
 * @since 1.0
 */

public class NewPage {

    private String type;
    private String title;
    private Space space;
    private Body body;
    private List<Ancestor> ancestors = new ArrayList<>();

    public void addAncestor(Ancestor ancestor) {
        this.ancestors.add(ancestor);
    }

    @RuntimeUse
    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @RuntimeUse
    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @RuntimeUse
    public Space getSpace() {
        return this.space;
    }

    public void setSpace(Space space) {
        this.space = space;
    }

    @RuntimeUse
    public Body getBody() {
        return this.body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    @RuntimeUse
    public List<Ancestor> getAncestors() {
        return this.ancestors;
    }

}
