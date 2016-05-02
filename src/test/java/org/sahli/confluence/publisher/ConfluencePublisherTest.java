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

package org.sahli.confluence.publisher;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.sahli.confluence.publisher.http.ConfluenceAttachment;
import org.sahli.confluence.publisher.http.ConfluencePage;
import org.sahli.confluence.publisher.http.ConfluenceRestClient;
import org.sahli.confluence.publisher.http.NotFoundException;
import org.sahli.confluence.publisher.metadata.ConfluencePageMetadata;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.inputStreamAsString;

public class ConfluencePublisherTest {

    private static final String TEST_RESOURCES = "src/test/resources/org/sahli/confluence/publisher";

    @Rule
    public ExpectedException expectedException = none();

    @Test
    public void metadata_withOnePageAndAncestorId_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", confluenceRestClientMock);

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getAncestorId(), is("72189173"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePageMetadata confluencePageMetadata = metadata.getPages().get(0);
        assertThat(confluencePageMetadata.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePageMetadata.getContentFilePath(), is("some-confluence-content.html"));
    }

    @Test
    public void metadata_withOnePageSpaceKey_convertItCorrectlyAndIsValid() throws Exception {
        // arrange + act
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", confluenceRestClientMock);

        // assert
        ConfluencePublisherMetadata metadata = confluencePublisher.getMetadata();
        assertThat(metadata.getSpaceKey(), is("~personalSpace"));
        assertThat(metadata.getPages(), hasSize(1));
        ConfluencePageMetadata confluencePageMetadata = metadata.getPages().get(0);
        assertThat(confluencePageMetadata.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePageMetadata.getContentFilePath(), is("some-confluence-content.html"));
    }

    @Test
    public void publish_withMetadataMissingSpaceKey_throwsIllegalArgumentException() throws Exception {
        // assert
        this.expectedException.expect(IllegalArgumentException.class);
        this.expectedException.expectMessage("spaceKey must be set");

        // arrange + act
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        ConfluencePublisher confluencePublisher = confluencePublisher("without-space-key", confluenceRestClientMock);
        confluencePublisher.publish();
    }

    @Test
    public void publish_oneNewPageWithSpaceKey_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-space-key", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderSpace(eq("~personalSpace"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
    }

    @Test
    public void publish_oneNewPageWithAncestorId_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithSpaceKeyAsRoot_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderSpace(anyString(), anyString(), anyString())).thenReturn("1234");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("root-space-key-multiple-pages", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderSpace(eq("~personalSpace"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Some Child Content"), eq("<h1>Some Child Content</h1>"));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithAncestorIdAsRoot_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString())).thenReturn("1234");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-multiple-pages", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        ArgumentCaptor<String> spaceKeyArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ancestorIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(confluenceRestClientMock, times(2)).addPageUnderAncestor(spaceKeyArgumentCaptor.capture(),
                ancestorIdArgumentCaptor.capture(), titleArgumentCaptor.capture(), contentArgumentCaptor.capture());
        assertThat(spaceKeyArgumentCaptor.getAllValues(), contains("~personalSpace", "~personalSpace"));
        assertThat(ancestorIdArgumentCaptor.getAllValues(), contains("72189173", "1234"));
        assertThat(titleArgumentCaptor.getAllValues(), contains("Some Confluence Content", "Some Child Content"));
        assertThat(contentArgumentCaptor.getAllValues(), contains("<h1>Some Confluence Content</h1>", "<h1>Some Child Content</h1>"));
    }

    @Test
    public void publish_metadataOnePageWithAttachmentsAndSpaceKeyAsRoot_attachesAttachmentToContent() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderSpace(anyString(), anyString(), anyString())).thenReturn("1234");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceRestClientMock.getAttachmentByFileName(anyString(), anyString())).thenThrow(new NotFoundException());

        ArgumentCaptor<String> contentId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attachmentFileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> attachmentContent = ArgumentCaptor.forClass(InputStream.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-space-key-page-with-attachments", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderSpace(eq("~personalSpace"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(2)).addAttachment(contentId.capture(), attachmentFileName.capture(), attachmentContent.capture());
        assertThat(contentId.getAllValues(), contains("1234", "1234"));
        assertThat(attachmentFileName.getAllValues(), contains("attachmentOne.txt", "attachmentTwo.txt"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(0)), is("attachment1"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(1)), is("attachment2"));
    }

    @Test
    public void publish_metadataOnePageWithAttachmentsAndAncestorIdAsRoot_attachesAttachmentToContent() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString(), anyString())).thenReturn("4321");
        when(confluenceRestClientMock.getPageByTitle(anyString(), anyString())).thenThrow(new NotFoundException());
        when(confluenceRestClientMock.getAttachmentByFileName(anyString(), anyString())).thenThrow(new NotFoundException());

        ArgumentCaptor<String> contentId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attachmentFileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> attachmentContent = ArgumentCaptor.forClass(InputStream.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("~personalSpace"), eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(2)).addAttachment(contentId.capture(), attachmentFileName.capture(), attachmentContent.capture());
        assertThat(contentId.getAllValues(), contains("4321", "4321"));
        assertThat(attachmentFileName.getAllValues(), contains("attachmentOne.txt", "attachmentTwo.txt"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(0)), is("attachment1"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(1)), is("attachment2"));
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootSpace_sendsUpdateRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-space-key", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderSpace(eq("~personalSpace"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(1)).updatePage(eq("3456"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2));
    }

    @Test
    public void publish_metadataWithExistingPageWithSameContentUnderRootSpace_sendsNoAddOrUpdateRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-space-key", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderSpace(eq("~personalSpace"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, never()).updatePage(eq("3456"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2));
    }

    @Test
    public void publish_metadataWithExistingPageWithDifferentContentUnderRootAncestor_sendsUpdateRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Other Confluence Content</h1>", 1));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(1)).updatePage(eq("3456"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2));
    }

    @Test
    public void publish_metadataWithExistingPageWithSameContentUnderRootAncestor_sendsNoAddOrUpdateRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-ancestor-id", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addPageUnderAncestor(eq("~personalSpace"), eq("1234"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, never()).updatePage(eq("3456"), eq("Existing Page"), eq("<h1>Some Confluence Content</h1>"), eq(2));
    }

    @Test
    public void publish_metadataWithExistingPageAndAttachmentWithDifferentAttachmentContentUnderRootSpace_sendsUpdateAttachmentRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");

        ConfluencePage existingConfluencePage = new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1);
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(existingConfluencePage);

        ConfluenceAttachment existingConfluenceAttachment = new ConfluenceAttachment("att12", "attachmentOne.txt", "/download/attachmentOne.txt", 1);
        when(confluenceRestClientMock.getAttachmentByFileName("3456", "attachmentOne.txt")).thenReturn(existingConfluenceAttachment);

        when(confluenceRestClientMock.getAttachmentContent(anyString())).thenReturn(new ByteArrayInputStream("Old content".getBytes()));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-and-existing-attachment-space-key", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));
        ArgumentCaptor<InputStream> attachmentContentCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(confluenceRestClientMock, times(1)).updateAttachmentContent(eq("3456"), eq("att12"), attachmentContentCaptor.capture());
        assertThat(inputStreamAsString(attachmentContentCaptor.getValue()), is("attachment1"));
    }

    @Test
    public void publish_metadataWithExistingPageAndAttachmentWithSameAttachmentContentUnderRootSpace_sendsNoAddOrUpdateAttachmentRequest() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.getPageByTitle("~personalSpace", "Existing Page")).thenReturn("3456");

        ConfluencePage existingConfluencePage = new ConfluencePage("3456", "Existing Page", "<h1>Some Confluence Content</h1>", 1);
        when(confluenceRestClientMock.getPageWithContentAndVersionById("3456")).thenReturn(existingConfluencePage);

        ConfluenceAttachment existingConfluenceAttachment = new ConfluenceAttachment("att12", "attachmentOne.txt", "/download/attachmentOne.txt", 1);
        when(confluenceRestClientMock.getAttachmentByFileName("3456", "attachmentOne.txt")).thenReturn(existingConfluenceAttachment);

        when(confluenceRestClientMock.getAttachmentContent(anyString())).thenReturn(new ByteArrayInputStream("attachment1".getBytes()));

        ConfluencePublisher confluencePublisher = confluencePublisher("existing-page-and-existing-attachment-space-key", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, never()).addAttachment(anyString(), anyString(), any(InputStream.class));
        verify(confluenceRestClientMock, never()).updateAttachmentContent(anyString(), anyString(), any(InputStream.class));
    }



    private static ConfluencePublisher confluencePublisher(String qualifier, ConfluenceRestClient confluenceRestClient) {
        return new ConfluencePublisher(TEST_RESOURCES + "/metadata-" + qualifier + ".json", confluenceRestClient);
    }

}