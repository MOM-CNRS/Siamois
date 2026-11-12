package fr.siamois.ui.bean.dialog.project;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceFromSuggestionRequest;
import fr.siamois.ui.api.openapi.v1.service.PlaceSuggestionApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The places of the new project dialog: which place fields the organisation's project form has, what their
 * pickers suggest and the place a picked suggestion becomes. It goes through the services the REST API
 * {@code /api/v1/places} uses, so the dialog suggests the same places as the React create form.
 */
@Component
@RequiredArgsConstructor
public class ProjectCreationPlaces {

    private static final int SUGGESTION_LIMIT = 20;

    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final PlaceSuggestionApiService placeSuggestionApiService;
    private final ProjectApiService projectApiService;

    /**
     * The place fields of an organisation's project form.
     *
     * @param organizationId the organisation
     * @param person         the user creating the project
     * @param lang           the user's language, for the labels
     * @return the place fields of the form
     */
    public ProjectPlaceFields fieldsOf(long organizationId, PersonDTO person, String lang) {
        return ProjectPlaceFields.of(recordingUnitOpenApiService.buildProjectTypes(organizationId, person, lang)
                .getFields().values());
    }

    /**
     * What the picker of a place field suggests: the organisation's places, then its sources' suggestions.
     *
     * @param organizationId the organisation
     * @param field          the place field
     * @param query          the text typed
     * @param dependencies   the places picked in the fields its sources depend on, by field id
     * @param lang           the user's language
     * @return the places to offer
     */
    public List<PlaceChoice> suggest(long organizationId, PlaceField field, String query,
                                     Map<Long, Long> dependencies, String lang) {
        PlaceSuggestionApiService.SuggestionRequest request = new PlaceSuggestionApiService.SuggestionRequest(
                organizationId, field.getId(), null, null, query == null ? "" : query.trim(), dependencies,
                SUGGESTION_LIMIT, lang);
        return placeSuggestionApiService.suggest(projectApiService.requireCaller(), request).items().stream()
                .map(PlaceChoice::of)
                .toList();
    }

    /**
     * The place a picked choice stands for: the choice itself when it already is one, otherwise the place
     * created in the organisation from the suggestion (or the one that already exists for it).
     *
     * @param organizationId the organisation
     * @param choice         the picked choice
     * @param lang           the user's language
     * @return a place of the organisation
     */
    public PlaceChoice placeOf(long organizationId, PlaceChoice choice, String lang) {
        if (!choice.isExternal()) {
            return choice;
        }
        PlaceFromSuggestionRequest request = new PlaceFromSuggestionRequest();
        request.setOrganizationId(organizationId);
        request.setSource(choice.getSource());
        request.setName(choice.getName());
        request.setCode(choice.getCode());
        request.setAddress(choice.getAddress());
        return PlaceChoice.created(placeSuggestionApiService.fromSuggestion(projectApiService.requireCaller(), request, lang),
                choice);
    }
}
