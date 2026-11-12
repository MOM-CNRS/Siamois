package fr.siamois.ui.bean.dialog.project;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The places the pickers of the new project dialog offered, by key: what turns the key a picker posts
 * back into the place it stands for. A suggestion is never rebuilt from what the browser sends, it is
 * one the server offered.
 */
public class OfferedPlaces implements Converter<PlaceChoice>, Serializable {

    private final Map<String, PlaceChoice> byKey = new HashMap<>();

    /**
     * Remembers the places offered to a picker.
     *
     * @param choices the places offered
     * @return the same places, for the picker to show
     */
    public List<PlaceChoice> remember(List<PlaceChoice> choices) {
        choices.forEach(this::remember);
        return choices;
    }

    /**
     * Remembers one place, such as the one created from a picked suggestion.
     *
     * @param choice the place
     * @return the same place
     */
    public PlaceChoice remember(PlaceChoice choice) {
        byKey.put(choice.getKey(), choice);
        return choice;
    }

    /** Forgets every place offered: the dialog starts again. */
    public void forget() {
        byKey.clear();
    }

    /**
     * The place offered under a key.
     *
     * @return the place, or null for an empty or unknown key
     */
    @Override
    @Nullable
    public PlaceChoice getAsObject(FacesContext context, UIComponent component, @Nullable String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return byKey.get(key);
    }

    /**
     * The key of a place.
     *
     * @return the key, or an empty string for no place
     */
    @Override
    public String getAsString(FacesContext context, UIComponent component, @Nullable PlaceChoice choice) {
        return choice == null ? "" : choice.getKey();
    }
}
