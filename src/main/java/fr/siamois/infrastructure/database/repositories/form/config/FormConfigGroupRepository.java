package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.form.config.FormConfigGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormConfigGroupRepository extends JpaRepository<FormConfigGroup, Long> {

    List<FormConfigGroup> findAllByFormConfigIdOrderByPosition(Long formConfigId);

    long countByFormConfigId(Long formConfigId);
}
