package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.PersonDTO;

import java.util.Set;

/**
 * Utilisateur authentifié et périmètre d'institutions pour les endpoints projet OpenAPI.
 */
public record ProjectApiCaller(PersonDTO person, Set<Long> accessibleInstitutionIds) {
}
