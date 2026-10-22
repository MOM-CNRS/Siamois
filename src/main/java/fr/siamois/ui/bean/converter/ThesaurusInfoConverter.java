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

        // a label may itself contain commas: server and id come first, the language last
        String label = String.join(",", java.util.Arrays.copyOfRange(parts, 2, parts.length - 1));
        return new ThesaurusInfo(parts[0], parts[1], label, parts[parts.length - 1]);
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, ThesaurusInfo thesaurusInfo) {
        return String.format("(%s,%s,%s,%s)", thesaurusInfo.server(), thesaurusInfo.idTheso(), thesaurusInfo.label(), thesaurusInfo.langLabel());
    }
}
