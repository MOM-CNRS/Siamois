package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("conceptConverter")
@RequiredArgsConstructor
public class ConceptConverter implements Converter<ConceptDTO> {

    private final ObjectMapper objectMapper;

    public ConceptConverter() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public ConceptDTO getAsObject(FacesContext facesContext, UIComponent uiComponent, String value) {
        try {
            return objectMapper.readValue(value, ConceptDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting String to Concept: {}", value, e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ConceptDTO concept) {
        try {
            if (concept == null) {
                return "";
            }
            return objectMapper.writeValueAsString(concept);
        } catch (JsonProcessingException e) {
            log.error("Error while converting SpatialUnit object to string", e);
            return null;
        }
    }
}