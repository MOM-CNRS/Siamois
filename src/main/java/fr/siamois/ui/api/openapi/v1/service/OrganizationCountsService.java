package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationCountsResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compteurs des cartes « Accéder aux bases de données » de l'accueil — mêmes appels que
 * {@code WelcomePanel} côté JSF, sauf les contenants, que le JSF affiche à 0 en dur.
 */
@Service
@RequiredArgsConstructor
public class OrganizationCountsService {

    private final ProjectApiService projectApiService;
    private final ActionUnitService actionUnitService;
    private final SpatialUnitService spatialUnitService;
    private final RecordingUnitService recordingUnitService;
    private final SpecimenService specimenService;
    private final PhaseService phaseService;
    private final ContainerService containerService;

    @Transactional(readOnly = true)
    public OrganizationCountsResource countsForOrganization(ProjectApiCaller caller, Long organizationId) {
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        Long id = institution.getId();
        return new OrganizationCountsResource(
                actionUnitService.countByInstitutionId(id),
                spatialUnitService.countByInstitutionId(id),
                recordingUnitService.countByInstitutionId(id),
                specimenService.countByInstitution(institution),
                phaseService.countSearchResults(institution, new FilterDTO()),
                containerService.countSearchResults(institution, new FilterDTO()));
    }
}
