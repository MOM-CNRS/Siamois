package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentOpenApiMapperTest {

  @Mock
  private ConceptMapper conceptMapper;
  @Mock
  private ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;

  @InjectMocks
  private ProjectDocumentOpenApiMapper mapper;

  private Document document;

  @BeforeEach
  void setUp() {
    document = new Document();
    document.setId(10L);
    document.setTitle("Plan");
    document.setDescription("Description");
    document.setFileName("plan.pdf");
    document.setMimeType("application/pdf");
    document.setUrl("https://example.com/plan.pdf");
    document.setFileCode("FC01");
    document.setSize(1024L);
    document.setMd5Sum("abc123");
  }

  @Test
  void toResource_mapsScalarFields() {
    ProjectDocumentResource resource = mapper.toResource(document);

    assertThat(resource.getResourceType()).isEqualTo("documents");
    assertThat(resource.getId()).isEqualTo("10");
    assertThat(resource.getTitle()).isEqualTo("Plan");
    assertThat(resource.getDescription()).isEqualTo("Description");
    assertThat(resource.getFileName()).isEqualTo("plan.pdf");
    assertThat(resource.getMimeType()).isEqualTo("application/pdf");
    assertThat(resource.getUrl()).isEqualTo("https://example.com/plan.pdf");
    assertThat(resource.getFileCode()).isEqualTo("FC01");
    assertThat(resource.getSize()).isEqualTo(1024L);
    assertThat(resource.getMd5Sum()).isEqualTo("abc123");
    assertThat(resource.getNature()).isNull();
    assertThat(resource.getScale()).isNull();
    assertThat(resource.getFormat()).isNull();
  }

  @Test
  void toResource_nullId_mapsNullResourceId() {
    document.setId(null);

    ProjectDocumentResource resource = mapper.toResource(document);

    assertThat(resource.getId()).isNull();
  }

  @Test
  void toResource_mapsConceptRelationships() {
    Concept nature = new Concept();
    nature.setId(1L);
    Concept scale = new Concept();
    scale.setId(2L);
    Concept format = new Concept();
    format.setId(3L);
    document.setNature(nature);
    document.setScale(scale);
    document.setFormat(format);

    when(conceptMapper.convert(any(Concept.class))).thenAnswer(invocation -> {
      Concept concept = invocation.getArgument(0);
      ConceptDTO dto = new ConceptDTO();
      dto.setId(concept.getId());
      return dto;
    });
    when(conceptResourceIdentifierMapper.convert(any(ConceptDTO.class))).thenAnswer(invocation -> {
      ConceptDTO dto = invocation.getArgument(0);
      return new ConceptResourceIdentifier("concepts", String.valueOf(dto.getId()));
    });

    ProjectDocumentResource resource = mapper.toResource(document);

    assertThat(resource.getNature().getData().getId()).isEqualTo("1");
    assertThat(resource.getScale().getData().getId()).isEqualTo("2");
    assertThat(resource.getFormat().getData().getId()).isEqualTo("3");
    verify(conceptMapper, times(3)).convert(any(Concept.class));
    verify(conceptResourceIdentifierMapper, times(3)).convert(any(ConceptDTO.class));
  }
}
