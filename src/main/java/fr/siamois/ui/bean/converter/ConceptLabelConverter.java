package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.ui.bean.LabelBean;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;

/**
 * Converter for ConceptLabel objects. Uses the LabelBean cache to convert between ConceptLabel objects and their string representations.
 */
@SessionScoped
@Component
@RequiredArgsConstructor
public class ConceptLabelConverter implements Converter<ConceptLabel> {

    private final LabelBean labelBean;

    @Override
    public ConceptLabel getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        return labelBean.findById(Long.parseLong(s)).orElse(null);
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ConceptLabel conceptLabel) {
        if (conceptLabel == null) {
            return "";
        }
        return String.valueOf(conceptLabel.getId());
    }
}
