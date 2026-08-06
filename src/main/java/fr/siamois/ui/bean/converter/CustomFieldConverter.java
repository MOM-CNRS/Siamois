package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component
public class CustomFieldConverter implements Converter<CustomField>, Serializable {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CustomField getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        try {
            // Parse JSON into a tree structure
            JsonNode node = mapper.readTree(s);

            // Deserialize into the correct class based on the type
            if (node.has("concepts")) {
                return mapper.readValue(s, CustomFieldSelectMultiple.class);
            } else {
                return mapper.readValue(s, CustomFieldInteger.class);
            }
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, CustomField field) {
        try {
            return mapper.writeValueAsString(field);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
