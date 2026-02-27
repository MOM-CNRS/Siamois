package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.dto.entity.ConceptLabelDTO;
import fr.siamois.ui.bean.LabelBean;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Converter for ConceptLabel objects. Uses the LabelBean cache to convert between ConceptLabel objects and their string representations.
 */
@Slf4j
@Scope(value = "session")
@Component
@RequiredArgsConstructor
public class ConceptLabelConverter implements Converter<ConceptLabelDTO> {

    private final LabelBean labelBean;

    @Override
    public ConceptLabelDTO getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return labelBean.findById(Long.parseLong(s)).orElse(null);
        } catch (NumberFormatException e) {
            log.debug("Invalid ConceptLabel ID format: \"{}\"", s);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ConceptLabelDTO conceptLabel) {
        if (conceptLabel == null) {
            return "";
        }
        return String.valueOf(conceptLabel.getId());
    }
}
