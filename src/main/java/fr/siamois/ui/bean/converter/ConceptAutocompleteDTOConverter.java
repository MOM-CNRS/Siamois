package fr.siamois.ui.bean.converter;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Base64;

@Slf4j
@Component("conceptAutocompleteDTOConverter")
public class ConceptAutocompleteDTOConverter implements Converter<ConceptAutocompleteDTO>, Serializable {

    @Override
    @Nullable
    public ConceptAutocompleteDTO getAsObject(FacesContext context, UIComponent component, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (ConceptAutocompleteDTO) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            log.warn("Unable to convert ConceptAutocompleteDTO: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @NonNull
    public String getAsString(FacesContext context, UIComponent component, @Nullable ConceptAutocompleteDTO value) {
        if (value == null) {
            return "";
        }
        byte[] bytes = SerializationUtils.serialize(value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
