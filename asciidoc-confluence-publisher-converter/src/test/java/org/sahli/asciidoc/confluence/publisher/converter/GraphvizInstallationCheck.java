/*
 * Copyright 2020 the original author or authors.
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

import net.sourceforge.plantuml.dot.Graphviz;
import net.sourceforge.plantuml.dot.GraphvizUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;

class GraphvizInstallationCheck implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        Graphviz graphviz = GraphvizUtils.create(null, "png");
        File dotExe = graphviz.getDotExe();

        if (!dotExe.exists()) {
            throw new AssertionError("Graphviz is not installed (Dot binary expected at path '" + dotExe.getAbsolutePath() + "')");
        }

        return base;
    }

}
