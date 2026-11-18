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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pages a user opened, per organisation: the last {@value #MAX_ENTRIES} are kept, whatever the session
 * or the browser they were opened from.
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
        store(userInfo, visit, true);
    }

    /**
     * Records a page reached by walking through the history (Back/Forward, a click on its entry): a page
     * already listed keeps its rank, one that is not is added at the top.
     */
    @Transactional
    public void rememberInPlace(UserInfo userInfo, Visit visit) {
        store(userInfo, visit, false);
    }

    private void store(UserInfo userInfo, Visit visit, boolean moveUp) {
        if (visit == null || visit.mainUri() == null || visit.mainUri().isBlank()) {
            return;
        }
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());
        String secondaryUri = visit.secondaryUri() == null ? "" : visit.secondaryUri();

        List<UserHistoryEntry> existing = repository
                .findByPersonAndInstitutionAndMainUriAndSecondaryUri(person, institution, visit.mainUri(), secondaryUri);
        UserHistoryEntry entry = existing.isEmpty() ? new UserHistoryEntry() : existing.get(0);
        // Two requests may have recorded the same page at once: one entry is enough.
        if (existing.size() > 1) {
            repository.deleteAll(existing.subList(1, existing.size()));
        }
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
        if (moveUp || entry.getVisitedAt() == null) {
            entry.setVisitedAt(Instant.now());
        }
        repository.save(entry);

        if (repository.countByPersonAndInstitution(person, institution) > MAX_ENTRIES) {
            List<UserHistoryEntry> all = repository.findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(
                    person, institution, PageRequest.of(0, MAX_ENTRIES * 2));
            if (all.size() > MAX_ENTRIES) {
                repository.deleteAll(all.subList(MAX_ENTRIES, all.size()));
            }
        }
    }

    /**
     * The user's latest pages in the organisation, the most recent first. A page whose entity or saved view
     * was deleted since is left out, be it the main pane's or the overview's.
     */
    @Transactional(readOnly = true)
    public List<UserHistoryEntry> latest(UserInfo userInfo, int limit) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());
        int size = Math.max(1, Math.min(limit, MAX_ENTRIES));
        List<UserHistoryEntry> kept = repository.findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(
                person, institution, PageRequest.of(0, MAX_ENTRIES));
        Set<String> uris = new HashSet<>();
        kept.forEach(entry -> {
            uris.add(entry.getMainUri());
            uris.add(entry.getSecondaryUri());
        });
        Set<String> missing = pageTargetService.missingAmong(uris);
        return kept.stream()
                .filter(entry -> !missing.contains(entry.getMainUri()) && !missing.contains(entry.getSecondaryUri()))
                .limit(size)
                .toList();
    }
}
