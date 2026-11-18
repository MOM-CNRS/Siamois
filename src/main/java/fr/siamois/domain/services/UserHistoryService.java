package fr.siamois.domain.services;

import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.UserHistoryRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pages a user opened, per organisation (the fiches opened full screen, the home page and the tables opened
 * from it): the last {@value #MAX_ENTRIES} are kept, whatever the session or the browser they were opened from.
 */
@Service
@RequiredArgsConstructor
public class UserHistoryService {

    public static final int MAX_ENTRIES = 50;

    private final UserHistoryRepository repository;
    private final PersonMapper personMapper;
    private final InstitutionMapper institutionMapper;
    private final PageTargetService pageTargetService;

    /** A visited page: the main pane, and the overview open next to it (nulls when there is none). */
    public record Visit(String mainUri, String mainTitle, String mainIcon, String mainStyleClass,
                        String secondaryUri, String secondaryTitle, String secondaryIcon, String secondaryStyleClass) {
    }

    /**
     * Puts the visit at the top of the user's history, and drops what falls beyond {@value #MAX_ENTRIES}.
     */
    @Transactional
    public void remember(UserInfo userInfo, Visit visit) {
        if (visit == null || visit.mainUri() == null || visit.mainUri().isBlank()) {
            return;
        }
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());
        String secondaryUri = visit.secondaryUri() == null ? "" : visit.secondaryUri();

        List<UserHistoryEntry> existing = repository
                .findByPersonAndInstitutionAndMainUriAndSecondaryUri(person, institution, visit.mainUri(), secondaryUri);
        // The time of the visit is part of the key: moving a page up replaces its entry (or its entries, when two
        // requests recorded it at once).
        repository.deleteAll(existing);

        UserHistoryEntry entry = new UserHistoryEntry();
        entry.setPerson(person);
        entry.setInstitution(institution);
        entry.setMainUri(visit.mainUri());
        entry.setMainTitle(visit.mainTitle());
        entry.setMainIcon(visit.mainIcon());
        entry.setMainStyleClass(visit.mainStyleClass());
        entry.setSecondaryUri(secondaryUri);
        entry.setSecondaryTitle(visit.secondaryTitle());
        entry.setSecondaryIcon(visit.secondaryIcon());
        entry.setSecondaryStyleClass(visit.secondaryStyleClass());
        // What the database keeps of it: the key read back must be the one written.
        entry.setVisitedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.save(entry);

        if (repository.countByPersonAndInstitution(person, institution) > MAX_ENTRIES) {
            List<UserHistoryEntry> all = repository.findByPersonAndInstitutionOrderByIdVisitedAtDesc(
                    person, institution, PageRequest.of(0, MAX_ENTRIES * 2));
            if (all.size() > MAX_ENTRIES) {
                repository.deleteAll(all.subList(MAX_ENTRIES, all.size()));
            }
        }
    }

    /**
     * The pages the user latest opened in the organisation, the most recent first. The fiches older versions
     * recorded as opened in the overview are left out, and so is a page whose entity or saved view was deleted.
     */
    @Transactional(readOnly = true)
    public List<UserHistoryEntry> latest(UserInfo userInfo, int limit) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());
        int size = Math.max(1, Math.min(limit, MAX_ENTRIES));
        List<UserHistoryEntry> kept = repository.findByPersonAndInstitutionOrderByIdVisitedAtDesc(
                person, institution, PageRequest.of(0, MAX_ENTRIES));
        List<UserHistoryEntry> pages = kept.stream().filter(entry -> entry.getSecondaryUri().isEmpty()).toList();
        Set<String> missing = pageTargetService.missingAmong(
                pages.stream().map(UserHistoryEntry::getMainUri).collect(Collectors.toSet()));
        return pages.stream()
                .filter(entry -> !missing.contains(entry.getMainUri()))
                .limit(size)
                .toList();
    }
}
