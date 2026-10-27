package fr.siamois.domain.services.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.CustomFieldPendingCandidate;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.api.CandidateApi;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.CandidateDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptRemoteAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.CustomFieldPendingCandidateRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldCandidateServiceTest {

    private static final long INSTITUTION_ID = 1L;
    private static final long PROJECT_ID = 2L;
    private static final long PERSON_ID = 3L;
    private static final String BASE_URI = "https://opentheso.example";

    @Mock
    private CandidateApi candidateApi;
    @Mock
    private ConceptApi conceptApi;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private CustomFieldRepository customFieldRepository;
    @Mock
    private CustomFieldPendingCandidateRepository pendingCandidateRepository;
    @Mock
    private DocumentationThesaurusRepository documentationThesaurusRepository;
    @Mock
    private VocabularyKeyRepository vocabularyKeyRepository;

    @InjectMocks
    private CustomFieldCandidateService service;

    private Vocabulary vocabulary;
    private CustomField field;
    private VocabularyApiKey apiKey;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(INSTITUTION_ID);
        institution.setName("Institution");
        PersonDTO person = new PersonDTO();
        person.setId(PERSON_ID);
        ExecutionContextHolder.set(new UserInfo(institution, person, "fr"));

        vocabulary = new Vocabulary();
        vocabulary.setId(7L);
        vocabulary.setBaseUri(BASE_URI);
        vocabulary.setExternalVocabularyId("th231");

        field = CustomFieldText.builder().id(10L).label("Couleur").hint("La couleur de la pâte").build();

        apiKey = new VocabularyApiKey();
        apiKey.setVocabulary(vocabulary);
        apiKey.setEncryptedKey("enc");

        lenient().when(pendingCandidateRepository.findByFieldIdAndVocabularyId(any(), any())).thenReturn(Optional.empty());
        lenient().when(conceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    private void projectThesaurus() {
        DocumentationThesaurus thesaurus = new DocumentationThesaurus();
        thesaurus.setVocabulary(vocabulary);
        when(documentationThesaurusRepository.findByActionUnitId(PROJECT_ID)).thenReturn(Optional.of(thesaurus));
    }

    private void withApiKey() {
        when(vocabularyKeyRepository.findById(any())).thenReturn(Optional.of(apiKey));
    }

    private static ConceptRemoteAutocompleteDTO result(String label, boolean altLabel) {
        return new ConceptRemoteAutocompleteDTO(42L, BASE_URI + "/?idc=42&idt=th231", label, altLabel, null);
    }

    @Test
    void submitCandidateFor_acceptedCandidate_isPendingWithoutSearching() throws JsonProcessingException {
        projectThesaurus();
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(true);

        service.submitCandidateFor(field, PROJECT_ID);

        CustomFieldPendingCandidate pending = savedPendingCandidate();
        assertThat(pending.getField()).isSameAs(field);
        assertThat(pending.getVocabulary()).isSameAs(vocabulary);
        assertThat(pending.getLang()).isEqualTo("fr");
        assertThat(field.getConcept()).isNull();
        verifyNoInteractions(conceptApi, customFieldRepository);

        ArgumentCaptor<CandidateDTO> candidate = ArgumentCaptor.forClass(CandidateDTO.class);
        verify(candidateApi).postCandidateFor(eq(apiKey), candidate.capture());
        assertThat(candidate.getValue().getThesoId()).isEqualTo("th231");
        assertThat(candidate.getValue().getTerme()[0].getValue()).isEqualTo("Couleur");
        assertThat(candidate.getValue().getTerme()[0].getLang()).isEqualTo("fr");
        assertThat(candidate.getValue().getDefinition()[0].getValue()).isEqualTo("La couleur de la pâte");
    }

    @Test
    void submitCandidateFor_refusedCandidate_linksTheExactPrefLabelMatchAsUnloadedStub() throws JsonProcessingException {
        projectThesaurus();
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(false);
        when(conceptApi.fetchRemoteAutocomplete(BASE_URI, "th231", "Couleur", "fr"))
                .thenReturn(List.of(result("Couleur de pâte", false), result("Couleur", true), result("Couleur", false)));
        when(conceptRepository.findConceptByExternalIdIgnoreCase("th231", "42")).thenReturn(Optional.empty());

        service.submitCandidateFor(field, PROJECT_ID);

        Concept concept = field.getConcept();
        assertThat(concept).isNotNull();
        assertThat(concept.isLoaded()).isFalse();
        assertThat(concept.getExternalId()).isEqualTo("42");
        assertThat(concept.getUri()).isEqualTo(BASE_URI + "/?idc=42&idt=th231");
        assertThat(concept.getVocabulary()).isSameAs(vocabulary);
        verify(customFieldRepository).save(field);
        verify(pendingCandidateRepository, never()).save(any());
    }

    @Test
    void submitCandidateFor_refusedCandidateAndNoMatch_isPending() throws JsonProcessingException {
        projectThesaurus();
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(false);
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(result("couleur", false)));

        service.submitCandidateFor(field, PROJECT_ID);

        assertThat(savedPendingCandidate().getField()).isSameAs(field);
        assertThat(field.getConcept()).isNull();
    }

    @Test
    void submitCandidateFor_withoutApiKey_searchesWithoutPosting() throws JsonProcessingException {
        projectThesaurus();
        when(vocabularyKeyRepository.findById(any())).thenReturn(Optional.empty());
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());

        service.submitCandidateFor(field, PROJECT_ID);

        savedPendingCandidate();
        verifyNoInteractions(candidateApi);
    }

    @Test
    void submitCandidateFor_unreachableThesaurus_isPending() throws JsonProcessingException {
        projectThesaurus();
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(false);
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("down"));

        service.submitCandidateFor(field, PROJECT_ID);

        savedPendingCandidate();
    }

    @Test
    void submitCandidateFor_fallsBackToTheInstitutionThesaurus() throws JsonProcessingException {
        DocumentationThesaurus thesaurus = new DocumentationThesaurus();
        thesaurus.setVocabulary(vocabulary);
        when(documentationThesaurusRepository.findByActionUnitId(PROJECT_ID)).thenReturn(Optional.empty());
        when(documentationThesaurusRepository.findOfInstitution(INSTITUTION_ID)).thenReturn(Optional.of(thesaurus));
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(true);

        service.submitCandidateFor(field, PROJECT_ID);

        assertThat(savedPendingCandidate().getVocabulary()).isSameAs(vocabulary);
    }

    @Test
    void submitCandidateFor_withoutDocumentationThesaurus_doesNothing() {
        when(documentationThesaurusRepository.findByActionUnitId(PROJECT_ID)).thenReturn(Optional.empty());
        when(documentationThesaurusRepository.findOfInstitution(INSTITUTION_ID)).thenReturn(Optional.empty());

        service.submitCandidateFor(field, PROJECT_ID);

        verifyNoInteractions(candidateApi, conceptApi, pendingCandidateRepository);
    }

    @Test
    void submitCandidateFor_fieldWithAConcept_isLeftAlone() {
        field.setConcept(new Concept());

        service.submitCandidateFor(field, PROJECT_ID);

        verifyNoInteractions(candidateApi, conceptApi, pendingCandidateRepository, documentationThesaurusRepository);
    }

    @Test
    void synchronizePendingOf_linksTheFieldsFoundAndDeletesTheirPendingCandidate() throws JsonProcessingException {
        CustomField other = CustomFieldText.builder().id(11L).label("Forme").build();
        CustomFieldPendingCandidate found = pending(field);
        CustomFieldPendingCandidate notFound = pending(other);
        when(pendingCandidateRepository.findAllByVocabularyId(7L)).thenReturn(List.of(found, notFound));
        when(conceptApi.fetchRemoteAutocomplete(BASE_URI, "th231", "Couleur", "fr")).thenReturn(List.of(result("Couleur", false)));
        when(conceptApi.fetchRemoteAutocomplete(BASE_URI, "th231", "Forme", "fr")).thenReturn(List.of());
        Concept existing = new Concept();
        when(conceptRepository.findConceptByExternalIdIgnoreCase("th231", "42")).thenReturn(Optional.of(existing));

        service.synchronizePendingOf(vocabulary);

        assertThat(field.getConcept()).isSameAs(existing);
        assertThat(other.getConcept()).isNull();
        verify(customFieldRepository).save(field);
        verify(pendingCandidateRepository).delete(found);
        verify(pendingCandidateRepository, never()).delete(notFound);
        verify(conceptRepository, never()).save(any());
    }

    @Test
    void findConceptOf_loadsAStubOnFirstAccess() {
        Concept stub = new Concept();
        field.setConcept(stub);

        assertThat(service.findConceptOf(field)).containsSame(stub);
        verify(conceptService).loadIfUnloaded(stub);
    }

    @Test
    void transfer_givesTheReplacementTheConceptOfTheField() {
        Concept concept = new Concept();
        field.setConcept(concept);
        CustomField replacement = CustomFieldText.builder().id(12L).label("Couleur").build();

        service.transfer(field, replacement);

        assertThat(replacement.getConcept()).isSameAs(concept);
        verify(customFieldRepository).save(replacement);
    }

    @Test
    void transfer_givesTheReplacementThePendingCandidatesOfTheField() {
        CustomField replacement = CustomFieldText.builder().id(12L).label("Couleur").build();
        when(pendingCandidateRepository.findAllByFieldId(10L)).thenReturn(List.of(pending(field)));

        service.transfer(field, replacement);

        CustomFieldPendingCandidate copy = savedPendingCandidate();
        assertThat(copy.getField()).isSameAs(replacement);
        assertThat(copy.getVocabulary()).isSameAs(vocabulary);
    }

    @Test
    void transfer_givesTheReplacementTheDocumentationOfAMeasurementNotItsType() {
        Concept documentation = new Concept();
        CustomFieldMeasurement measurement = CustomFieldMeasurement.builder().id(13L).label("Hauteur")
                .measurementType(new Concept()).concept(documentation).build();
        CustomField replacement = CustomFieldText.builder().id(12L).label("Hauteur").build();

        service.transfer(measurement, replacement);

        assertThat(replacement.getConcept()).isSameAs(documentation);
    }

    @Test
    void submitCandidateFor_measurementWithAType_isStillDocumented() throws JsonProcessingException {
        CustomFieldMeasurement measurement = CustomFieldMeasurement.builder().id(13L).label("Hauteur")
                .measurementType(new Concept()).build();
        projectThesaurus();
        withApiKey();
        when(candidateApi.postCandidateFor(eq(apiKey), any())).thenReturn(true);

        service.submitCandidateFor(measurement, PROJECT_ID);

        assertThat(savedPendingCandidate().getField()).isSameAs(measurement);
    }

    private CustomFieldPendingCandidate savedPendingCandidate() {
        ArgumentCaptor<CustomFieldPendingCandidate> saved = ArgumentCaptor.forClass(CustomFieldPendingCandidate.class);
        verify(pendingCandidateRepository).save(saved.capture());
        return saved.getValue();
    }

    private CustomFieldPendingCandidate pending(CustomField of) {
        CustomFieldPendingCandidate candidate = new CustomFieldPendingCandidate();
        candidate.setField(of);
        candidate.setVocabulary(vocabulary);
        candidate.setLang("fr");
        return candidate;
    }
}
