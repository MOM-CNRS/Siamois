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
import java.util.Base64;

@Slf4j
@Component
public class ConceptAutocompleteDTOConverter implements Converter<ConceptAutocompleteDTO> {

    @Override
    @Nullable
    public ConceptAutocompleteDTO getAsObject(FacesContext facesContext, UIComponent uiComponent, @Nullable String s) {
        byte[] bytes = Base64.getDecoder().decode(s);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ConceptAutocompleteDTO) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    @NonNull
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, @Nullable ConceptAutocompleteDTO conceptAutocompleteDTO) {
        byte[] bytes = SerializationUtils.serialize(conceptAutocompleteDTO);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
