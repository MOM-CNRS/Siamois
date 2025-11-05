package fr.siamois.infrastructure.api.dto;


import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ConceptBranchDTO {
    private final Map<String, FullInfoDTO> data = new HashMap<>();

    @Setter
    private String parentUrl;

    public void addConceptBranchDTO(String url, FullInfoDTO dto) {
        this.data.putIfAbsent(url, dto);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public static class ConceptBranchDTOBuilder {
        public static final String STRING_TYPE = "string";
        private final ConceptBranchDTO conceptBranchDTO;

        public ConceptBranchDTOBuilder() {
            this.conceptBranchDTO = new ConceptBranchDTO();
        }

        public ConceptBranchDTOBuilder identifier(String url, String identifier) {
            FullInfoDTO fullInfoDTO = conceptBranchDTO.data.computeIfAbsent(url, k -> new FullInfoDTO());
            PurlInfoDTO purlInfoDTO = new PurlInfoDTO();
            purlInfoDTO.setType(STRING_TYPE);
            purlInfoDTO.setValue(identifier);
            fullInfoDTO.setIdentifier(new PurlInfoDTO[]{ purlInfoDTO });
            return this;
        }

        public ConceptBranchDTOBuilder notation(String url, String fieldCode) {
            FullInfoDTO fullInfoDTO = conceptBranchDTO.data.computeIfAbsent(url, k -> new FullInfoDTO());
            PurlInfoDTO purlInfoDTO = new PurlInfoDTO();
            purlInfoDTO.setType(STRING_TYPE);
            purlInfoDTO.setValue(fieldCode);
            fullInfoDTO.setNotation(new PurlInfoDTO[]{ purlInfoDTO });
            return this;
        }

        public ConceptBranchDTOBuilder label(String url, String label, String lang) {
            FullInfoDTO fullInfoDTO = conceptBranchDTO.data.computeIfAbsent(url, k -> new FullInfoDTO());
            PurlInfoDTO purlInfoDTO = new PurlInfoDTO();
            purlInfoDTO.setType(STRING_TYPE);
            purlInfoDTO.setValue(label);
            purlInfoDTO.setLang(lang);
            if (fullInfoDTO.getPrefLabel() == null) {
                fullInfoDTO.setPrefLabel(new PurlInfoDTO[]{ purlInfoDTO });
            } else {
                PurlInfoDTO[] existingLabels = fullInfoDTO.getPrefLabel();
                PurlInfoDTO[] newLabels = new PurlInfoDTO[existingLabels.length + 1];
                System.arraycopy(existingLabels, 0, newLabels, 0, existingLabels.length);
                newLabels[existingLabels.length] = purlInfoDTO;
                fullInfoDTO.setPrefLabel(newLabels);
            }
            return this;
        }

        public ConceptBranchDTOBuilder definition(String url, String definition, String lang) {
            FullInfoDTO fullInfoDTO = conceptBranchDTO.data.computeIfAbsent(url, k -> new FullInfoDTO());
            PurlInfoDTO purlInfoDTO = new PurlInfoDTO();
            purlInfoDTO.setType(STRING_TYPE);
            purlInfoDTO.setValue(definition);
            purlInfoDTO.setLang(lang);
            if (fullInfoDTO.getDefinition() == null) {
                fullInfoDTO.setDefinition(new PurlInfoDTO[]{ purlInfoDTO });
            } else {
                PurlInfoDTO[] existingLabels = fullInfoDTO.getDefinition();
                PurlInfoDTO[] newLabels = new PurlInfoDTO[existingLabels.length + 1];
                System.arraycopy(existingLabels, 0, newLabels, 0, existingLabels.length);
                newLabels[existingLabels.length] = purlInfoDTO;
                fullInfoDTO.setDefinition(newLabels);
            }
            return this;
        }

        public ConceptBranchDTO build() {
            return this.conceptBranchDTO;
        }
    }

}
