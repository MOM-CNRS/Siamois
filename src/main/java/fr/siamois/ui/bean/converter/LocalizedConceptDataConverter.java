package fr.siamois.ui.bean.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Slf4j
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Component
@RequiredArgsConstructor
public class LocalizedConceptDataConverter implements Converter<LocalizedConceptData> {

    private final ObjectMapper objectMapper;

    @Override
    public LocalizedConceptData getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return objectMapper.readValue(s, LocalizedConceptData.class);
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, LocalizedConceptData localizedConceptData) {
        if (localizedConceptData == null) return "";
        try {
            return objectMapper.writeValueAsString(localizedConceptData);
        } catch (JsonProcessingException e) {
            log.debug(e.getMessage(), e);
            return "";
        }
    }
}
