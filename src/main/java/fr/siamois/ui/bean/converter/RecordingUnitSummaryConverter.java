package fr.siamois.ui.bean.converter;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component("recordingUnitSummaryConverter")
@RequiredArgsConstructor
public class RecordingUnitSummaryConverter implements Converter<RecordingUnitSummaryDTO>, Serializable {

    private final transient RecordingUnitService recordingUnitService;

    @Override
    public RecordingUnitSummaryDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            RecordingUnitDTO dto = recordingUnitService.findById(Long.parseLong(value.trim()));
            return dto == null ? null : new RecordingUnitSummaryDTO(dto);
        } catch (Exception e) {
            log.warn("Unable to convert RecordingUnit id '{}': {}", value, e.getMessage());
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, RecordingUnitSummaryDTO value) {
        if (value == null || value.getId() == null) {
            return "";
        }
        return String.valueOf(value.getId());
    }
}
