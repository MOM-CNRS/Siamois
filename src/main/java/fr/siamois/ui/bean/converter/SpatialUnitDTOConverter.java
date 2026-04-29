package fr.siamois.ui.bean.converter;

import fr.siamois.dto.entity.SpatialUnitDTO;
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
public class SpatialUnitDTOConverter implements Converter<SpatialUnitDTO> {
    @Override
    public SpatialUnitDTO getAsObject(FacesContext context, UIComponent component, String value) {
        byte[] bytes = Base64.getDecoder().decode(value);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SpatialUnitDTO) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SpatialUnitDTO value) {
        byte[] bytes = SerializationUtils.serialize(value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
