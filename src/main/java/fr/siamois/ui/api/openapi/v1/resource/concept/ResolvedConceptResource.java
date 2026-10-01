package fr.siamois.ui.api.openapi.v1.resource.concept;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "Concept résolu dans une langue donnée")
public class ResolvedConceptResource extends ConceptResourceIdentifier {

    @Schema(description = "URL externe", example = "http://thesaurus.mom.fr/concept/234")
    private String externalUrl;

    @Schema(description = "Libellé préférentiel dans la langue demandée")
    private String resolvedLabel;

    @Schema(description = "Libellés alternatifs")
    private List<String> altLabels;

    @Schema(description = "Définition")
    private String definition;

    @Schema(description = "Lien vers le concept dans le thésaurus (Opentheso)")
    private String thesaurusUrl;

    @Schema(description = "Libellé préférentiel d'origine, renseigné seulement quand resolvedLabel est un libellé alternatif")
    private String prefLabel;

    @Schema(description = "Libellés préférentiels des parents du concept")
    private String parents;

    public static ResolvedConceptResource from(ConceptAutocompleteDTO dto) {
        ResolvedConceptResource r = new ResolvedConceptResource();
        r.setResourceType("concepts");

        if (dto.getConceptLabelToDisplay() != null) {
            r.setResolvedLabel(dto.getConceptLabelToDisplay().getLabel());
            if (dto.getConceptLabelToDisplay().getConcept() != null) {
                r.setId(String.valueOf(dto.getConceptLabelToDisplay().getConcept().getId()));
                r.setExternalUrl(dto.getConceptLabelToDisplay().getConcept().getExternalId());
            }
        }

        if (dto.getConceptLabelToDisplay() != null && dto.getConceptLabelToDisplay().isAltLabel()) {
            r.setPrefLabel(dto.getOriginalPrefLabel());
        }
        if (dto.getHierarchyPrefLabels() != null && !dto.getHierarchyPrefLabels().isBlank()) {
            r.setParents(dto.getHierarchyPrefLabels());
        }
        r.setThesaurusUrl(thesaurusUrlOf(dto));

        r.setAltLabels(dto.getAltLabels());
        r.setDefinition(dto.getDefinition());
        return r;
    }

    private static String thesaurusUrlOf(ConceptAutocompleteDTO dto) {
        ConceptDTO concept = dto.getConceptLabelToDisplay() == null ? null : dto.concept();
        VocabularyDTO vocabulary = concept == null ? null : concept.getVocabulary();
        if (vocabulary == null || vocabulary.getBaseUri() == null || concept.getExternalId() == null) {
            return null;
        }
        return vocabulary.getBaseUri() + "/?idc=" + concept.getExternalId()
                + "&idt=" + vocabulary.getExternalVocabularyId();
    }
}
