package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Which fields are a side of a configurable relation, kept in memory: every list and fiche asks it of the fields
 * it projects, and the definitions are few and seldom change. Reloaded at most every {@link #TTL}, and at once on
 * this instance when {@link RelationDefinitionService} changes a definition.
 */
@Service
@RequiredArgsConstructor
public class RelationFieldIndex {

    /** How long another instance of the application may serve a definition change late. */
    static final Duration TTL = Duration.ofMinutes(1);

    /** A field's side of a definition. */
    public record Side(long definitionId, RelationSide side, boolean symmetric, boolean complex) {
    }

    private final RelationDefinitionRepository definitionRepository;
    private final Clock clock = Clock.systemUTC();

    /** The loaded sides and when they were loaded, replaced as a whole. */
    private record Snapshot(Map<Long, Side> sides, Instant loadedAt) {
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    /** The fields that are a side of a definition, by field id. */
    @Transactional(readOnly = true)
    public Map<Long, Side> sides() {
        Snapshot current = snapshot.get();
        Instant now = Instant.now(clock);
        if (current == null || now.isAfter(current.loadedAt().plus(TTL))) {
            current = new Snapshot(load(), now);
            snapshot.set(current);
        }
        return current.sides();
    }

    public void evict() {
        snapshot.set(null);
    }

    private Map<Long, Side> load() {
        Map<Long, Side> out = new HashMap<>();
        for (RelationDefinition definition : definitionRepository.findAll()) {
            boolean complex = !definition.isSimple();
            out.put(definition.getSourceField().getId(), new Side(definition.getId(), RelationSide.SOURCE,
                    definition.isSymmetric(), complex));
            if (!definition.isSymmetric()) {
                out.put(definition.getTargetField().getId(), new Side(definition.getId(), RelationSide.TARGET,
                        false, complex));
            }
        }
        return Map.copyOf(out);
    }
}
