package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.form.OnTheFlyFieldService;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.ui.api.openapi.v1.request.OnTheFlyMeasurementRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.OnTheFlyFieldsResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnTheFlyFieldOpenApiServiceTest {

    private static final long ID = 5L;
    private static final long PROJECT_ID = 9L;
    private static final Set<Long> INSTITUTIONS = Set.of(1L);

    @Mock private OnTheFlyFieldService onTheFlyFieldService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private SpecimenService specimenService;
    @Mock private PhaseOpenApiService phaseOpenApiService;
    @Mock private ContainerOpenApiService containerOpenApiService;
    @Mock private DocumentOpenApiService documentOpenApiService;
    @Mock private StudyOpenApiService studyOpenApiService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private ConceptRepository conceptRepository;
    @Mock private UnitDefinitionRepository unitDefinitionRepository;
    @Mock private LabelService labelService;
    @Mock private LangService langService;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private FieldConfigurationService fieldConfigurationService;
    @Mock private ConceptLabelRepository conceptLabelRepository;
    @Mock private UnitDefinitionService unitDefinitionService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private OnTheFlyFieldOpenApiService service;

    private PersonDTO person;
    private InstitutionDTO institution;
    private ActionUnitSummaryDTO project;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        person = new PersonDTO();
        person.setId(3L);
        institution = new InstitutionDTO();
        institution.setId(1L);
        project = mock(ActionUnitSummaryDTO.class);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(project.getCreatedByInstitution()).thenReturn(institution);
        when(langService.localeForApiLang(any())).thenReturn(Locale.FRENCH);
        when(langService.resolveMessage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(onTheFlyFieldService.fieldsOf(any(), anyLong())).thenReturn(List.of());
        when(entityManager.getReference(eq(Person.class), any())).thenReturn(new Person());
    }

    // ---- who may do what ----

    private void recordingUnitWithRight(boolean canEdit) {
        RecordingUnitDTO dto = mock(RecordingUnitDTO.class);
        when(dto.getCreatedByInstitution()).thenReturn(institution);
        when(dto.getActionUnit()).thenReturn(project);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(ID, INSTITUTIONS)).thenReturn(dto);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), eq(dto))).thenReturn(canEdit);
    }

    @Test
    void list_resolvesEveryKindOfRecord() {
        recordingUnitWithRight(false);
        SpecimenDTO specimen = mock(SpecimenDTO.class);
        when(specimen.getCreatedByInstitution()).thenReturn(institution);
        when(specimenService.findAccessibleById(ID, INSTITUTIONS)).thenReturn(Optional.of(specimen));
        PhaseDTO phase = mock(PhaseDTO.class);
        when(phase.getActionUnit()).thenReturn(project);
        when(phaseOpenApiService.requireAccessible(ID, person, INSTITUTIONS)).thenReturn(phase);
        ContainerDTO container = mock(ContainerDTO.class);
        when(container.getActionUnit()).thenReturn(project);
        when(containerOpenApiService.requireAccessible(ID, person, INSTITUTIONS)).thenReturn(container);
        DocumentDTO document = mock(DocumentDTO.class);
        when(document.getActionUnit()).thenReturn(project);
        when(documentOpenApiService.requireAccessible(ID, person, INSTITUTIONS)).thenReturn(document);
        StudyDTO study = mock(StudyDTO.class);
        when(study.getActionUnit()).thenReturn(project);
        when(studyOpenApiService.requireAccessible(ID, person, INSTITUTIONS)).thenReturn(study);

        for (ConfigurableTable table : ConfigurableTable.projectScopedTables()) {
            OnTheFlyFieldsResponse response = service.list(table, ID, person, INSTITUTIONS, "fr");
            assertTrue(response.fields().isEmpty(), table.name());
        }
    }

    @Test
    void list_refusesAProjectAndAnUnknownFind() {
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.list(ConfigurableTable.PROJECT, ID, person, INSTITUTIONS, "fr")));
        when(specimenService.findAccessibleById(ID, INSTITUTIONS)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.list(ConfigurableTable.MOBILIER, ID, person, INSTITUTIONS, "fr")));
    }

    @Test
    void list_refusesARecordWithoutOrganisation() {
        RecordingUnitDTO dto = mock(RecordingUnitDTO.class);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(ID, INSTITUTIONS)).thenReturn(dto);

        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.list(ConfigurableTable.UE, ID, person, INSTITUTIONS, "fr")));
    }

    @Test
    void list_describesTheFieldsAndTheirPanels() {
        recordingUnitWithRight(false);
        CustomFieldMeasurement field = measurement(7L, "Hauteur");
        field.setPanelName("general");
        when(onTheFlyFieldService.fieldsOf(ConfigurableTable.UE, ID)).thenReturn(List.of(field));

        OnTheFlyFieldsResponse response = service.list(ConfigurableTable.UE, ID, person, INSTITUTIONS, "fr");

        assertEquals(1, response.fields().size());
        assertEquals("Hauteur", response.fields().get("7").label());
        assertEquals("general", response.placements().get(0).panel());
    }

    @Test
    void writing_needsTheRightToEdit() {
        recordingUnitWithRight(false);
        OnTheFlyMeasurementRequest body = request(1L, null, null, 1L);

        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, body, person, INSTITUTIONS, "fr")));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.remove(ConfigurableTable.UE, ID, 7L, person, INSTITUTIONS, "fr")));
    }

    // ---- adding a measure ----

    @Test
    void add_buildsTheMeasureFromItsTypeNatureAndUnits() {
        recordingUnitWithRight(true);
        Concept type = concept(11L);
        Concept nature = concept(12L);
        when(conceptRepository.findById(11L)).thenReturn(Optional.of(type));
        when(conceptRepository.findById(12L)).thenReturn(Optional.of(nature));
        unitsFound(unit(1L, "m", UnitDefinition.Dimension.LENGTH), unit(2L, "cm", UnitDefinition.Dimension.LENGTH));
        when(labelService.findLabelOf(same(type), anyString())).thenReturn(label("Hauteur"));
        when(labelService.findLabelOf(same(nature), anyString())).thenReturn(label("maximale"));

        service.addMeasurement(ConfigurableTable.UE, ID,
                new OnTheFlyMeasurementRequest("general", 11L, 12L, 1L, List.of(2L), null, null, null), person, INSTITUTIONS, "fr");

        ArgumentCaptor<CustomFieldMeasurement> added = ArgumentCaptor.forClass(CustomFieldMeasurement.class);
        verify(onTheFlyFieldService).add(eq(ConfigurableTable.UE), eq(ID), eq("general"), added.capture());
        CustomFieldMeasurement field = added.getValue();
        assertEquals("Hauteur maximale", field.getLabel());
        assertSame(type, field.getMeasurementType());
        assertSame(nature, field.getMeasurementNature());
        assertEquals("m", field.getUnit().getSymbol());
        assertEquals(2, field.getAllowedUnits().size());
        assertFalse(field.getIsSystemField());
    }

    @Test
    void add_needsATypeAndUnitsOfOneDimension() {
        recordingUnitWithRight(true);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, null, person, INSTITUTIONS, "fr")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addMeasurement(ConfigurableTable.UE, ID,
                new OnTheFlyMeasurementRequest("p", null, null, 1L, List.of(), " ", null, null), person, INSTITUTIONS, "fr")));

        when(conceptRepository.findById(11L)).thenReturn(Optional.of(concept(11L)));
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(label("Hauteur"));
        OnTheFlyMeasurementRequest noUnit = request(11L, null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, noUnit, person, INSTITUTIONS, "fr")));

        when(unitDefinitionRepository.findAllById(any())).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, request(11L, null, null, 1L), person, INSTITUTIONS, "fr")));

        unitsFound(unit(1L, "m", UnitDefinition.Dimension.LENGTH), unit(2L, "g", UnitDefinition.Dimension.MASS));
        OnTheFlyMeasurementRequest mixed = new OnTheFlyMeasurementRequest("p", 11L, null, 1L, List.of(2L), null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, mixed, person, INSTITUTIONS, "fr")));
    }

    @Test
    void add_unknownConceptIsNotFound() {
        recordingUnitWithRight(true);
        when(conceptRepository.findById(11L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, request(11L, null, null, 1L), person, INSTITUTIONS, "fr")));
    }

    @Test
    void add_reusesALocalTypeThatAlreadyExists() {
        recordingUnitWithRight(true);
        Concept existing = concept(21L);
        ConceptAutocompleteDTO found = autocomplete(21L, "Poids");
        when(tableFieldConfigService.measureConcepts(eq(PROJECT_ID), eq(CustomFieldMeasurement.MEASUREMENT_TYPE_FIELD_CODE), eq("poids")))
                .thenReturn(List.of(found));
        when(conceptRepository.findById(21L)).thenReturn(Optional.of(existing));
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(label("Poids"));
        unitsFound(unit(1L, "g", UnitDefinition.Dimension.MASS));

        service.addMeasurement(ConfigurableTable.UE, ID,
                new OnTheFlyMeasurementRequest("p", null, null, 1L, List.of(), " poids ", null, null), person, INSTITUTIONS, "fr");

        verify(conceptRepository, never()).save(any());
    }

    @Test
    void add_createsALocalConceptWithoutUriWhenNoneFits() throws Exception {
        recordingUnitWithRight(true);
        Concept fieldConcept = concept(30L);
        fieldConcept.setVocabulary(new Vocabulary());
        ConceptFieldConfig config = mock(ConceptFieldConfig.class);
        when(config.getConcept()).thenReturn(fieldConcept);
        ConceptAutocompleteDTO other = autocomplete(99L, "Autre");
        when(tableFieldConfigService.measureConcepts(any(), anyString(), anyString())).thenReturn(List.of(other));
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), anyString(), eq(PROJECT_ID))).thenReturn(config);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(inv -> {
            Concept saved = inv.getArgument(0);
            saved.setId(31L);
            return saved;
        });
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(label("Largeur"));
        unitsFound(unit(1L, "m", UnitDefinition.Dimension.LENGTH));

        service.addMeasurement(ConfigurableTable.UE, ID,
                new OnTheFlyMeasurementRequest("p", null, null, 1L, List.of(), "Largeur", "  ", null), person, INSTITUTIONS, null);

        ArgumentCaptor<Concept> created = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository).save(created.capture());
        assertTrue(created.getValue().getExternalId().startsWith("local-"));
        assertNull(created.getValue().getUri());
        assertTrue(created.getValue().isLoaded());
        ArgumentCaptor<ConceptLabel> label = ArgumentCaptor.forClass(ConceptLabel.class);
        verify(conceptLabelRepository).save(label.capture());
        assertInstanceOf(ConceptPrefLabel.class, label.getValue());
        assertEquals("Largeur", label.getValue().getLabel());
        assertSame(fieldConcept, label.getValue().getParentConcept());
    }

    @Test
    void add_aLocalConceptNeedsTheFieldToBeConfigured() throws Exception {
        recordingUnitWithRight(true);
        when(tableFieldConfigService.measureConcepts(any(), anyString(), anyString())).thenReturn(List.of());
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), anyString(), any(Long.class)))
                .thenThrow(new NoConfigForFieldException("none"));

        OnTheFlyMeasurementRequest body = new OnTheFlyMeasurementRequest("p", null, null, 1L, List.of(), "Largeur", null, null);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, body, person, INSTITUTIONS, "fr")));
    }

    // ---- reusing a measure ----

    @Test
    void add_copiesAMeasureOfTheTable() {
        recordingUnitWithRight(true);
        CustomFieldMeasurement source = measurement(40L, "Hauteur");
        CustomFieldMeasurement copy = measurement(null, "Hauteur");
        when(onTheFlyFieldService.findReusableMeasurements(eq(ConfigurableTable.UE), eq(PROJECT_ID), eq(""), anyInt())).thenReturn(List.of(source));
        when(onTheFlyFieldService.copyOf(source)).thenReturn(copy);

        service.addMeasurement(ConfigurableTable.UE, ID,
                new OnTheFlyMeasurementRequest("general", null, null, null, null, null, null, 40L), person, INSTITUTIONS, "fr");

        verify(onTheFlyFieldService).add(ConfigurableTable.UE, ID, "general", copy);
    }

    @Test
    void add_cannotCopyAMeasureThatIsNotOfferedForReuse() {
        recordingUnitWithRight(true);
        when(onTheFlyFieldService.findReusableMeasurements(any(), any(), anyString(), anyInt())).thenReturn(List.of());
        OnTheFlyMeasurementRequest body = new OnTheFlyMeasurementRequest("p", null, null, null, null, null, null, 40L);

        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.addMeasurement(ConfigurableTable.UE, ID, body, person, INSTITUTIONS, "fr")));
    }

    @Test
    void existing_offersTheMeasuresNotYetOnTheRecord() {
        recordingUnitWithRight(false);
        CustomFieldMeasurement already = measurement(7L, "Hauteur");
        CustomFieldMeasurement other = measurement(8L, "Largeur");
        other.setUnit(unit(1L, "m", UnitDefinition.Dimension.LENGTH));
        when(onTheFlyFieldService.fieldsOf(ConfigurableTable.UE, ID)).thenReturn(List.of(already));
        when(onTheFlyFieldService.findReusableMeasurements(eq(ConfigurableTable.UE), eq(PROJECT_ID), eq("h"), anyInt()))
                .thenReturn(List.of(already, other, measurement(9L, "Poids")));

        var found = service.existingMeasurements(ConfigurableTable.UE, ID, "h", person, INSTITUTIONS, "fr");

        assertEquals(List.of(8L, 9L), found.stream().map(OnTheFlyFieldOpenApiService.ExistingMeasure::fieldId).toList());
        assertEquals("m", found.get(0).unit());
        assertNull(found.get(1).unit());
    }

    // ---- removing ----

    @Test
    void remove_unlinksTheField() {
        recordingUnitWithRight(true);
        when(onTheFlyFieldService.remove(ConfigurableTable.UE, ID, 7L)).thenReturn(true);

        assertNotNull(service.remove(ConfigurableTable.UE, ID, 7L, person, INSTITUTIONS, "fr"));
    }

    @Test
    void remove_unknownFieldIsNotFound() {
        recordingUnitWithRight(true);
        when(onTheFlyFieldService.remove(ConfigurableTable.UE, ID, 7L)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.remove(ConfigurableTable.UE, ID, 7L, person, INSTITUTIONS, "fr")));
    }

    // ---- what the overlay offers ----

    @Test
    void concepts_searchTheTypeOrTheNatureField() {
        recordingUnitWithRight(false);
        ConceptAutocompleteDTO height = autocomplete(1L, "Hauteur");
        ConceptAutocompleteDTO maximal = autocomplete(2L, "maximale");
        when(tableFieldConfigService.measureConcepts(PROJECT_ID, CustomFieldMeasurement.MEASUREMENT_TYPE_FIELD_CODE, "h"))
                .thenReturn(List.of(height));
        when(tableFieldConfigService.measureConcepts(PROJECT_ID, CustomFieldMeasurement.MEASUREMENT_NATURE_FIELD_CODE, "m"))
                .thenReturn(List.of(maximal));

        var types = service.measurementConcepts(ConfigurableTable.UE, ID, "TYPE", "h", person, INSTITUTIONS, "fr");
        var natures = service.measurementConcepts(ConfigurableTable.UE, ID, "nature", "m", person, INSTITUTIONS, "fr");

        assertEquals("Hauteur", types.get(0).label());
        assertEquals(2L, natures.get(0).id());
    }

    @Test
    void concepts_fallBackOnThePrefLabelWhenNoneIsToDisplay() {
        recordingUnitWithRight(false);
        ConceptAutocompleteDTO bare = mock(ConceptAutocompleteDTO.class);
        ConceptDTO concept = mock(ConceptDTO.class);
        when(concept.getId()).thenReturn(3L);
        when(bare.concept()).thenReturn(concept);
        when(bare.getOriginalPrefLabel()).thenReturn("Epaisseur");
        when(tableFieldConfigService.measureConcepts(any(), anyString(), any())).thenReturn(List.of(bare));

        var found = service.measurementConcepts(ConfigurableTable.UE, ID, "TYPE", null, person, INSTITUTIONS, "fr");

        assertEquals("Epaisseur", found.get(0).label());
    }

    @Test
    void units_areListedWithTheirDimension() {
        recordingUnitWithRight(false);
        when(unitDefinitionService.findOptions()).thenReturn(List.of(
                UnitDefinitionDTO.builder().id(1L).label("Mètre").symbol("m").dimension(UnitDefinition.Dimension.LENGTH).systemBase(true).build(),
                UnitDefinitionDTO.builder().id(2L).label("Sans").symbol("?").build()));

        var units = service.measurementUnits(ConfigurableTable.UE, ID, person, INSTITUTIONS, "fr");

        assertEquals("LENGTH", units.get(0).dimension());
        assertTrue(units.get(0).systemBase());
        assertNull(units.get(1).dimension());
    }

    // ---- helpers ----

    private HttpStatus status(Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private static OnTheFlyMeasurementRequest request(Long type, Long nature, String panel, Long unit) {
        return new OnTheFlyMeasurementRequest(panel == null ? "p" : panel, type, nature, unit, List.of(), null, null, null);
    }

    private static CustomFieldMeasurement measurement(Long id, String label) {
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().build();
        field.setId(id);
        field.setLabel(label);
        return field;
    }

    private static Concept concept(Long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }

    private static UnitDefinition unit(Long id, String symbol, UnitDefinition.Dimension dimension) {
        return UnitDefinition.builder().id(id).symbol(symbol).label(symbol).dimension(dimension).build();
    }

    private void unitsFound(UnitDefinition... units) {
        when(unitDefinitionRepository.findAllById(any())).thenReturn(List.of(units));
    }

    private static fr.siamois.domain.models.vocabulary.label.ConceptLabel label(String text) {
        ConceptPrefLabel label = new ConceptPrefLabel();
        label.setLabel(text);
        return label;
    }

    private static ConceptAutocompleteDTO autocomplete(Long conceptId, String text) {
        ConceptAutocompleteDTO dto = mock(ConceptAutocompleteDTO.class);
        ConceptDTO concept = mock(ConceptDTO.class);
        when(concept.getId()).thenReturn(conceptId);
        ConceptLabelDTO shown = mock(ConceptLabelDTO.class);
        when(shown.getLabel()).thenReturn(text);
        when(dto.concept()).thenReturn(concept);
        when(dto.getConceptLabelToDisplay()).thenReturn(shown);
        return dto;
    }
}
