package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.primefaces.PrimeFaces;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The new project dialog: the identifier it proposes, the places it offers and what it sends to create the project. */
class NewProjectDialogBeanTest {

    private static final String LANG = "fr";

    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final ProjectApiCaller caller = mock(ProjectApiCaller.class);
    private final ProjectCreationPlaces projectCreationPlaces = mock(ProjectCreationPlaces.class);

    private NewProjectDialogBean bean;

    @BeforeEach
    void setUp() {
        LangBean langBean = mock(LangBean.class);
        when(langBean.getLanguageCode()).thenReturn(LANG);
        bean = new NewProjectDialogBean(mock(InstitutionService.class), mock(ProfilePermissionService.class),
                mock(FieldConfigurationService.class), projectApiService, projectCreationPlaces, mock(ProjectListBean.class),
                mock(SessionSettingsBean.class), langBean);
        when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void proposeIdentifier_shouldFillTheFieldWithTheNextIdentifierOfTheOrganisation() {
        bean.setInstitutionId(3L);
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG)).thenReturn(Optional.of("PRJ-004"));

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isEqualTo("PRJ-004");
    }

    @Test
    void proposeIdentifier_shouldLeaveTheFieldEmpty_whenTheOrganisationCannotNumberItsProjects() {
        bean.setInstitutionId(3L);
        bean.setIdentifier("old");
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG)).thenReturn(Optional.empty());

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
    }

    @Test
    void proposeIdentifier_shouldLeaveTheFieldEmpty_whenNoOrganisationIsChosen() {
        bean.setIdentifier("old");

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
        verify(projectApiService, never()).previewNextProjectIdentifier(any(), any(), any());
    }

    @Test
    void proposeIdentifier_shouldNotBlockTheDialog_whenThePreviewIsRefused() {
        bean.setInstitutionId(3L);
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
    }

    @Test
    void onOrganisationChange_shouldProposeTheIdentifierOfTheNewOrganisation() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(5L);
        bean.setCreatableInstitutions(List.of(institution));
        bean.setInstitutionId(5L);
        when(projectApiService.previewNextProjectIdentifier(caller, "5", LANG)).thenReturn(Optional.of("B-001"));

        bean.onOrganisationChange();

        assertThat(bean.getIdentifier()).isEqualTo("B-001");
        assertThat(bean.getTypeConcept()).isNull();
    }

    @Test
    void onOrganisationChange_shouldOfferThePlaceFieldsOfTheNewOrganisation_andForgetThePlacesPicked() {
        bean.setInstitutionId(5L);
        bean.setMainLocation(place(1L, "Lyon"));
        bean.setSpatialContext(new ArrayList<>(List.of(place(2L, "Rue Mercière"))));
        ProjectPlaceFields fields = new ProjectPlaceFields(MAIN_LOCATION, SPATIAL_CONTEXT);
        when(projectCreationPlaces.fieldsOf(eq(5L), any(), eq(LANG))).thenReturn(fields);
        when(projectApiService.previewNextProjectIdentifier(caller, "5", LANG)).thenReturn(Optional.empty());

        bean.onOrganisationChange();

        assertThat(bean.getPlaceFields()).isEqualTo(fields);
        assertThat(bean.getMainLocation()).isNull();
        assertThat(bean.getSpatialContext()).isEmpty();
    }

    @Test
    void loadPlaceFields_shouldOfferNoPlaceField_whenTheFormCannotBeRead() {
        bean.setInstitutionId(5L);
        when(projectCreationPlaces.fieldsOf(eq(5L), any(), eq(LANG))).thenThrow(new IllegalStateException("boom"));

        bean.loadPlaceFields();

        assertThat(bean.getPlaceFields()).isEqualTo(ProjectPlaceFields.NONE);
    }

    @Test
    void completeSpatialContext_shouldNarrowTheSuggestionsByTheMainLocation() {
        bean.setInstitutionId(5L);
        bean.setPlaceFields(new ProjectPlaceFields(MAIN_LOCATION, SPATIAL_CONTEXT));
        bean.setMainLocation(place(1L, "Lyon"));
        List<PlaceChoice> offered = List.of(place(2L, "Rue Mercière"));
        when(projectCreationPlaces.suggest(5L, SPATIAL_CONTEXT, "rue", Map.of(10L, 1L), LANG)).thenReturn(offered);

        List<PlaceChoice> suggested = bean.completeSpatialContext("rue");

        assertThat(suggested).isEqualTo(offered);
        assertThat(bean.getOfferedPlaces().getAsObject(null, null, "2")).isEqualTo(offered.get(0));
    }

    @Test
    void completeMainLocation_shouldSuggestNothing_whenTheFormHasNoMainLocation() {
        bean.setInstitutionId(5L);

        assertThat(bean.completeMainLocation("ly")).isEmpty();
        verifyNoInteractions(projectCreationPlaces);
    }

    @Test
    void onPlacePicked_shouldTurnAPickedSuggestionIntoAPlaceOfTheOrganisation() {
        bean.setInstitutionId(5L);
        PlaceChoice suggestion = new PlaceChoice("external:INSEE:69123", null, "Lyon", "69123", "INSEE", null, null);
        PlaceChoice created = place(7L, "Lyon");
        bean.setMainLocation(suggestion);
        when(projectCreationPlaces.placeOf(5L, suggestion, LANG)).thenReturn(created);

        bean.onPlacePicked();

        assertThat(bean.getMainLocation()).isEqualTo(created);
        assertThat(bean.getOfferedPlaces().getAsObject(null, null, "7")).isEqualTo(created);
    }

    @Test
    void create_shouldSendTheTypedIdentifierAndThePlacesPicked() {
        bean.setInstitutionId(5L);
        bean.setName("Fouille");
        bean.setProposedIdentifier("PRJ-004");
        bean.setIdentifier(" PRJ-100 ");
        bean.setMainLocation(place(1L, "Lyon"));
        bean.setSpatialContext(new ArrayList<>(List.of(place(2L, "Rue Mercière"), place(2L, "Rue Mercière"), place(3L, "Place Bellecour"))));

        ProjectCreateRequest request = create();

        assertThat(request.getOrganizationId()).isEqualTo("5");
        assertThat(request.getName()).isEqualTo("Fouille");
        assertThat(request.getIdentifier()).isEqualTo("PRJ-100");
        assertThat(request.getMainLocationId()).isEqualTo("1");
        assertThat(request.getSpatialContextSpatialUnitIds()).containsExactly("2", "3");
    }

    @Test
    void create_shouldLetTheServerAllocateTheIdentifier_whenTheProposedOneIsKept() {
        bean.setInstitutionId(5L);
        bean.setName("Fouille");
        bean.setProposedIdentifier("PRJ-004");
        bean.setIdentifier("PRJ-004");

        ProjectCreateRequest request = create();

        assertThat(request.getIdentifier()).isNull();
        assertThat(request.getMainLocationId()).isNull();
        assertThat(request.getSpatialContextSpatialUnitIds()).isNull();
    }

    private static final PlaceField MAIN_LOCATION = new PlaceField(10L, "Commune");
    private static final PlaceField SPATIAL_CONTEXT = new PlaceField(11L, "Localisations précises");

    private static PlaceChoice place(long id, String name) {
        return new PlaceChoice(String.valueOf(id), id, name, null, "SIAMOIS", null, null);
    }

    /** Creates the project, outside of a request: the Faces messages and PrimeFaces are stubbed. */
    private ProjectCreateRequest create() {
        try (MockedStatic<MessageUtils> ignored = mockStatic(MessageUtils.class);
             MockedStatic<PrimeFaces> primeFaces = mockStatic(PrimeFaces.class)) {
            primeFaces.when(PrimeFaces::current).thenReturn(RETURNS_DEEP_STUBS);
            bean.create();
        }
        ArgumentCaptor<ProjectCreateRequest> request = ArgumentCaptor.forClass(ProjectCreateRequest.class);
        verify(projectApiService).createProject(eq(caller), request.capture(), eq(LANG));
        return request.getValue();
    }
}
