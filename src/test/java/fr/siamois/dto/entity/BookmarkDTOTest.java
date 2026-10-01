package fr.siamois.dto.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookmarkDTOTest {

    private static BookmarkDTO bookmarkOf(String resourceUri) {
        BookmarkDTO dto = new BookmarkDTO();
        dto.setResourceUri(resourceUri);
        return dto;
    }

    @Test
    void phaseAndContainerBookmarksGetTheirOwnIconAndColor() {
        assertEquals("bi bi-layers", bookmarkOf("/phase/8").getBookmarkIcon());
        assertEquals("var(--ground-main-color)", bookmarkOf("/phase/8").getBookmarkColor());
        assertEquals("bi bi-box-seam", bookmarkOf("/container/3").getBookmarkIcon());
        assertEquals("var(--third-main-color)", bookmarkOf("/container/3").getBookmarkColor());
    }

    @Test
    void unknownUriFallsBackToAPlainBookmark() {
        assertEquals("bi bi-bookmark", bookmarkOf("/nothing/1").getBookmarkIcon());
        assertEquals("var(--siamois-green)", bookmarkOf("/nothing/1").getBookmarkColor());
    }

    @Test
    void recordingUnitKeepsItsIcon() {
        assertEquals("bi bi-pencil-square", bookmarkOf("/recording-unit/4").getBookmarkIcon());
    }
}
