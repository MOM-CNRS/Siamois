package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.ConceptFieldConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptFieldConfigRepository extends CrudRepository<ConceptFieldConfig, Long> {

    Optional<ConceptFieldConfig> findOneByVocabularyIdAndFieldCode(Long vocabularyId, String fieldCode);

    List<ConceptFieldConfig> findAllByVocabularyId(Long vocabularyId);

    @Query("SELECT DISTINCT cfc.fieldCode " +
            "FROM ConceptFieldConfig cfc " +
            "WHERE cfc.vocabulary.id IN :vocabularyIds " +
            "ORDER BY cfc.fieldCode")
    List<String> findDistinctFieldCodesOf(@Param("vocabularyIds") Collection<Long> vocabularyIds);
}
