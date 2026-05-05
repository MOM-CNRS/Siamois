package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.FullAddress;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

import java.io.Serializable;
@Component("fullAddressConverter")
public class FullAddressConverter  implements Converter<FullAddress>, Serializable {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public FullAddress getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        try {

            return mapper.readValue(s, FullAddress.class);

        } catch (JsonProcessingException e) {

            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, FullAddress field) {
        try {
            return mapper.writeValueAsString(field);
        } catch (JsonProcessingException e) {

            return null;
        }
    }

}


