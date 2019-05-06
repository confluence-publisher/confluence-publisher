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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceAttachment;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher.CONTENT_HASH_PROPERTY_KEY;
import static org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher.INITIAL_PAGE_VERSION;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.APPEND_TO_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy.REPLACE_ANCESTOR;
import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisherTest {

    private static final String TEST_RESOURCES = "src/test/resources/org/sahli/asciidoc/confluence/publisher/client";
    private static final String SOME_CONFLUENCE_CONTENT_SHA256_HASH = "7a901829ba6a0b6f7f084ae4313bdb5d83bc2c4ea21b452ba7073c0b0c60faae";

    ConfluencePublisherListener confluencePublisherListenerMock = mock(ConfluencePublisherListener.class);
    ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void publish_withMetadataMissingSpaceKey_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("spaceKey must be set");

        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("without-space-key");
        confluencePublisher.publish();
    }

    @Test
    public void publish_withMetadataMissingAncestorId_throwsIllegalArgumentException() {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("ancestorId must be set");

        // arrange + act
        ConfluencePublisher confluencePublisher = confluencePublisher("without-ancestor-id");
        confluencePublisher.publish();
    }

    @Test
    public void publish_oneNewPageWithAncestorId_delegatesToConfluenceRestClient() {
        // arrange
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("2345");

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_multiplePageWithAncestorId_delegatesToConfluenceRestClient() {
        // arrange
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("2345", "3456");

        ConfluencePublisher confluencePublisher = confluencePublisher("multiple-page-ancestor-id", "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Other Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("3456", "Some Other Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_multiplePageWithAncestorIdAndReplaceAncestorStrategy_delegatesToConfluenceRestClient() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Multiple root pages detected: 'Some Confluence Content', 'Some Other Confluence Content', but 'REPLACE_ANCESTOR' publishing strategy only supports one single root page");

        ConfluencePublisher confluencePublisher = confluencePublisher("multiple-page-ancestor-id-replace", REPLACE_ANCESTOR, "version message");
        confluencePublisher.publish();
    }

    @Test
    public void publish_multiplePagesInHierarchyWithAncestorIdAsRoot_delegatesToConfluenceRestClient() {
        // arrange
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("1234", "2345");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-multiple-pages", "version message");

        // act
        confluencePublisher.publish();

        // assert
        ArgumentCaptor<String> spaceKeyArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ancestorIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(confluenceRestClientMock, times(2)).addPageUnderAncestor(spaceKeyArgumentCaptor.capture(), ancestorIdArgumentCaptor.capture(), titleArgumentCaptor.capture(), contentArgumentCaptor.capture(), messageArgumentCaptor.capture());
        assertThat(spaceKeyArgumentCaptor.getAllValues(), contains("~personalSpace", "~personalSpace"));
        assertThat(ancestorIdArgumentCaptor.getAllValues(), contains("72189173", "1234"));
        assertThat(titleArgumentCaptor.getAllValues(), contains("Some Confluence Content", "Some Child Content"));
        assertThat(contentArgumentCaptor.getAllValues(), contains("<h1>Some Confluence Content</h1>", "<h1>Some Child Content</h1>"));
        assertThat(messageArgumentCaptor.getAllValues(), contains("version message", "version message"));

        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("1234", "Some Confluence Content", "<h1>Some Confluence Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).pageAdded(eq(new ConfluencePage("2345", "Some Child Content", "<h1>Some Child Content</h1>", INITIAL_PAGE_VERSION)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataOnePageWithNewAttachmentsAndAncestorIdAsRoot_attachesAttachmentToContent() {
        // arrange
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("4321");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceRestClientMock.getAttachmentByFileName(anyString(), anyString())).thenThrow(new NotFoundException());

        ArgumentCaptor<String> contentId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attachmentFileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> attachmentContent = ArgumentCaptor.forClass(InputStream.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock).addPageUnderAncestor("~personalSpace", "72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", null);
        verify(confluenceRestClientMock, times(2)).addAttachment(contentId.capture(), attachmentFileName.capture(), attachmentContent.capture());
        assertThat(contentId.getAllValues(), contains("4321", "4321"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(attachmentFileName.getAllValues().indexOf("attachmentOne.txt")), UTF_8), is("attachment1"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(attachmentFileName.getAllValues().indexOf("attachmentTwo.txt")), UTF_8), is("attachment2"));
        verify(confluenceRestClientMock).setPropertyByKey("4321", "attachmentOne.txt-hash", sha256Hex("attachment1"));
        verify(confluenceRestClientMock).setPropertyByKey("4321", "attachmentTwo.txt-hash", sha256Hex("attachment2"));
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootAncestor_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("3456", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1);

        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(existingPage);
        when(confluenceRestClientMock.getPropertyByKey("3456", CONTENT_HASH_PROPERTY_KEY)).thenReturn("someWrongHash");

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceRestClientMock, times(1)).updatePage(eq("3456"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq("version message"));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootAncestorAndReplaceAncestorStrategy_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("1234", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1);

        when(confluenceRestClientMock.getPageWithContentAndVersionById("1234")).thenReturn(existingPage);
        when(confluenceRestClientMock.getPropertyByKey("1234", CONTENT_HASH_PROPERTY_KEY)).thenReturn("someWrongHash");

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id-replace", REPLACE_ANCESTOR, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq("version message"));
        verify(confluenceRestClientMock, times(1)).updatePage(eq("1234"), eq(null), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq("version message"));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("1234", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageWithSameContentButDifferentTitleAndReplaceAncestorStrategy_sendsUpdateRequest() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("1234", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1);

        when(confluenceRestClientMock.getPageWithContentAndVersionById("1234")).thenReturn(existingPage);
        when(confluenceRestClientMock.getPropertyByKey("1234", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id-replace", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(null));
        verify(confluenceRestClientMock, times(1)).updatePage(eq("1234"), eq(null), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq(null));

        verify(confluencePublisherListenerMock, times(1)).pageUpdated(eq(existingPage), eq(new ConfluencePage("1234", "Existing Page", "<h1>Some Confluence Content</h1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithExistingPageAndReplaceAncestorStrategy_sendsUpdate() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1);

        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(existingPage);
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(any(), any(), any(), any(), any());
        verify(confluenceRestClientMock).updatePage(eq("72189173"), eq(null), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"), eq(2), eq(null));
        verify(confluencePublisherListenerMock).pageUpdated(existingPage, new ConfluencePage("72189173", "Some Confluence Content", "<h1>Some Confluence Content</h1>", 2));
        verify(confluencePublisherListenerMock).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_whenAttachmentsHaveSameContentHash_doesNotUpdateAttachments() {
        // arrange
        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "/download/attachmentOne.txt", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt-hash")).thenReturn(sha256Hex("attachment1"));

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "/download/attachmentTwo.txt", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentTwo.txt-hash")).thenReturn(sha256Hex("attachment2"));

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addAttachment(any(), any(), any());
        verify(confluenceRestClientMock, never()).updateAttachmentContent(any(), any(), any());
    }

    @Test
    public void publish_whenExistingAttachmentsHaveMissingHashProperty_updatesAttachmentsAndHashProperties() {
        // arrange
        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt")).thenReturn(null);

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentTwo.txt-hash")).thenReturn(null);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentOne.txt-hash");
        verify(confluenceRestClientMock).updateAttachmentContent(eq("72189173"), eq("att1"), any(FileInputStream.class));
        verify(confluenceRestClientMock).setPropertyByKey("72189173", "attachmentOne.txt-hash", sha256Hex("attachment1"));

        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentTwo.txt-hash");
        verify(confluenceRestClientMock).updateAttachmentContent(eq("72189173"), eq("att2"), any(FileInputStream.class));
        verify(confluenceRestClientMock).setPropertyByKey("72189173", "attachmentTwo.txt-hash", sha256Hex("attachment2"));
    }

    @Test
    public void publish_whenExistingAttachmentsHaveDifferentHashProperty_updatesAttachmentsAndHashProperties() {
        // arrange
        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt")).thenReturn("otherHash1");

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt")).thenReturn("otherHash2");

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentOne.txt-hash");
        verify(confluenceRestClientMock).updateAttachmentContent(eq("72189173"), eq("att1"), content.capture());
        verify(confluenceRestClientMock).setPropertyByKey("72189173", "attachmentOne.txt-hash", sha256Hex("attachment1"));
        assertThat(inputStreamAsString(content.getValue(), UTF_8), is("attachment1"));

        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentTwo.txt-hash");
        verify(confluenceRestClientMock).updateAttachmentContent(eq("72189173"), eq("att2"), content.capture());
        verify(confluenceRestClientMock).setPropertyByKey("72189173", "attachmentTwo.txt-hash", sha256Hex("attachment2"));
        assertThat(inputStreamAsString(content.getValue(), UTF_8), is("attachment2"));
    }

    @Test
    public void publish_whenNewAttachmentsAreEmpty_deletesAttachmentsPresentOnConfluence() {
        //arrange
        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceRestClientMock.getAttachments("72189173")).thenReturn(asList(
                new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1),
                new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1)
        ));

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock).deleteAttachment("att1");
        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentOne.txt-hash");

        verify(confluenceRestClientMock).deleteAttachment("att2");
        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentTwo.txt-hash");
    }

    @Test
    public void publish_whenOldAttachmentsStillExist_deletesAttachmentsNotPresentUnderPage() {
        // arrange
        when(confluenceRestClientMock.getPageWithContentAndVersionById("72189173")).thenReturn(new ConfluencePage("72189173", "Existing Page (Old Title)", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceRestClientMock.getAttachments("72189173")).thenReturn(asList(
                new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1),
                new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1),
                new ConfluenceAttachment("att3", "attachmentThree.txt", "", 1)

        ));

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt-hash")).thenReturn(sha256Hex("attachment1"));

        when(confluenceRestClientMock.getAttachmentByFileName("72189173", "attachmentTwo.txt")).thenReturn(new ConfluenceAttachment("att2", "attachmentTwo.txt", "", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentTwo.txt-hash")).thenReturn(sha256Hex("attachment2"));

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", REPLACE_ANCESTOR, null);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).deleteAttachment("att1");
        verify(confluenceRestClientMock, never()).deletePropertyByKey("72189173", "attachmentOne.txt-hash");

        verify(confluenceRestClientMock, never()).deleteAttachment("att2");
        verify(confluenceRestClientMock, never()).deletePropertyByKey("72189173", "attachmentTwo.txt-hash");

        verify(confluenceRestClientMock).deleteAttachment("att3");
        verify(confluenceRestClientMock).deletePropertyByKey("72189173", "attachmentThree.txt-hash");
    }

    @Test
    public void publish_metadataWithExistingPageAndAttachmentWithDifferentAttachmentContentUnderRootSpace_sendsUpdateAttachmentRequest() {
        // arrange
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("3456", CONTENT_HASH_PROPERTY_KEY)).thenReturn(SOME_CONFLUENCE_CONTENT_SHA256_HASH);

        when(confluenceRestClientMock.getAttachmentByFileName("3456", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "/download/attachmentOne.txt", 1));
        when(confluenceRestClientMock.getPropertyByKey("72189173", "attachmentOne.txt-hash")).thenReturn(sha256Hex("otherContent"));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-and-existing-attachment-space-key");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));
        ArgumentCaptor<InputStream> attachmentContentCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(confluenceRestClientMock, times(1)).updateAttachmentContent(eq("3456"), eq("att1"), attachmentContentCaptor.capture());
        assertThat(inputStreamAsString(attachmentContentCaptor.getValue(), UTF_8), is("attachment1"));
    }

    @Test
    public void publish_metadataWithExistingPageAndAttachmentWithSameAttachmentContentUnderRootSpace_sendsNoAddOrUpdateAttachmentRequest() {
        // arrange
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");

        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1));
        when(confluenceRestClientMock.getPropertyByKey("3456", CONTENT_HASH_PROPERTY_KEY)).thenReturn(CONTENT_HASH_PROPERTY_KEY);

        when(confluenceRestClientMock.getAttachmentByFileName("3456", "attachmentOne.txt")).thenReturn(new ConfluenceAttachment("att1", "attachmentOne.txt", "/download/attachmentOne.txt", 1));
        when(confluenceRestClientMock.getPropertyByKey("3456", "attachmentOne.txt-hash")).thenReturn(sha256Hex("attachment1"));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-and-existing-attachment-space-key");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));
        verify(confluenceRestClientMock, never()).updateAttachmentContent(anyString(), anyString(), any(InputStream.class));
    }

    @Test
    public void publish_metadataWithOneExistingPageButConfluencePageHasMissingHashPropertyValue_pageIsUpdatedAndHashPropertyIsSet() {
        // arrange
        ConfluencePage existingPage = new ConfluencePage("12", "Some Confluence Content", "<h1>Some Confluence Content</1>", 1);

        when(confluenceRestClientMock.getChildPages("1234")).thenReturn(singletonList(existingPage));
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Some Confluence Content")).thenReturn("12");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("12")).thenReturn(existingPage);
        when(confluenceRestClientMock.getPropertyByKey("12", CONTENT_HASH_PROPERTY_KEY)).thenReturn(null);

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).setPropertyByKey("12", CONTENT_HASH_PROPERTY_KEY, SOME_CONFLUENCE_CONTENT_SHA256_HASH);
    }

    @Test
    public void publish_metadataWithMultipleRemovedPagesInHierarchy_sendsDeletePageRequestForEachRemovedPage() {
        // arrange
        ConfluencePage existingParentPage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</1>", 2);
        ConfluencePage existingChildPage = new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</1>", 3);

        when(confluenceRestClientMock.getChildPages("1234")).thenReturn(singletonList(existingParentPage));
        when(confluenceRestClientMock.getChildPages("2345")).thenReturn(singletonList(existingChildPage));

        ConfluencePublisher confluencePublisher = confluencePublisher("zero-page-space-key", "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).deletePage(eq("2345"));

        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).pageDeleted(eq(new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</1>", 3)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    @Test
    public void publish_metadataWithMultipleRemovedPagesInHierarchy_sendsDeletePageRequestForEachRemovedPageExpectAncestor() {
        // arrange
        ConfluencePage existingParentPage = new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</1>", 2);
        ConfluencePage existingChildPage = new ConfluencePage("3456", "Some Child Content", "<h1>Some Child Content</1>", 3);

        when(confluenceRestClientMock.getChildPages("1234")).thenReturn(singletonList(existingParentPage));
        when(confluenceRestClientMock.getChildPages("2345")).thenReturn(singletonList(existingChildPage));

        ConfluencePublisher confluencePublisher = confluencePublisher("zero-page-space-key-replace", REPLACE_ANCESTOR, "version message");

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(0)).deletePage(eq("2345"));

        verify(confluencePublisherListenerMock, times(0)).pageDeleted(eq(new ConfluencePage("2345", "Some Confluence Content", "<h1>Some Confluence Content</1>", 2)));
        verify(confluencePublisherListenerMock, times(1)).publishCompleted();
        verifyNoMoreInteractions(confluencePublisherListenerMock);
    }

    private ConfluencePublisher confluencePublisher(String qualifier) {
        return confluencePublisher(qualifier, null);
    }

    private ConfluencePublisher confluencePublisher(String qualifier, String versionedMessage) {
        return confluencePublisher(qualifier, APPEND_TO_ANCESTOR, versionedMessage);
    }

    private ConfluencePublisher confluencePublisher(String qualifier, PublishingStrategy publishingStrategy, String versionMessage) {
        Path metadataFilePath = Paths.get(TEST_RESOURCES + "/metadata-" + qualifier + ".json");
        Path contentRoot = metadataFilePath.getParent().toAbsolutePath();

        ConfluencePublisherMetadata metadata = readConfig(metadataFilePath);
        resolveAbsoluteContentFileAndAttachmentsPath(metadata.getPages(), contentRoot);

        return new ConfluencePublisher(metadata, publishingStrategy, confluenceRestClientMock, confluencePublisherListenerMock, versionMessage);
    }

    private static ConfluencePublisherMetadata readConfig(Path metadataFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        try {
            return objectMapper.readValue(newInputStream(metadataFile), ConfluencePublisherMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read metadata", e);
        }
    }

    private static void resolveAbsoluteContentFileAndAttachmentsPath(List<ConfluencePageMetadata> pages, Path contentRoot) {
        pages.forEach((page) -> {
            page.setContentFilePath(contentRoot.resolve(page.getContentFilePath()).toString());
            page.setAttachments(page.getAttachments().entrySet().stream().collect(toMap(
                    (entry) -> entry.getValue(),
                    (entry) -> contentRoot.resolve(entry.getKey()).toString()
            )));

            resolveAbsoluteContentFileAndAttachmentsPath(page.getChildren(), contentRoot);
        });
    }

}
