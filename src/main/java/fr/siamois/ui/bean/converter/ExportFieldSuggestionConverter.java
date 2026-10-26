package fr.siamois.ui.bean.converter;

import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditorBean.FieldSuggestion;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/** Convertit un champ proposé par la liste de saisie d'un mapping (voir {@link FieldSuggestion#encoded()}). */
@Component("exportFieldSuggestionConverter")
public class ExportFieldSuggestionConverter implements Converter<FieldSuggestion>, Serializable {

    @Override
    public FieldSuggestion getAsObject(FacesContext context, UIComponent component, String value) {
        return FieldSuggestion.decode(value);
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, FieldSuggestion value) {
        return value == null ? "" : value.encoded();
    }
}
