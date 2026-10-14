package fr.siamois.ui.bean.settings.components;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OptionElement {
    private String bootstrapIconClass;
    private String title;
    private String description;
    private RedirectAction actionFromBean;
    /** Élément affiché mais non ouvrable (ex. service en travaux). */
    private boolean disabled;
    /** Infobulle du service non ouvrable. */
    private String disabledHint;

    public OptionElement(String bootstrapIconClass, String title, String description, RedirectAction actionFromBean) {
        this(bootstrapIconClass, title, description, actionFromBean, false, null);
    }
}
