package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Résout les libellés de concepts <strong>par lot</strong>, en deux requêtes pour une page entière.
 *
 * <p>{@link LabelService#findLabelOf} fait 1 à 2 requêtes <em>par concept</em> et n'est pas mis en cache :
 * sur une page de liste de projets où chaque ligne porte un type, un statut, un système, un état de terrain,
 * une nature d'aménagement, plus n périodes et n sujets, cela produit des centaines de requêtes. Cette classe
 * applique exactement la même règle de résolution — libellé préféré dans la langue demandée, sinon premier
 * libellé alternatif dans cette langue, sinon repli {@code [externalId]} — mais sur un {@code IN (...)}.</p>
 *
 * <p>Le résultat est un {@code Map<conceptId, label>} que les mappers consomment au lieu d'appeler
 * {@code LabelService}.</p>
 */
@Component
@RequiredArgsConstructor
public class ConceptLabelBatchResolver {

    private final ConceptLabelRepository conceptLabelRepository;

    /**
     * @param concepts concepts à résoudre ; les {@code null} et ceux sans id sont ignorés
     * @param langCode code langue ISO ; {@code fr} par défaut si vide
     * @return libellé par id de concept — jamais {@code null}, jamais de valeur {@code null}
     */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveLabels(Collection<ConceptDTO> concepts, String langCode) {
        String lang = (langCode == null || langCode.isBlank()) ? "fr" : langCode.trim().toLowerCase();

        Map<Long, ConceptDTO> byId = new HashMap<>();
        for (ConceptDTO concept : concepts) {
            if (concept != null) {
                byId.putIfAbsent(concept.getId(), concept);
            }
        }
        if (byId.isEmpty()) {
            return Map.of();
        }

        Set<Long> ids = new LinkedHashSet<>(byId.keySet());
        Map<Long, String> resolved = new HashMap<>();

        for (ConceptPrefLabel pref : conceptLabelRepository.findAllPrefLabelsByLangCodeAndConcept_IdIn(lang, ids)) {
            Long id = conceptIdOf(pref.getConcept());
            if (id != null) {
                resolved.putIfAbsent(id, pref.getLabel());
            }
        }

        // Second passage seulement pour ce qui n'a pas de libellé préféré, mais toujours en une requête :
        // filtrer côté base sur un sous-ensemble ne vaut pas un aller-retour supplémentaire ici.
        if (resolved.size() < ids.size()) {
            for (ConceptAltLabel alt : conceptLabelRepository.findAllAltLabelsByLangCodeAndConcept_IdIn(lang, ids)) {
                Long id = conceptIdOf(alt.getConcept());
                if (id != null) {
                    resolved.putIfAbsent(id, alt.getLabel());
                }
            }
        }

        // Repli identique à LabelService.findLabelOf : "[externalId]".
        for (Map.Entry<Long, ConceptDTO> entry : byId.entrySet()) {
            resolved.computeIfAbsent(entry.getKey(), id -> "[" + entry.getValue().getExternalId() + "]");
        }

        return resolved;
    }

    private static Long conceptIdOf(Object concept) {
        if (concept == null) return null;
        if (concept instanceof fr.siamois.domain.models.vocabulary.Concept c) return c.getId();
        return null;
    }

    /**
     * Libellé d'un concept déjà résolu par lot, avec le même repli que {@link #resolveLabels}. Permet aux
     * mappers de ne pas dupliquer la logique de repli quand un concept n'était pas dans le lot.
     */
    public static String labelOf(ConceptDTO concept, Map<Long, String> resolvedLabels) {
        if (concept == null) return null;
        String label = resolvedLabels.get(concept.getId());
        return Objects.requireNonNullElseGet(label, () -> "[" + concept.getExternalId() + "]");
    }
}
