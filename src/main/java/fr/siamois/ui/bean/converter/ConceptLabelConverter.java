package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

@Component
public class ConceptLabelConverter implements Converter<ConceptLabel> {

    private final ObjectMapper objectMapper;

    public ConceptLabelConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ConceptLabel getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(s, ConceptLabel.class);
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ConceptLabel conceptLabel) {
        try {
            return objectMapper.writeValueAsString(conceptLabel);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
