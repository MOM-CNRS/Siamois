package fr.siamois.infrastructure.database.repositories.vocabulary.label;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LocalizedAltConceptLabel;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalizedAltConceptLabelRepository extends CrudRepository<LocalizedAltConceptLabel, ConceptLabel.Id> {

    @Query(
            nativeQuery = true,
            value = "SELECT lacl.* FROM localized_alt_concept_label lacl " +
                    "WHERE lacl.fk_field_parent_concept_id = :fieldConceptId " +
                    "AND lacl.lang_code = :langCode " +
                    "AND unaccent(lacl.label) ILIKE unaccent('%' || :input || '%') " +
                    "LIMIT :limit"
    )
    List<LocalizedAltConceptLabel> findAllByParentConceptAndInputLimited(Long fieldConceptId, String langCode, String input, int limit);
}
