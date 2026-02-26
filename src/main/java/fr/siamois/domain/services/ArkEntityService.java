package fr.siamois.domain.services;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.AbstractEntityDTO;
import org.springframework.core.convert.ConversionService;

import java.util.List;

/**
 * Service interface for managing ArkEntities.
 * This service provides methods to find ArkEntities that are not associated with an Ark,
 * and to save an ArkEntity.
 */
public interface ArkEntityService {



    /**
     * Finds all ArkEntities that are not associated with an Ark in the specified institution.
     *
     * @param institution the institution to search within
     * @return a list of ArkEntities without an associated Ark
     */
    List<? extends ArkEntity> findWithoutArk(Institution institution);

    /**
     * Saves the given ArkEntity.
     *
     * @param toSave the ArkEntity to save
     * @return the saved ArkEntity
     */
    AbstractEntityDTO save(AbstractEntityDTO toSave);
}
