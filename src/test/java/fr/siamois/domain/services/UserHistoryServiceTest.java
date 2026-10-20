package fr.siamois.domain.services;

import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.UserHistoryRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserHistoryServiceTest {

    @Mock
    private UserHistoryRepository repository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private InstitutionMapper institutionMapper;
    @InjectMocks
    private UserHistoryService service;

    private final Person person = new Person();
    private final Institution institution = new Institution();
    private final UserInfo userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");

    @BeforeEach
    void setUp() {
        lenient().when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        lenient().when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
    }

    private static UserHistoryService.Visit visit(String main, String secondary) {
        return new UserHistoryService.Visit(main, "Titre", "icon", "css", secondary, secondary == null ? null : "Volet", null, null);
    }

    @Test
    void record_newPage_createsAnEntryWithoutOverviewAsEmptyUri() {
        when(repository.findByPersonAndInstitutionAndMainUriAndSecondaryUri(person, institution, "/action-unit/1", ""))
                .thenReturn(List.of());

        service.record(userInfo, visit("/action-unit/1", null));

        ArgumentCaptor<UserHistoryEntry> saved = ArgumentCaptor.forClass(UserHistoryEntry.class);
        verify(repository).save(saved.capture());
        assertEquals("/action-unit/1", saved.getValue().getMainUri());
        assertEquals("", saved.getValue().getSecondaryUri());
        assertEquals("Titre", saved.getValue().getMainTitle());
        assertSame(person, saved.getValue().getPerson());
    }

    @Test
    void record_samePageAgain_movesTheExistingEntryUpAndDropsDuplicates() {
        UserHistoryEntry first = new UserHistoryEntry();
        UserHistoryEntry duplicate = new UserHistoryEntry();
        when(repository.findByPersonAndInstitutionAndMainUriAndSecondaryUri(person, institution, "/a", "/b"))
                .thenReturn(List.of(first, duplicate));

        service.record(userInfo, visit("/a", "/b"));

        verify(repository).deleteAll(List.of(duplicate));
        verify(repository).save(first);
        assertEquals("Volet", first.getSecondaryTitle());
    }

    @Test
    void record_beyondTheLimit_dropsTheOldest() {
        when(repository.findByPersonAndInstitutionAndMainUriAndSecondaryUri(any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        when(repository.countByPersonAndInstitution(person, institution)).thenReturn((long) UserHistoryService.MAX_ENTRIES + 1);
        List<UserHistoryEntry> all = new ArrayList<>();
        for (int i = 0; i <= UserHistoryService.MAX_ENTRIES; i++) {
            all.add(new UserHistoryEntry());
        }
        when(repository.findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(eq(person), eq(institution), any(Pageable.class)))
                .thenReturn(all);

        service.record(userInfo, visit("/x", null));

        verify(repository).deleteAll(all.subList(UserHistoryService.MAX_ENTRIES, all.size()));
    }

    @Test
    void record_withoutPage_doesNothing() {
        service.record(userInfo, null);
        service.record(userInfo, visit(" ", null));

        verify(repository, never()).save(any());
        verifyNoInteractions(repository);
    }

    @Test
    void latest_asksForThePageSizeBoundedToWhatIsKept() {
        when(repository.findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(eq(person), eq(institution), any(Pageable.class)))
                .thenReturn(List.of());

        service.latest(userInfo, 1000);
        service.latest(userInfo, 0);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(eq(person), eq(institution), page.capture());
        assertEquals(UserHistoryService.MAX_ENTRIES, page.getAllValues().get(0).getPageSize());
        assertEquals(1, page.getAllValues().get(1).getPageSize());
    }
}
