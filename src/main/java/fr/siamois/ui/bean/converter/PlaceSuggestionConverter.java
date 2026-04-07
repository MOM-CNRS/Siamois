package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.PlaceSuggestionDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.faces.bean.ManagedBean;
import java.io.Serializable;

@ManagedBean
@Component
@Slf4j
public class PlaceSuggestionConverter implements Converter<PlaceSuggestionDTO>, Serializable {

    private final ObjectMapper objectMapper;

    public PlaceSuggestionConverter() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
    }


    @Override
    public PlaceSuggestionDTO getAsObject(FacesContext context, UIComponent component, String value) {
        try {
            return objectMapper.readValue(value, PlaceSuggestionDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Error while converting string to PlaceSuggestionDTO object", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, PlaceSuggestionDTO value) {
        try {
            if (value == null) {
                return "";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Error while converting PlaceSuggestionDTO object to string", e);
            return null;
        }
    }
}



