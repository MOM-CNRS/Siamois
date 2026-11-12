package fr.siamois.ui.bean.dialog.project;

import lombok.Value;

import java.io.Serializable;
import java.util.List;

/**
 * What a place picker of the new project dialog suggests for a search: the places to offer, and whether
 * every source could be narrowed by the places its parameters read. A source left without one searches
 * more widely, which the picker says under its suggestions.
 */
@Value
public class PlaceSuggestions implements Serializable {

    /** Nothing to suggest. */
    public static final PlaceSuggestions NONE = new PlaceSuggestions(List.of(), true);

    transient List<PlaceChoice> choices;
    boolean narrowed;
}
