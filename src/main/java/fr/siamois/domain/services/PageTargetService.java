package fr.siamois.domain.services;

import fr.siamois.infrastructure.database.repositories.PageTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells which pages of the app ({@code /recording-unit/12}, {@code /recording-unit?viewId=3}) no longer lead
 * anywhere, their entity or saved view having been deleted. A page about neither (a list, the home page) always
 * leads somewhere.
 */
@Service
@RequiredArgsConstructor
public class PageTargetService {

    private static final Pattern ENTITY_PAGE = Pattern.compile("/?([a-z-]+)/(\\d{1,18})(?:\\?.*)?");
    private static final Pattern SAVED_VIEW_PAGE = Pattern.compile("/?[a-z-]+\\?(?:.*&)?viewId=(\\d{1,18})(?:&.*)?");

    private final PageTargetRepository repository;

    private record PageTarget(String kind, Long id) {
    }

    /**
     * Whether this page is the fiche of one entity.
     */
    public boolean isFiche(String uri) {
        Matcher entityPage = ENTITY_PAGE.matcher(uri);
        return entityPage.matches() && repository.isKnownKind(entityPage.group(1));
    }

    /**
     * Whether the entity or saved view this page is about was deleted.
     */
    @Transactional(readOnly = true)
    public boolean isMissing(String uri) {
        return !missingAmong(Set.of(uri)).isEmpty();
    }

    /**
     * Those of these pages whose entity or saved view was deleted.
     */
    @Transactional(readOnly = true)
    public Set<String> missingAmong(Collection<String> uris) {
        Map<String, PageTarget> targets = new HashMap<>();
        Map<String, Set<Long>> idsByKind = new HashMap<>();
        for (String uri : uris) {
            targetOf(uri).ifPresent(target -> {
                targets.put(uri, target);
                idsByKind.computeIfAbsent(target.kind(), kind -> new HashSet<>()).add(target.id());
            });
        }

        Map<String, Set<Long>> existing = new HashMap<>();
        idsByKind.forEach((kind, ids) -> existing.put(kind, repository.existingIds(kind, ids)));

        Set<String> missing = new HashSet<>();
        targets.forEach((uri, target) -> {
            if (!existing.get(target.kind()).contains(target.id())) {
                missing.add(uri);
            }
        });
        return missing;
    }

    private Optional<PageTarget> targetOf(String uri) {
        Matcher entityPage = ENTITY_PAGE.matcher(uri);
        if (entityPage.matches()) {
            return repository.isKnownKind(entityPage.group(1))
                    ? Optional.of(new PageTarget(entityPage.group(1), Long.valueOf(entityPage.group(2))))
                    : Optional.empty();
        }
        Matcher savedViewPage = SAVED_VIEW_PAGE.matcher(uri);
        return savedViewPage.matches()
                ? Optional.of(new PageTarget(PageTargetRepository.SAVED_VIEW, Long.valueOf(savedViewPage.group(1))))
                : Optional.empty();
    }
}
