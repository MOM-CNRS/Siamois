package fr.siamois.ui.bean.converter;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component("spatialUnitDTOConverter")
@RequiredArgsConstructor
public class SpatialUnitDTOConverter implements Converter<SpatialUnitDTO>, Serializable {

    private final transient SpatialUnitService spatialUnitService;

    @Override
    public SpatialUnitDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return spatialUnitService.findById(Long.parseLong(value.trim()));
        } catch (Exception e) {
            log.warn("Unable to convert SpatialUnit id '{}': {}", value, e.getMessage());
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SpatialUnitDTO value) {
        if (value == null || value.getId() == null) {
            return "";
        }
        return String.valueOf(value.getId());
    }
}
