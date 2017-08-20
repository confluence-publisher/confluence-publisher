package org.sahli.asciidoc.confluence.publisher.converter;

import org.junit.Test;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class FolderBasedAsciidocPagesStructureProviderTest {

    private static AsciidocPage NON_EXISTING_ASCIIDOC_PAGE = mock(AsciidocPage.class);

    @Test
    public void structure_nestedStructure_returnsAsciidocPagesStructureWithAllNonIncludeAdocFiles() throws Exception {
        // arrange
        Path documentationRootFolder = Paths.get("src/test/resources/folder-based-asciidoc-page-structure");
        FolderBasedAsciidocPagesStructureProvider folderBasedAsciidocSourceStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder);

        // act
        AsciidocPagesStructureProvider.AsciidocPagesStructure structure = folderBasedAsciidocSourceStructureProvider.structure();

        // assert
        assertThat(structure.pages().size(), is(1));

        AsciidocPage indexPage = asciidocPageByPath(structure.pages(), documentationRootFolder.resolve("index.adoc"));
        assertThat(indexPage, is(not(nullValue())));
        assertThat(indexPage.children().size(), is(2));

        AsciidocPage subPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one.adoc"));
        assertThat(subPageOne, is(not(nullValue())));
        assertThat(subPageOne.children().size(), is(1));

        AsciidocPage subPageTwo = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-two.adoc"));
        assertThat(subPageTwo, is(not(nullValue())));
        assertThat(subPageTwo.children().size(), is(0));

        AsciidocPage subSubPageOne = asciidocPageByPath(indexPage.children(), documentationRootFolder.resolve("index/sub-page-one/sub-sub-page-one.adoc"));
        assertThat(subSubPageOne, is(not(nullValue())));
        assertThat(subSubPageOne.children().size(), is(0));
    }

    private AsciidocPage asciidocPageByPath(List<AsciidocPage> asciidocPages, Path asciidocPagePath) {
        return asciidocPages.stream()
                .filter((asciidocPage) -> asciidocPage.path().equals(asciidocPagePath))
                .findFirst()
                .orElse(NON_EXISTING_ASCIIDOC_PAGE);
    }

}