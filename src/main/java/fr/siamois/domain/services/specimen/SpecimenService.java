package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.dto.entity.*;
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
    public int generateNextIdentifier(SpecimenDTO specimen) {
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
    public SpecimenDTO save(SpecimenDTO toSave) {

        if (toSave.getFullIdentifier() == null) {
            if (toSave.getIdentifier() == null) {

                toSave.setIdentifier(generateNextIdentifier(toSave));
            }
            // Set full identifier
            toSave.setFullIdentifier(toSave.getFullIdentifier());
        }

        // Convertir SpecimenDTO en Specimen
        Specimen specimen = conversionService.convert(toSave, Specimen.class);

        // Sauvegarder l'entité Specimen
        Specimen savedSpecimen = specimenRepository.save(specimen);

        // Convertir l'entité sauvegardée en SpecimenDTO et la retourner
        return conversionService.convert(savedSpecimen, SpecimenDTO.class);
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
    @Transactional(readOnly = true)
    public SpecimenDTO findById(Long id) {
        Specimen specimen = specimenRepository.findById(id).orElse(null);
        return specimen != null ? conversionService.convert(specimen, SpecimenDTO.class) : null;
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
    @Transactional(readOnly = true)
    public Page<SpecimenDTO> findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<Specimen> specimenPage = specimenRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, fullIdentifier, categoryIds, global, langCode, pageable
        );

        return specimenPage.map(specimen -> conversionService.convert(specimen, SpecimenDTO.class));
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
    @Transactional(readOnly = true)
    public Page<SpecimenDTO> findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            Long recordingUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<Specimen> specimenPage = specimenRepository.findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );

        return specimenPage.map(specimen -> conversionService.convert(specimen, SpecimenDTO.class));
    }


    @Transactional(readOnly = true)
    public Page<SpecimenDTO> findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long spatialUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<Specimen> specimenPage = specimenRepository.findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );

        return specimenPage.map(specimen -> conversionService.convert(specimen, SpecimenDTO.class));
    }


    @Transactional(readOnly = true)
    public Page<SpecimenDTO> findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long actionUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<Specimen> specimenPage = specimenRepository.findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                actionUnitId, fullIdentifier, categoryIds, global, langCode, pageable
        );

        return specimenPage.map(specimen -> conversionService.convert(specimen, SpecimenDTO.class));
    }


    /**
     * Updates the type of multiple specimens in bulk.
     *
     * @param ids  the list of IDs of the specimens to update
     * @param type the new type to set for the specimens
     * @return the number of specimens updated
     */
    @Transactional
    public int bulkUpdateType(List<Long> ids, ConceptDTO type) {
        return specimenRepository.updateTypeByIds(type.getId(), ids);
    }

    /**
     * Counts the number of specimens created by a specific institution.
     *
     * @param institution the institution for which to count the specimens
     * @return the count of specimens created by the institution
     */
    public long countByInstitution(InstitutionDTO institution) {
        Institution institutionEntity = conversionService.convert(institution, Institution.class);
        return specimenRepository.countByCreatedByInstitution(institutionEntity);
    }



    public Integer countByActionContext(ActionUnitDTO actionUnit) {
        return specimenRepository.countByActionContext(actionUnit.getId());
    }

}
