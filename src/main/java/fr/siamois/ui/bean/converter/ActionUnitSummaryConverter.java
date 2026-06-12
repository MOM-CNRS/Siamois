package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
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
public class ActionUnitSummaryConverter implements Converter<ActionUnitSummaryDTO>, Serializable {

    private final ObjectMapper objectMapper;

    public ActionUnitSummaryConverter() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public ActionUnitSummaryDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, ActionUnitSummaryDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Error while converting string to ActionUnitSummaryDTO", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, ActionUnitSummaryDTO value) {
        if (value == null) return "";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Error while converting ActionUnitSummaryDTO to string", e);
            return "";
        }
    }
}
