package fr.siamois.ui.bean.converter;

import fr.siamois.dto.entity.ProfileDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

/**
 * Serializes with Base64-encoded Java serialization rather than JSON: PrimeFaces'
 * {@code SelectCheckboxMenu} widget reads each item's submitted value via jQuery's
 * {@code .data("item-value")}, which auto-parses any {@code data-*} attribute that looks like a
 * JSON object into a real JS object — turning it into the literal string "[object Object]" once
 * it's later serialized back for the ajax request. Base64 output never starts with {@code {},}
 * so it's left untouched as plain text.
 */
@Component
@Slf4j
public class ProfileDTOConverter implements Converter<ProfileDTO> {

    @Override
    public ProfileDTO getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (ProfileDTO) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            log.error("Error while converting string to ProfileDTO object", e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, ProfileDTO value) {
        if (value == null) {
            return "";
        }
        byte[] bytes = SerializationUtils.serialize(value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
