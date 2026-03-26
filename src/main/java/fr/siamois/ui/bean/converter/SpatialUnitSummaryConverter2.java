package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
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
public class SpatialUnitSummaryConverter2 implements Converter<SpatialUnitSummaryDTO>, Serializable {

    private final ObjectMapper objectMapper;
    private final SpatialUnitService spatialUnitService;

    public SpatialUnitSummaryConverter2(SpatialUnitService spatialUnitService) {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
        this.spatialUnitService = spatialUnitService;
    }


    @Override
    public SpatialUnitSummaryDTO getAsObject(FacesContext context, UIComponent component, String value) {
        try {
            return new SpatialUnitSummaryDTO(spatialUnitService.findById(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            log.error("Error while converting string to SpatialUnit object", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SpatialUnitSummaryDTO value) {
        try {
            if (value == null) {
                return "";
            }
            return objectMapper.writeValueAsString(value.getId());
        } catch (JsonProcessingException e) {
            log.error("Error while converting SpatialUnit object to string", e);
            return null;
        }
    }
}