/*
 * Copyright 2016-2019 the original author or authors.
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

package org.sahli.asciidoc.confluence.publisher.client;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.utils.ResourceUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

@RunWith(MockitoJUnitRunner.class)
public class ConfluenceAttachmentsHandlerTest {

    private static final String ATTACHMENT_FILE_NAME = "attachmentOne.txt";
    private static final String ATTACHMENT_PATH = ResourceUtils.getTestResourcePath(ATTACHMENT_FILE_NAME);
    private static final String ATTACHMENT_ID = "att1";
    private static final String ATTACHMENT_CONTENT = "attachment1";
    private static final String ATTACHMENT_HASH = sha256Hex(ATTACHMENT_CONTENT);
    private static final String CONTENT_ID = "contentId";

    @Mock
    ConfluenceClient client;

    @InjectMocks
    ConfluenceAttachmentsHandler handler;

    @Test
    public void deleteAttachmentsNotPresentUnderPage_whenNewAttachmentsEmpty_deletesAllExistingAttachments() {
        Map<String, String> newAttachments = Collections.emptyMap();
        when(client.getAttachments(CONTENT_ID)).thenReturn(asList(
                new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1),
                new ConfluenceAttachment("otherAttachment", ATTACHMENT_FILE_NAME, "", 1)
        ));

        handler.deleteAttachmentsNotPresentUnderPage(CONTENT_ID, newAttachments);

        verify(client).deleteAttachment(ATTACHMENT_ID);
        verify(client).deletePropertyByKey(CONTENT_ID, ATTACHMENT_ID);
        verify(client).deleteAttachment("otherAttachment");
        verify(client).deletePropertyByKey(CONTENT_ID, "otherAttachment");
    }

    @Test
    public void deleteAttachmentsNotPresentUnderPage_whenOldAttachmentsStillExist_doesNothing() {
        Map<String, String> newAttachments = new HashMap<>();
        newAttachments.put(ATTACHMENT_FILE_NAME, ATTACHMENT_PATH);
        newAttachments.put("otherFile.txt", ATTACHMENT_PATH);
        when(client.getAttachments(CONTENT_ID)).thenReturn(asList(
                new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1)
        ));

        handler.deleteAttachmentsNotPresentUnderPage(CONTENT_ID, newAttachments);

        verify(client).getAttachments(anyString());
        verifyNoMoreInteractions(client);
    }

    @Test
    public void addAttachments_withEmptyAttachments_doesNothing() {
        Map<String, String> attachments = Collections.emptyMap();

        handler.addAttachments(CONTENT_ID, attachments);

        verifyZeroInteractions(client);
    }

    @Test
    public void addAttachments_whenNoExistingAttachment_createsNewAttachment() {
        Map<String, String> attachments = new HashMap<>();
        attachments.put(ATTACHMENT_FILE_NAME, ATTACHMENT_PATH);

        when(client.getAttachmentByFileName(CONTENT_ID, ATTACHMENT_FILE_NAME)).thenThrow(NotFoundException.class);
        when(client.addAttachment(eq(CONTENT_ID), eq(ATTACHMENT_FILE_NAME), any(InputStream.class)))
                .thenReturn(new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1));

        handler.addAttachments(CONTENT_ID, attachments);

        verify(client).setPropertyByKey(CONTENT_ID, ATTACHMENT_ID, ATTACHMENT_HASH);
    }

    @Test
    public void addAttachments_whenExistingAttachmentHasTheSameContent_doesNothing() {
        Map<String, String> attachments = new HashMap<>();
        attachments.put(ATTACHMENT_FILE_NAME, ATTACHMENT_PATH);

        when(client.getAttachmentByFileName(CONTENT_ID, ATTACHMENT_FILE_NAME))
                .thenReturn(new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1));
        when(client.getPropertyByKey(CONTENT_ID, ATTACHMENT_ID)).thenReturn(ATTACHMENT_HASH);

        handler.addAttachments(CONTENT_ID, attachments);

        verify(client).getAttachmentByFileName(anyString(), anyString());
        verify(client).getPropertyByKey(anyString(), anyString());
        verifyNoMoreInteractions(client);
    }

    @Test
    public void addAttachments_whenExistingAttachmentHasMissingHashProperty_updatesAttachmentAndHashProperty() {
        Map<String, String> attachments = new HashMap<>();
        attachments.put(ATTACHMENT_FILE_NAME, ATTACHMENT_PATH);

        when(client.getAttachmentByFileName(CONTENT_ID, ATTACHMENT_FILE_NAME))
                .thenReturn(new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1));
        when(client.getPropertyByKey(CONTENT_ID, ATTACHMENT_ID)).thenReturn(null);

        handler.addAttachments(CONTENT_ID, attachments);

        verify(client).deletePropertyByKey(CONTENT_ID, ATTACHMENT_ID);
        verify(client).updateAttachmentContent(eq(CONTENT_ID), eq(ATTACHMENT_ID), any(FileInputStream.class));
        verify(client).setPropertyByKey(CONTENT_ID, ATTACHMENT_ID, ATTACHMENT_HASH);
    }

    @Test
    public void addAttachments_whenExistingAttachmentHasDifferentContent_updatesAttachmentAndHashProperty() {
        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
        Map<String, String> attachments = new HashMap<>();
        attachments.put(ATTACHMENT_FILE_NAME, ATTACHMENT_PATH);

        when(client.getAttachmentByFileName(CONTENT_ID, ATTACHMENT_FILE_NAME))
                .thenReturn(new ConfluenceAttachment(ATTACHMENT_ID, ATTACHMENT_FILE_NAME, "", 1));
        when(client.getPropertyByKey(CONTENT_ID, ATTACHMENT_ID)).thenReturn("otherHash");

        handler.addAttachments(CONTENT_ID, attachments);

        verify(client).deletePropertyByKey(CONTENT_ID, ATTACHMENT_ID);
        verify(client).updateAttachmentContent(eq(CONTENT_ID), eq(ATTACHMENT_ID), content.capture());
        verify(client).setPropertyByKey(CONTENT_ID, ATTACHMENT_ID, ATTACHMENT_HASH);
        assertThat(inputStreamAsString(content.getValue(), StandardCharsets.UTF_8), is(ATTACHMENT_CONTENT));
    }

}