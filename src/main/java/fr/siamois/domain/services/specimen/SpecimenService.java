package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.SpecimenMapper;
import fr.siamois.mapper.SpecimenSummaryMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static fr.siamois.domain.models.ValidationStatus.*;

@Service
@RequiredArgsConstructor
public class SpecimenService implements ArkEntityService {

    private final SpecimenRepository specimenRepository;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenMapper specimenMapper;
    private final SpecimenSummaryMapper specimenSummaryMapper;
    private final InstitutionMapper institutionMapper;


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

    private Specimen newOrGetSpecimen(Specimen specimen) {
        Specimen managedSpecimen;
        if (specimen.getId() != null) {
            Optional<Specimen> optRecordingUnit = specimenRepository.findById(specimen.getId());
            managedSpecimen = optRecordingUnit.orElse(specimen);
        } else {
            managedSpecimen = specimen;
        }
        return managedSpecimen;
    }

    private void setupChilds(Specimen specimen, Specimen managedSpecimen) {
        if (specimen.getChildren() == null) {
            return;
        }

        // 1. Get the IDs of the children we WANT to have
        Set<Long> incomingIds = specimen.getChildren().stream()
                .filter(c -> c != null && c.getId() != null)
                .map(Specimen::getId)
                .collect(Collectors.toSet());

        // 2. Remove children no longer in the list
        managedSpecimen.getChildren().removeIf(child ->
                !incomingIds.contains(child.getId()));

        // 3. Add new children
        for (Long id : incomingIds) {
            boolean alreadyPresent = managedSpecimen.getChildren().stream()
                    .anyMatch(c -> c.getId().equals(id));

            if (!alreadyPresent) {
                Specimen child = specimenRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Child not found: " + id));
                managedSpecimen.getChildren().add(child);
            }
        }
    }

    private void setupParents(Specimen specimen, Specimen managedSpecimen) {
        if (specimen.getParents() == null) {
            return;
        }

        // Fetch current parents of managedSpecimen
        Set<Specimen> currentParents = new HashSet<>(managedSpecimen.getParents());
        Set<Specimen> newParents = new HashSet<>();

        // Build the set of new parents
        for (Specimen parentRef : specimen.getParents()) {
            Specimen parent = specimenRepository.findById(parentRef.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent not found: " + parentRef.getId()));
            newParents.add(parent);
        }

        // Find parents to add (in newParents but not in currentParents)
        for (Specimen parent : newParents) {
            if (!currentParents.contains(parent)) {
                parent.getChildren().add(managedSpecimen);
                specimenRepository.save(parent);
            }
        }

        // Find parents to remove (in currentParents but not in newParents)
        for (Specimen parent : currentParents) {
            if (!newParents.contains(parent)) {
                parent.getChildren().remove(managedSpecimen);
                specimenRepository.save(parent);
            }
        }

        // Update the parents list of managedSpecimen
        managedSpecimen.getParents().clear();
        managedSpecimen.getParents().addAll(newParents);
    }

    /**
     * Synchronize collection
     *
     * @param managedCollection La collection issue de l'entité managée (ex: managedSpecimen.getMaterialClass())
     * @param newCollection     La collection contenant les nouvelles données (ex: specimen.getMaterialClass())
     * @param <T>               Le type de l'entité (ici Concept)
     */
    private <T> void synchronizeCollection(Collection<T> managedCollection, Collection<T> newCollection) {
        if (managedCollection == null) {
            return; // Sécurité si la collection de l'entité n'est pas initialisée
        }

        // 1. Si la nouvelle collection est nulle ou vide, on vide simplement la collection managée
        if (newCollection == null || newCollection.isEmpty()) {
            managedCollection.clear();
            return;
        }

        // 2. Supprimer les éléments qui ne sont plus présents dans la nouvelle collection
        managedCollection.retainAll(newCollection);

        // 3. Ajouter les éléments de la nouvelle collection qui n'étaient pas encore présents
        for (T element : newCollection) {
            if (!managedCollection.contains(element)) {
                managedCollection.add(element);
            }
        }
    }

    private static void setupOtherFields(Specimen specimen, Specimen managedSpecimen) {

        managedSpecimen.setArk(specimen.getArk());
        managedSpecimen.setDescription(specimen.getDescription());
        managedSpecimen.setCollectors(specimen.getCollectors());
        managedSpecimen.setCollectionDate(specimen.getCollectionDate());
        managedSpecimen.setWeight(specimen.getWeight());
        managedSpecimen.setNormalizedInterpretation(specimen.getNormalizedInterpretation());
        managedSpecimen.setValidated(specimen.getValidated());
        managedSpecimen.setValidatedAt(specimen.getValidatedAt());
        managedSpecimen.setValidatedBy(specimen.getValidatedBy());
        managedSpecimen.setTaq(specimen.getTaq());
        managedSpecimen.setTpq(specimen.getTpq());
        managedSpecimen.setComments(specimen.getComments());
        managedSpecimen.setOtherIdentifier(specimen.getOtherIdentifier());
        managedSpecimen.setCategory(specimen.getCategory());
        managedSpecimen.setIsolationNumber(specimen.getIsolationNumber());
        managedSpecimen.setWeight(specimen.getWeight());
        managedSpecimen.setNumberOfElements(specimen.getNumberOfElements());

        if (managedSpecimen.getCreatedBy() == null) {
            managedSpecimen.setCreatedBy(specimen.getCreatedBy());
        }


        managedSpecimen.setChronologicalAttribution(specimen.getChronologicalAttribution());

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
            toSave.setFullIdentifier(toSave.getRecordingUnit().getFullIdentifier()+"_"+toSave.getIdentifier().toString());
        }

        // Convertir SpecimenDTO en Specimen
        Specimen specimen = specimenMapper.invertConvert(toSave);

        Specimen managedSpecimen = newOrGetSpecimen(specimen);

        setupParents(specimen, managedSpecimen);
        setupChilds(specimen, managedSpecimen);
        setupOtherFields(specimen, managedSpecimen);

        synchronizeCollection(managedSpecimen.getMaterialClass(), specimen.getMaterialClass());
        synchronizeCollection(managedSpecimen.getMaterial(), specimen.getMaterial());

        // Sauvegarder l'entité Specimen
        managedSpecimen = specimenRepository.save(managedSpecimen);

        // Convertir l'entité sauvegardée en SpecimenDTO et la retourner
        return specimenMapper.convert(managedSpecimen);
    }

    @Override
    public AbstractEntityDTO save(AbstractEntityDTO abstractEntityDTO) {
        Specimen specimen = specimenMapper.invertConvert((SpecimenDTO) abstractEntityDTO);
        Specimen saved = specimenRepository.save(specimen);
        return specimenMapper.convert(saved);
    }

    /**
     * Finds a specimen by its ID.
     *
     * @param id the ID of the specimen to find
     * @return the specimen if found, or null if not found
     */
    @Transactional(readOnly = true)
    public SpecimenDTO findById(Long id) {
        Specimen specimen = specimenRepository.findById(id, Specimen.class).orElse(null);
        return specimen != null ? specimenMapper.convert(specimen) : null;
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

        return specimenPage.map(specimenMapper::convert);
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

        return specimenPage.map(specimenMapper::convert);
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

        return specimenPage.map(specimenMapper::convert);
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

        return specimenPage.map(specimenMapper::convert);
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
        Institution institutionEntity = institutionMapper.invertConvert(institution);
        return specimenRepository.countByCreatedByInstitution(institutionEntity);
    }



    public Integer countByActionContext(ActionUnitDTO actionUnit) {
        return specimenRepository.countByActionContext(actionUnit.getId());
    }

    /**
     * Find the next SpecimenDTO created by a specific action after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param recordingUnit The recordingUnit to find ActionUnits for
     * @param current The current SpecimenDTO to find the next one from
     * @return The next ActionUnitDTO, or the oldest one if there is no next
     */
    public SpecimenDTO findNextByActionUnit(RecordingUnitSummaryDTO recordingUnit, SpecimenDTO current) {
        return specimenRepository
                .findFirstByRecordingUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        recordingUnit.getId(), current.getCreationTime())
                .map(specimenMapper::convert)
                .orElseGet(() -> specimenRepository
                        .findFirstByRecordingUnitIdOrderByCreationTimeAsc(recordingUnit.getId())
                        .map(specimenMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + recordingUnit.getId()))
                );
    }

    /**
     * Find the previous SpecimenDTO created by a specific action before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param recordingUnit The ru to find SpecimenDTO for
     * @param current The current ActionUnit to find the previous one from
     * @return The previous ActionUnitDTO, or the most recent one if there is no previous
     */
    public SpecimenDTO findPreviousByActionUnit(RecordingUnitSummaryDTO recordingUnit,
                                                SpecimenDTO current) {
        return specimenRepository
                .findFirstByRecordingUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        recordingUnit.getId(), current.getCreationTime())
                .map(specimenMapper::convert)
                .orElseGet(() -> specimenRepository
                        .findFirstByRecordingUnitIdOrderByCreationTimeDesc(recordingUnit.getId())
                        .map(specimenMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + recordingUnit.getId()))
                );
    }

    /**
     * Toggle the validated status of an RecordingUnit.
     *
     * @param id The id of the Recording unit to toggle
     * @return The updated RecordingUnitDTO
     */
    public SpecimenDTO toggleValidated(Long id) {
        Specimen unit = specimenRepository.findById(id)
                .orElseThrow(() -> new ActionUnitNotFoundException("ActionUnit not found with id: " + id));

        // Cycle through the enum values
        switch (unit.getValidated()) {
            case INCOMPLETE:
                unit.setValidated(COMPLETE);
                break;
            case COMPLETE:
                unit.setValidated(VALIDATED);
                break;
            case VALIDATED:
                unit.setValidated(INCOMPLETE);
                break;
            default:
                throw new IllegalStateException("Unknown status: " + unit.getValidated());
        }

        return specimenMapper.convert(specimenRepository.save(unit));
    }

    public List<SpecimenSummaryDTO> findAllByActionUnit(@NotNull Long recordingUnitId) {
        
        Long id = recordingUnitRepository.findById(recordingUnitId).get().getActionUnit().getId();
        return specimenRepository
                .findFirst10ByActionUnitId(id)
                .stream()
                .map(specimenSummaryMapper::convert)
                .toList();
    }

}
