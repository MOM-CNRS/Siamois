package fr.siamois.ui.bean.converter;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component("specimenSummaryConverter")
@RequiredArgsConstructor
public class SpecimenSummaryConverter implements Converter<SpecimenSummaryDTO>, Serializable {

    private final transient SpecimenService specimenService;

    @Override
    public SpecimenSummaryDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            SpecimenDTO dto = specimenService.findById(Long.parseLong(value.trim()));
            if (dto == null) {
                return null;
            }
            SpecimenSummaryDTO summary = new SpecimenSummaryDTO();
            summary.setId(dto.getId());
            summary.setFullIdentifier(dto.getFullIdentifier());
            return summary;
        } catch (Exception e) {
            log.warn("Unable to convert Specimen id '{}': {}", value, e.getMessage());
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SpecimenSummaryDTO value) {
        if (value == null || value.getId() == null) {
            return "";
        }
        return String.valueOf(value.getId());
    }
}
