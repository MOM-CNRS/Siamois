package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;

import java.util.List;
import java.util.Set;

/**
 * Utilisateur authentifié et périmètre d'institutions pour les endpoints projet OpenAPI.
 * Porte la liste complète des institutions pour éviter un second appel en base lors de la pagination.
 */
public record ProjectApiCaller(PersonDTO person, Set<Long> accessibleInstitutionIds, List<InstitutionDTO> institutions) {
}
