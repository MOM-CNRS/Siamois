package fr.siamois.ui.bean.settings;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One row of the "OpenTheso API key" section of the profile page: an OpenTheso instance and the API key the
 * user types for it.
 */
@Getter
@Setter
@NoArgsConstructor
public class ThesaurusApiKey implements Serializable {

    /** The id of the instance in the database: what the key is saved against. */
    private Long instanceId;

    /** The base URL of the instance. */
    private String url;

    private String apiKey;

    public ThesaurusApiKey(Long instanceId, String url, String apiKey) {
        this.instanceId = instanceId;
        this.url = url;
        this.apiKey = apiKey;
    }
}
