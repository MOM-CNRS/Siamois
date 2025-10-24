package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FieldRepository extends CrudRepository<CustomField, Long> {

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "UPDATE concept_field_config " +
                    "SET existing_hash = :hash " +
                    "WHERE config_id = :fieldConfigId"
    )
    void updateChecksumForFieldConfig(Long fieldConfigId, String hash);
}
