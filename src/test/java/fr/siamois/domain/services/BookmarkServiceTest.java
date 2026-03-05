package fr.siamois.domain.services;

import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.BookmarkRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.InstitutionMapperImpl;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import io.swagger.models.auth.In;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private InstitutionMapper institutionMapper;

    @InjectMocks
    private BookmarkService bookmarkService;

    private UserInfo userInfo;
    private Person person;
    private Institution institution;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setUsername("user1");
        person.setId(1L);

        institution = new Institution();
        institution.setName("institution1");
        institution.setId(1L);

        userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");

    }

    @Test
    void testFindAllReturnsBookmarks() {

        Bookmark bookmark = new Bookmark();

        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(bookmarkRepository.findByPersonAndInstitution(person, institution)).thenReturn(List.of(bookmark));

        List<Bookmark> result = bookmarkService.findAll(userInfo);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(bookmark, result.get(0));
        verify(bookmarkRepository, times(1))
                .findByPersonAndInstitution(person, institution);
    }

    @Test
    void testFindAllReturnsEmptyList() {
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(bookmarkRepository.findByPersonAndInstitution(person, institution))
                .thenReturn(Collections.emptyList());

        List<Bookmark> result = bookmarkService.findAll(userInfo);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(bookmarkRepository, times(1))
                .findByPersonAndInstitution(person, institution);
    }

    @Test
    void testSaveCreatesBookmark() {

        AbstractPanel panel = mock(AbstractPanel.class);
        Bookmark savedBookmark = new Bookmark();
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(panel.ressourceUri()).thenReturn("resourceUri");
        when(panel.getTitleCodeOrTitle()).thenReturn("titleCode");
        when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(savedBookmark);

        Bookmark result = bookmarkService.save(userInfo, panel);

        assertNotNull(result);
        assertEquals(savedBookmark, result);
        verify(panel, times(1)).ressourceUri();
        verify(panel, times(1)).getTitleCodeOrTitle();
        verify(bookmarkRepository, times(1)).save(any(Bookmark.class));
    }

    @Test
    void testSave_Success() {
        String uri = "resource-uri";
        String title = "title";
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        Bookmark expectedBookmark = new Bookmark();
        expectedBookmark.setResourceUri(uri);
        expectedBookmark.setTitleCode(title);

        when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(expectedBookmark);

        Bookmark result = bookmarkService.save(userInfo, uri, title);

        assertNotNull(result);
        assertEquals(result, expectedBookmark);
        assertEquals(uri, result.getResourceUri());
        assertEquals(title, result.getTitleCode());

        verify(bookmarkRepository).save(any(Bookmark.class));
    }

    @Test
    void testIsRessourceBookmarkedByUser_ReturnsTrue() {
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(bookmarkRepository.countBookmarkByPersonAndInstitutionAndResourceUri(
                person, institution, "resource-uri")).thenReturn(1L);

        boolean result = bookmarkService.isRessourceBookmarkedByUser(userInfo, "resource-uri");

        assertTrue(result);
    }

    @Test
    void testIsRessourceBookmarkedByUser_ReturnsFalse() {
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(bookmarkRepository.countBookmarkByPersonAndInstitutionAndResourceUri(
                person, institution, "resource-uri")).thenReturn(0L);

        boolean result = bookmarkService.isRessourceBookmarkedByUser(userInfo, "resource-uri");

        assertFalse(result);
    }

    @Test
    void testDeleteBookmark_ExecutesSuccessfully() {
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        doNothing().when(bookmarkRepository).deleteBookmarkByPersonAndInstitutionAndResourceUri(
                person, institution, "resource-uri");

        bookmarkService.deleteBookmark(userInfo, "resource-uri");

        verify(bookmarkRepository).deleteBookmarkByPersonAndInstitutionAndResourceUri(
                person, institution, "resource-uri");
    }

}