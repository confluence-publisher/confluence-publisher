/*
 * Copyright 2016 the original author or authors.
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

package org.sahli.confluence.publisher.http;

import java.io.InputStream;
import java.util.List;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public interface ConfluenceClient {

    String addPageUnderAncestor(String spaceKey, String ancestorId, String title, String content);

    void updatePage(String contentId, String title, String content, int newVersion);

    void deletePage(String contentId);

    String getPageByTitle(String spaceKey, String title) throws NotFoundException, MultipleResultsException;

    void addAttachment(String contentId, String attachmentFileName, InputStream attachmentContent);

    void updateAttachmentContent(String contentId, String attachmentId, InputStream attachmentContent);

    void deleteAttachment(String attachmentId);

    ConfluenceAttachment getAttachmentByFileName(String contentId, String attachmentFileName) throws NotFoundException, MultipleResultsException;

    ConfluencePage getPageWithContentAndVersionById(String contentId);

    boolean pageExistsByTitle(String spaceKey, String title);

    boolean attachmentExistsByFileName(String contentId, String attachmentFileName);

    InputStream getAttachmentContent(String relativeDownloadLink);

    List<ConfluencePage> getChildPages(String contentId);

    List<ConfluenceAttachment> getAttachments(String contentId);

    String getSpaceContentId(String spaceKey);

}
