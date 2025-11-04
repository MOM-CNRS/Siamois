package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.ui.bean.LabelBean;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;

/**
 * Converter for ConceptLabel objects. Uses the LabelBean cache to convert between ConceptLabel objects and their string representations.
 */
@Slf4j
@SessionScoped
@Component
@RequiredArgsConstructor
public class ConceptLabelConverter implements Converter<ConceptLabel> {

    private final LabelBean labelBean;

    @Override
    public ConceptLabel getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
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
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ConceptLabel conceptLabel) {
        if (conceptLabel == null) {
            return "";
        }
        return String.valueOf(conceptLabel.getId());
    }
}
