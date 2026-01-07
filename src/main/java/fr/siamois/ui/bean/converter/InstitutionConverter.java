package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component("institutionConverter") // make sure this name matches your XHTML
public class InstitutionConverter implements Converter<Institution>, Serializable {

    private final transient InstitutionService institutionService;

    @Autowired
    public InstitutionConverter(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @Override
    public Institution getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(value);
            return institutionService.findById(id);
        } catch (Exception e) {
            log.error("Error converting Institution id '{}' to object", value, e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, Institution institution) {
        if (institution == null || institution.getId() == null) {
            return "";
        }
        try {
            return String.valueOf(institution.getId());
        } catch (Exception e) {
            log.error("Error converting Institution to string", e);
            return "";
        }
    }
}
