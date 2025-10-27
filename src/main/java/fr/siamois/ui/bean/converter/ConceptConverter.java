package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("conceptConverter")
@RequiredArgsConstructor
public class ConceptConverter implements Converter<Concept> {

    private final ConceptService conceptService;

    @Override
    public Concept getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        try {
            return conceptService.findById(Long.parseLong(s)).orElse(null);
        } catch (NumberFormatException e) {
            log.error("Error converting String to Concept: {}", s, e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, Concept concept) {
        return concept.getId().toString();
    }
}