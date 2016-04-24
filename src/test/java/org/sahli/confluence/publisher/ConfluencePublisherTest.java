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
import org.sahli.confluence.publisher.http.ConfluenceRestClient;
import org.sahli.confluence.publisher.metadata.ConfluencePage;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.InputStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
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
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
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
        ConfluencePage confluencePage = metadata.getPages().get(0);
        assertThat(confluencePage.getTitle(), is("Some Confluence Content"));
        assertThat(confluencePage.getContentFilePath(), is("some-confluence-content.html"));
    }

    @Test
    public void publish_oneNewPageWithSpaceKey_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
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
        ConfluencePublisher confluencePublisher = confluencePublisher("one-page-ancestor-id", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithSpaceKeyAsRoot_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderSpace(anyString(), anyString(), anyString())).thenReturn("1234");
        ConfluencePublisher confluencePublisher = confluencePublisher("root-space-key-multiple-pages", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderSpace(eq("~personalSpace"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("1234"), eq("Some Child Content"), eq("<h1>Some Child Content</h1>"));
    }

    @Test
    public void publish_multiplePagesInHierarchyWithAncestorIdAsRoot_delegatesToConfluenceRestClient() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString())).thenReturn("1234");
        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-multiple-pages", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("1234"), eq("Some Child Content"), eq("<h1>Some Child Content</h1>"));
    }

    @Test
    public void publish_metadataOnePageWithAttachmentsAndSpaceKeyAsRoot_attachesAttachmentToContent() throws Exception {
        // arrange
        ConfluenceRestClient confluenceRestClientMock = mock(ConfluenceRestClient.class);
        when(confluenceRestClientMock.addPageUnderSpace(anyString(), anyString(), anyString())).thenReturn("1234");

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
        when(confluenceRestClientMock.addPageUnderAncestor(anyString(), anyString(), anyString())).thenReturn("4321");

        ArgumentCaptor<String> contentId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attachmentFileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> attachmentContent = ArgumentCaptor.forClass(InputStream.class);

        ConfluencePublisher confluencePublisher = confluencePublisher("root-ancestor-id-page-with-attachments", confluenceRestClientMock);

        // act
        confluencePublisher.publish();

        // assert
        verify(confluenceRestClientMock, times(1)).addPageUnderAncestor(eq("72189173"), eq("Some Confluence Content"), eq("<h1>Some Confluence Content</h1>"));
        verify(confluenceRestClientMock, times(2)).addAttachment(contentId.capture(), attachmentFileName.capture(), attachmentContent.capture());
        assertThat(contentId.getAllValues(), contains("4321", "4321"));
        assertThat(attachmentFileName.getAllValues(), contains("attachmentOne.txt", "attachmentTwo.txt"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(0)), is("attachment1"));
        assertThat(inputStreamAsString(attachmentContent.getAllValues().get(1)), is("attachment2"));
    }


    private static ConfluencePublisher confluencePublisher(String qualifier, ConfluenceRestClient confluenceRestClient) {
        return new ConfluencePublisher(TEST_RESOURCES + "/metadata-" + qualifier + ".json", confluenceRestClient);
    }

}