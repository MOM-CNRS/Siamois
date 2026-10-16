package fr.siamois.ui.bean.converter;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.Serializable;

@Slf4j
@Component("actionUnitDTOConverter")
@RequiredArgsConstructor
public class ActionUnitDTOConverter implements Converter<ActionUnitDTO>, Serializable {

    private final transient ActionUnitService actionUnitService;

    @Override
    public ActionUnitDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return actionUnitService.findById(Long.parseLong(value.trim()));
        } catch (Exception e) {
            log.warn("Unable to convert ActionUnit id '{}': {}", value, e.getMessage());
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, ActionUnitDTO value) {
        return (value == null || value.getId() == null) ? "" : String.valueOf(value.getId());
    }
}