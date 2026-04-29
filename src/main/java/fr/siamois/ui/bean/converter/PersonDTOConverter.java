package fr.siamois.ui.bean.converter;

import fr.siamois.dto.entity.PersonDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

@Component
public class PersonDTOConverter implements Converter<PersonDTO> {
    @Override
    public PersonDTO getAsObject(FacesContext context, UIComponent component, String value) {
        byte[] bytes = Base64.getDecoder().decode(value);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (PersonDTO) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, PersonDTO value) {
        byte[] bytes = SerializationUtils.serialize(value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
