package fr.siamois.infrastructure.api.dto;

public record ConceptRemoteAutocompleteDTO(Long identifier, String uri, String label, Boolean isAltLabel,
                                           String definition) {
}
