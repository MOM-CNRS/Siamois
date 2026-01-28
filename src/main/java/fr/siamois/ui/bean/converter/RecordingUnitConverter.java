package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
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
public class RecordingUnitConverter implements Converter<RecordingUnit>, Serializable {

    private final ObjectMapper objectMapper;

    public RecordingUnitConverter() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
    }


    @Override
    public RecordingUnit getAsObject(FacesContext context, UIComponent component, String value) {
        try {
            return objectMapper.readValue(value, RecordingUnit.class);
        } catch (JsonProcessingException e) {
            log.error("Error while converting string to RecordingUnit object", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, RecordingUnit value) {
        try {
            if (value == null) {
                return "";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Error while converting RecordingUnit object to string", e);
            return null;
        }
    }
}
