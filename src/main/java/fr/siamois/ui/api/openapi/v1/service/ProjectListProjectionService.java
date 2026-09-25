package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce qu'une page de liste de projets doit résoudre <strong>par lot</strong>, en plus des lignes elles-mêmes :
 * les libellés de concepts et la projection {@code answers} demandée via {@code ?fields=}.
 *
 * <p>Composant dédié plutôt qu'une méthode de plus sur {@link ProjectApiService} : ce dernier a déjà seize
 * dépendances et est instancié à la main par huit classes de test, qu'un argument supplémentaire casserait
 * à chaque fois.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectListProjectionService {

    private final ProjectAnswersProjector projectAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;

    /**
     * @param resolvedLabels libellé par id de concept, couvrant à la fois le type de chaque projet et les
     *                       concepts des champs projetés — un seul lot, pas un par usage
     * @param answersByProjectId {@code answers} par id de projet ; vide si {@code ?fields=} est absent
     */
    public record ProjectListProjection(Map<Long, String> resolvedLabels,
                                        Map<Long, Map<String, Object>> answersByProjectId) {

        public static ProjectListProjection empty() {
            return new ProjectListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long projectId) {
            return answersByProjectId.get(projectId);
        }
    }

    /**
     * @param fieldsParam {@code all}, {@code default}, une liste d'ids de champs séparés par des virgules,
     *                    ou {@code null}/vide pour ne rien projeter — auquel cas les réponses n'ont pas de
     *                    clé {@code answers} et la liste reste au coût qu'elle avait avant.
     */
    public ProjectListProjection build(Collection<AccessibleProjectForApi> rows, String fieldsParam, String lang) {
        if (rows == null || rows.isEmpty()) {
            return ProjectListProjection.empty();
        }
        List<ActionUnitDTO> dtos = rows.stream().map(AccessibleProjectForApi::actionUnit).toList();
        Set<String> fieldIds = projectAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        // Un seul lot de libellés pour toute la page : le type de chaque projet, que le mapper résolvait
        // jusqu'ici concept par concept, et les concepts des champs projetés.
        List<ConceptDTO> concepts = new ArrayList<>(projectAnswersProjector.collectConcepts(dtos, fieldIds));
        for (ActionUnitDTO dto : dtos) {
            if (dto != null && dto.getType() != null) {
                concepts.add(dto.getType());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        return new ProjectListProjection(labels, projectAnswersProjector.project(dtos, fieldIds, labels));
    }
}
