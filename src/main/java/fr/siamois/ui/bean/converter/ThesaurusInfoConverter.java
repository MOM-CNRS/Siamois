package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

@Component
public class ThesaurusInfoConverter implements Converter<ThesaurusInfo> {

    @Override
    public ThesaurusInfo getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        if (s == null || s.length() < 5)
            return new ThesaurusInfo("", "", "", "");

        String infos = s.substring(1, s.length() - 1);
        String[] parts = infos.split(",");

        if (parts.length < 4) {
            return new ThesaurusInfo("", "", "", "");
        }

        return new ThesaurusInfo(parts[0], parts[1], parts[2], parts[3]);
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ThesaurusInfo thesaurusInfo) {
        return String.format("(%s,%s,%s,%s)", thesaurusInfo.server(), thesaurusInfo.idTheso(), thesaurusInfo.label(), thesaurusInfo.langLabel());
    }
}
