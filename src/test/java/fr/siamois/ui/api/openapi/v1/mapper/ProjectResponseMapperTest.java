package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.ActionCodeDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectResponseMapperTest {

    @Mock
    private ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private LabelService labelService;

    @InjectMocks
    private ProjectResponseMapper projectResponseMapper;

    private ActionUnitDTO dto;
    private ConceptDTO typeDto;
    private Concept conceptEntity;

    @BeforeEach
    void setUp() {
        dto = new ActionUnitDTO();
        dto.setId(10L);
        dto.setName("Projet X");
        dto.setIdentifier("PX-1");
        dto.setFullIdentifier("ORG-PX-1");

        typeDto = new ConceptDTO();
        typeDto.setId(99L);
        typeDto.setExternalId("TYPE-EXT");
        dto.setType(typeDto);

        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        dto.setCreatedByInstitution(inst);

        conceptEntity = new Concept();

        ConceptResourceIdentifier typeIdent = new ConceptResourceIdentifier();
        typeIdent.setResourceType("concepts");
        typeIdent.setId("99");
        when(conceptResourceIdentifierMapper.convert(typeDto)).thenReturn(typeIdent);
    }

    @Test
    void toResource_setsCategorieViaConceptMapperAndLabelService() {
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("Fouille programmée");
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(label);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getCategorie()).isEqualTo("Fouille programmée");
        verify(conceptMapper).invertConvert(typeDto);
        verify(labelService).findLabelOf(conceptEntity, "fr");
    }

    @Test
    void toResource_categorieFallback_whenInvertConvertThrows() {
        when(conceptMapper.invertConvert(typeDto)).thenThrow(new RuntimeException("conversion"));

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getCategorie()).isEqualTo("[TYPE-EXT]");
    }

    @Test
    void toResource_usesLangFromParameter() {
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("Survey");
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        when(labelService.findLabelOf(conceptEntity, "en")).thenReturn(label);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "en");

        assertThat(r.getCategorie()).isEqualTo("Survey");
        verify(labelService).findLabelOf(conceptEntity, "en");
    }

    @Test
    void toResource_localisation_communeAndPrecises() {
        SpatialUnitSummaryDTO main = new SpatialUnitSummaryDTO();
        main.setName("Commune A");
        main.setCode("75001");
        dto.setMainLocation(main);

        SpatialUnitSummaryDTO ctx1 = new SpatialUnitSummaryDTO();
        ctx1.setName("Zone nord");
        SpatialUnitSummaryDTO ctx2 = new SpatialUnitSummaryDTO();
        ctx2.setName("Commune A");
        ctx2.setCode("75001");
        Set<SpatialUnitSummaryDTO> ctx = new LinkedHashSet<>();
        ctx.add(ctx1);
        ctx.add(ctx2);
        dto.setSpatialContext(ctx);

        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("T");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(label);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 1L, 2L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getLocalisation().getCommuneOuLocalisation()).isEqualTo("Commune A (75001)");
        assertThat(r.getLocalisation().getLocalisationsPrecises()).containsExactly("Zone nord");
    }

    @Test
    void toResource_singleArgDelegatesToFr() {
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("L");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(label);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        projectResponseMapper.toResource(row);

        verify(labelService).findLabelOf(conceptEntity, "fr");
    }

    @Test
    void toResource_setsCodeOperationArcheologique() {
        ActionCodeDTO ac = new ActionCodeDTO();
        ac.setCode("OA-999");
        dto.setPrimaryActionCode(ac);
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("L");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(label);

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getCodeOperationArcheologique()).isEqualTo("OA-999");
    }

    @Test
    void toResource_typeConcept_containsVocabularyAndLabel() {
        VocabularyType vt = mock(VocabularyType.class);
        when(vt.getLabel()).thenReturn("Thesaurus");
        VocabularyDTO voc = VocabularyDTO.builder()
                .id(50L)
                .externalVocabularyId("EXT-VOC")
                .baseUri("http://example/theso")
                .type(vt)
                .build();
        typeDto.setVocabulary(voc);
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("Excavation");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(label);

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getTypeConcept()).isNotNull();
        assertThat(r.getTypeConcept().getVocabularyId()).isEqualTo(50L);
        assertThat(r.getTypeConcept().getVocabularyExternalId()).isEqualTo("EXT-VOC");
        assertThat(r.getTypeConcept().getVocabularyBaseUri()).isEqualTo("http://example/theso");
        assertThat(r.getTypeConcept().getVocabularyTypeLabel()).isEqualTo("Thesaurus");
        assertThat(r.getTypeConcept().getConceptId()).isEqualTo(99L);
        assertThat(r.getTypeConcept().getDisplayLabel()).isEqualTo("Excavation");
    }

    @Test
    void toResource_actionCodeTypeConcept_whenPrimaryActionCodeHasType() {
        ConceptDTO codeType = new ConceptDTO();
        codeType.setId(3L);
        codeType.setExternalId("CT-1");
        ActionCodeDTO ac = new ActionCodeDTO();
        ac.setCode("X");
        ac.setType(codeType);
        dto.setPrimaryActionCode(ac);

        Concept codeTypeEntity = new Concept();
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        when(conceptMapper.invertConvert(codeType)).thenReturn(codeTypeEntity);
        ConceptLabel l1 = mock(ConceptLabel.class);
        when(l1.getLabel()).thenReturn("T1");
        ConceptLabel l2 = mock(ConceptLabel.class);
        when(l2.getLabel()).thenReturn("T2");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(l1);
        when(labelService.findLabelOf(codeTypeEntity, "fr")).thenReturn(l2);

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getActionCodeTypeConcept()).isNotNull();
        assertThat(r.getActionCodeTypeConcept().getConceptId()).isEqualTo(3L);
        assertThat(r.getActionCodeTypeConcept().getDisplayLabel()).isEqualTo("T2");
    }

    @Test
    void toResource_mainLocationCategoryConcept() {
        ConceptDTO cat = new ConceptDTO();
        cat.setId(8L);
        cat.setExternalId("CAT-1");
        SpatialUnitSummaryDTO main = new SpatialUnitSummaryDTO();
        main.setId(100L);
        main.setName("Lieu");
        main.setCategory(cat);
        dto.setMainLocation(main);

        Concept catEntity = new Concept();
        when(conceptMapper.invertConvert(typeDto)).thenReturn(conceptEntity);
        when(conceptMapper.invertConvert(cat)).thenReturn(catEntity);
        ConceptLabel l1 = mock(ConceptLabel.class);
        when(l1.getLabel()).thenReturn("TypeOp");
        ConceptLabel l2 = mock(ConceptLabel.class);
        when(l2.getLabel()).thenReturn("Carre");
        when(labelService.findLabelOf(conceptEntity, "fr")).thenReturn(l1);
        when(labelService.findLabelOf(catEntity, "fr")).thenReturn(l2);

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getMainLocationCategoryConcept()).isNotNull();
        assertThat(r.getMainLocationCategoryConcept().getConceptId()).isEqualTo(8L);
        assertThat(r.getMainLocationCategoryConcept().getDisplayLabel()).isEqualTo("Carre");
    }
}
