package fr.siamois.ui.bean.converter;

import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditorBean.SearchHit;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/** Convertit un résultat de la recherche de colonnes de l'éditeur d'export (voir {@link SearchHit#encoded()}). */
@Component("exportSearchHitConverter")
public class ExportSearchHitConverter implements Converter<SearchHit>, Serializable {

    @Override
    public SearchHit getAsObject(FacesContext context, UIComponent component, String value) {
        return SearchHit.decode(value);
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, SearchHit value) {
        return value == null ? "" : value.encoded();
    }
}
