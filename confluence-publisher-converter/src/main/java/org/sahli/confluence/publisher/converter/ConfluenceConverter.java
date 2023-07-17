/*
 * Copyright 2016-2017 the original author or authors.
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

package org.sahli.confluence.publisher.converter;

import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public final class ConfluenceConverter {

    private final static Logger LOGGER = LoggerFactory.getLogger(ConfluenceConverter.class);

    private final String spaceKey;
    private final String ancestorId;


    public ConfluenceConverter(String spaceKey, String ancestorId) {
        this.spaceKey = spaceKey;
        this.ancestorId = ancestorId;
    }

    public ConfluencePublisherMetadata convert(PagesStructureProvider pagesStructureProvider) {

        List<ConfluencePageMetadata> confluencePages = pagesStructureProvider.buildPageTree();

        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();
        confluencePublisherMetadata.setSpaceKey(this.spaceKey);
        confluencePublisherMetadata.setAncestorId(this.ancestorId);
        confluencePublisherMetadata.setPages(confluencePages);

        return confluencePublisherMetadata;
    }







}
