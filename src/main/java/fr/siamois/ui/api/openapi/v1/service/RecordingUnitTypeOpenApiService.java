package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Liste des types d'UE (concepts SIARU.TYPE) pour une organisation, alignée sur le vocabulaire métier.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitTypeOpenApiService {

    private final InstitutionService institutionService;
    private final FieldConfigurationService fieldConfigurationService;

    @Transactional(readOnly = true)
    public List<ConceptAutocompleteDTO> listRecordingUnitTypes(long organizationId,
                                                               PersonDTO personDto,
                                                               String lang,
                                                               String query) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        String input = query == null ? null : query.trim();
        if (input != null && input.isEmpty()) {
            input = null;
        }
        try {
            return fieldConfigurationService.fetchAutocomplete(userInfo, RecordingUnit.TYPE_FIELD_CODE, input);
        } catch (NoConfigForFieldException e) {
            return List.of();
        }
    }
}
