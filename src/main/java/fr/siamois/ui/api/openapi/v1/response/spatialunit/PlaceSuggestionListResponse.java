package fr.siamois.ui.api.openapi.v1.response.spatialunit;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PlaceSuggestionListResponse extends ListResponse<PlaceSuggestionItemApi> {

    @Schema(description = "Sources du champ dont un paramètre dépend d'un autre champ lieu sans valeur : interrogées sans ce "
            + "filtre (recherche élargie, par défaut) ou ignorées (onMissing SKIP). Le client invite alors à remplir ce champ")
    private final List<String> unnarrowedSources;

    public PlaceSuggestionListResponse(List<PlaceSuggestionItemApi> data, ListMeta meta, List<String> unnarrowedSources) {
        super(data, meta);
        this.unnarrowedSources = List.copyOf(unnarrowedSources);
    }
}
