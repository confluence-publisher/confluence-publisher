/*
 * Copyright 2025 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client.http;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.io.InputStream;
import java.util.List;

public interface HttpRequestFactory {

    HttpPost addPageUnderAncestorRequest(String spaceKey, String ancestorId, String title, String content, String versionMessage);

    HttpPut updatePageRequest(String contentId, String ancestorId, String title, String content, int newVersion, String versionMessage, boolean notifyWatchers);

    HttpDelete deletePageRequest(String contentId);

    HttpPost addAttachmentRequest(String contentId, String attachmentFileName, InputStream attachmentContent);

    HttpPost updateAttachmentContentRequest(String contentId, String attachmentId, InputStream attachmentContent, boolean notifyWatchers);

    HttpDelete deleteAttachmentRequest(String attachmentId);

    HttpGet getPageByTitleRequest(String spaceKey, String title);

    HttpGet getAttachmentByFileNameRequest(String contentId, String attachmentFileName, String expandOptions);

    HttpGet getPageByIdRequest(String contentId, String expandOptions);

    HttpGet getChildPagesByIdRequest(String parentContentId, Integer limit, Integer start, String expandOptions);

    HttpGet getAttachmentsRequest(String contentId, Integer limit, Integer start, String expandOptions);

    HttpGet getAttachmentContentRequest(String relativeDownloadLink);

    HttpGet getPropertyByKeyRequest(String contentId, String key);

    HttpDelete deletePropertyByKeyRequest(String contentId, String key);

    HttpPost setPropertyByKeyRequest(String contentId, String key, String value);

    HttpGet getLabelsRequest(String contentId);

    HttpPost addLabelsRequest(String contentId, List<String> labels);

    HttpDelete deleteLabelRequest(String contentId, String label);
}
