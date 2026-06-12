package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.SearchResultDTO;
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
public class SearchResultConverter implements Converter<SearchResultDTO>, Serializable {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SearchResultDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, SearchResultDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting string to SearchResultDTO", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SearchResultDTO value) {
        if (value == null) return "";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Error converting SearchResultDTO to string", e);
            return "";
        }
    }
}
