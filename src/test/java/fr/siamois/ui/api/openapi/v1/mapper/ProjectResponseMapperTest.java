package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

        ConceptResourceIdentifier typeIdent = new ConceptResourceIdentifier();
        typeIdent.setResourceType("concepts");
        typeIdent.setId("99");
        lenient().when(conceptResourceIdentifierMapper.convert(typeDto)).thenReturn(typeIdent);

        ConceptPrefLabelDTO defaultLabel = new ConceptPrefLabelDTO();
        defaultLabel.setLabel("label");
        lenient().when(labelService.findLabelOf(any(ConceptDTO.class), any())).thenReturn(defaultLabel);
    }

    @Test
    void toResource_setsCategorieViaConceptMapperAndLabelService() {
        ConceptPrefLabelDTO labelDto = new ConceptPrefLabelDTO();
        labelDto.setLabel("Fouille programmée");
        when(labelService.findLabelOf(typeDto, "fr")).thenReturn(labelDto);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getType().getResolvedLabel()).isEqualTo("Fouille programmée");
        verify(labelService).findLabelOf(typeDto, "fr");
    }

    @Test
    void toResource_categorieFallback_whenInvertConvertThrows() {
        ConceptPrefLabelDTO fallback = new ConceptPrefLabelDTO();
        fallback.setLabel("[TYPE-EXT]");
        when(labelService.findLabelOf(typeDto, "fr")).thenReturn(fallback);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getType().getResolvedLabel()).isEqualTo("[TYPE-EXT]");
    }

    @Test
    void toResource_usesLangFromParameter() {
        ConceptPrefLabelDTO labelDto = new ConceptPrefLabelDTO();
        labelDto.setLabel("Survey");
        when(labelService.findLabelOf(typeDto, "en")).thenReturn(labelDto);

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        ProjectResource r = projectResponseMapper.toResource(row, "en");

        assertThat(r.getType().getResolvedLabel()).isEqualTo("Survey");
        verify(labelService).findLabelOf(typeDto, "en");
    }

    @Test
    void toResource_localisation_communeAndPrecises() {
        SpatialUnitSummaryDTO main = new SpatialUnitSummaryDTO();
        main.setId(1L);
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

        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 1L, 2L);
        ProjectResource r = projectResponseMapper.toResource(row, "fr");

        assertThat(r.getMainLocation().getName()).isEqualTo("Commune A");
    }

    @Test
    void toResource_singleArgDelegatesToFr() {
        AccessibleProjectForApi row = new AccessibleProjectForApi(dto, 0L, 0L);
        projectResponseMapper.toResource(row);

        verify(labelService).findLabelOf(typeDto, "fr");
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

        ConceptPrefLabelDTO labelDto = new ConceptPrefLabelDTO();
        labelDto.setLabel("Excavation");
        when(labelService.findLabelOf(typeDto, "fr")).thenReturn(labelDto);

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getType().getResolvedLabel()).isNotNull();
        assertThat(r.getType().getId()).isEqualTo("99");
        assertThat(r.getType().getResolvedLabel()).isEqualTo("Excavation");
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

        ProjectResource r = projectResponseMapper.toResource(new AccessibleProjectForApi(dto, 0L, 0L), "fr");

        assertThat(r.getMainLocation()).isNotNull();
        assertThat(r.getMainLocation().getId()).isEqualTo("100");
        assertThat(r.getMainLocation().getName()).isEqualTo("Lieu");
    }
}
