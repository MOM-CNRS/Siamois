package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.LocalizedConceptDataId;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;

@SessionScoped
@Component
@RequiredArgsConstructor
public class LocalizedConceptDataConverter implements Converter<LocalizedConceptData> {

    private final LocalizedConceptDataRepository localizedConceptDataRepository;

    @Override
    public LocalizedConceptData getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        if (s == null || s.isEmpty()) return null;
        String[] keys = s.split(",");
        LocalizedConceptDataId id = new LocalizedConceptDataId();
        id.setConceptId(Long.parseLong(keys[0]));
        id.setLangCode(keys[1]);
        return  localizedConceptDataRepository.findById(id).orElse(null);
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, LocalizedConceptData localizedConceptData) {
        if (localizedConceptData == null) {
            return "";
        }
        return localizedConceptData.getId().toString();
    }
}
