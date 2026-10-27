package fr.siamois.ui.bean.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One row of the "OpenTheso API key" section of the profile page: a thesaurus and the API key the user
 * types for it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ThesaurusApiKey implements Serializable {

    /** The label of the thesaurus, in the language of the user. */
    private String label;

    /** The complete URL of the thesaurus. */
    private String url;

    private String apiKey;
}
