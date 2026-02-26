package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpecimenService implements ArkEntityService {

    private final SpecimenRepository specimenRepository;
    private final ConversionService conversionService;

    public SpecimenService(SpecimenRepository specimenRepository, ConversionService conversionService) {
        this.specimenRepository = specimenRepository;
        this.conversionService = conversionService;
    }

    @Override
    public List<Specimen> findWithoutArk(Institution institution) {
        return specimenRepository.findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    /**
     * Generates the next identifier for a specimen based on the maximum identifier used in the recording unit.
     *
     * @param specimen the specimen for which the identifier is being generated
     * @return the next identifier to be used for the specimen
     */
    public int generateNextIdentifier(Specimen specimen) {
        // Generate next identifier
        Integer currentMaxIdentifier = specimenRepository.findMaxUsedIdentifierByRecordingUnit(specimen.getRecordingUnit().getId());
        return ((currentMaxIdentifier == null) ? 1 : currentMaxIdentifier + 1);
    }

    /**
     * Saves a specimen to the repository.
     *
     * @param toSave the specimen to save
     * @return the saved specimen
     */
    public Specimen save(Specimen toSave) {

        if (toSave.getFullIdentifier() == null) {
            if (toSave.getIdentifier() == null) {

                toSave.setIdentifier(generateNextIdentifier(toSave));
            }
            // Set full identifier
            toSave.setFullIdentifier(toSave.displayFullIdentifier());
        }

        return specimenRepository.save(toSave);
    }

    @Override
    public AbstractEntityDTO save(AbstractEntityDTO abstractEntityDTO) {
        Specimen specimen = conversionService.convert(abstractEntityDTO, Specimen.class);
        Specimen saved = specimenRepository.save(specimen);
        return conversionService.convert(saved, abstractEntityDTO.getClass());
    }

    /**
     * Finds a specimen by its ID.
     *
     * @param id the ID of the specimen to find
     * @return the specimen if found, or null if not found
     */
    @Transactional
    public Specimen findById(Long id) {
        return specimenRepository.findById(id).orElse(null);
    }

    /**
     * Finds all specimens by institution and full identifier containing the specified string,
     *
     * @param institutionId  the ID of the institution to filter by
     * @param fullIdentifier the string to search for in the full identifier of the specimens
     * @param categoryIds    the IDs of the categories to filter by
     * @param global         the global search string to filter by
     * @param langCode       the language code for localization
     * @param pageable       the pagination information
     * @return a page of specimens matching the criteria
     */
    @Transactional
    public Page<Specimen> findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        return specimenRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, fullIdentifier, categoryIds, global, langCode, pageable
        );
    }

    /**
     * Finds all specimens by institution, recording unit, full identifier containing the specified string,
     *
     * @param institutionId   the ID of the institution to filter by
     * @param recordingUnitId the ID of the recording unit to filter by
     * @param fullIdentifier  the string to search for in the full identifier of the specimens
     * @param categoryIds     the IDs of the categories to filter by
     * @param global          the global search string to filter by
     * @param langCode        the language code for localization
     * @param pageable        the pagination information
     * @return a page of specimens matching the criteria
     */
    @Transactional
    public Page<Specimen> findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            Long recordingUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        return specimenRepository.findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );
    }

    @Transactional
    public Page<Specimen> findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long spatialUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        return specimenRepository.findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );
    }

    @Transactional
    public Page<Specimen> findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long actionUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ){
        return specimenRepository.findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                actionUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );
    }

    /**
     * Updates the type of multiple specimens in bulk.
     *
     * @param ids  the list of IDs of the specimens to update
     * @param type the new type to set for the specimens
     * @return the number of specimens updated
     */
    @Transactional
    public int bulkUpdateType(List<Long> ids, Concept type) {
        return specimenRepository.updateTypeByIds(type.getId(), ids);
    }

    /**
     * Counts the number of specimens created by a specific institution.
     *
     * @param institution the institution for which to count the specimens
     * @return the count of specimens created by the institution
     */
    public long countByInstitution(Institution institution) {
        return specimenRepository.countByCreatedByInstitution(institution);
    }

    public Integer countBySpatialContext(SpatialUnit spatialUnit) {
        return specimenRepository.countBySpatialContext(spatialUnit.getId());
    }

    public Integer countByActionContext(ActionUnit actionUnit) {
        return specimenRepository.countByActionContext(actionUnit.getId());
    }

}
