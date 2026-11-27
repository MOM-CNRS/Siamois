package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The stratigraphic relationships of the bulk actions, written as links of the stratigraphic definitions. */
class RecordingUnitStratigraphyWriteServiceTest {

    private static final Set<Long> REACH = Set.of(3L);

    private final RecordingUnitService recordingUnitService = mock(RecordingUnitService.class);
    private final RelationLinkService linkService = mock(RelationLinkService.class);
    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationLinkRepository linkRepository = mock(RelationLinkRepository.class);
    private final RecordingUnitStratigraphyWriteService service = new RecordingUnitStratigraphyWriteService(
            recordingUnitService, linkService, definitionRepository, linkRepository);

    private final PersonDTO person = new PersonDTO();

    @BeforeEach
    void setUp() {
        RecordingUnit owner = new RecordingUnit();
        owner.setId(12L);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity("12", REACH, null))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(owner, null));
        when(definitionRepository.findByField(-328L)).thenReturn(Optional.of(definition(10L)));
        when(definitionRepository.findByField(-330L)).thenReturn(Optional.of(definition(11L)));
    }

    @Test
    void anAnteriorUnitIsLinkedThroughTheOwnersSourceField() {
        service.add("12", request(5L, "anterior", 201L), person, REACH);

        RelationLinkService.CreateRequest created = created();
        assertThat(created.definitionId()).isEqualTo(10L);
        assertThat(created.side()).isEqualTo(RelationSide.SOURCE);
        assertThat(created.edited()).isEqualTo(new RelationLinkService.End(RelationEndKind.RECORDING_UNIT, 12L));
        assertThat(created.other()).isEqualTo(new RelationLinkService.End(RelationEndKind.RECORDING_UNIT, 5L));
        assertThat(created.qualifier()).isEqualTo(201L);
    }

    @Test
    void aPosteriorUnitIsLinkedThroughTheMirrorFieldAndASynchronousOneThroughTheSynchrony() {
        service.add("12", request(5L, "posterior", 301L), person, REACH);
        assertThat(created().side()).isEqualTo(RelationSide.TARGET);

        reset(linkService);
        service.add("12", request(5L, "synchronous", null), person, REACH);
        assertThat(created().definitionId()).isEqualTo(11L);
    }

    @Test
    void twoUnitsAlreadyRelatedAreRefused() {
        when(linkRepository.findStratigraphicBetween(12L, 5L)).thenReturn(List.of(new RelationLink()));

        var body = request(5L, "anterior", null);
        assertThatThrownBy(() -> service.add("12", body, person, REACH))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("déjà reliées");
        verifyNoInteractions(linkService);
    }

    @Test
    void anUnknownPositionIsABadRequest() {
        var body = request(5L, "left", null);
        assertThatThrownBy(() -> service.add("12", body, person, REACH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removesTheLinkOfThePairFromTheOwnersSide() {
        RelationLink link = new RelationLink();
        link.setId(900L);
        link.setSourceId(5L);
        link.setTargetId(12L);
        when(linkRepository.findStratigraphicBetween(12L, 5L)).thenReturn(List.of(link));

        service.remove("12", 5L, person, REACH);

        verify(linkService).delete(900L, RelationSide.TARGET, person, REACH);
    }

    @Test
    void removingAPairWithoutALinkIsA404() {
        assertThatThrownBy(() -> service.remove("12", 5L, person, REACH))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("introuvable");
    }

    private RelationLinkService.CreateRequest created() {
        ArgumentCaptor<RelationLinkService.CreateRequest> request = ArgumentCaptor.forClass(RelationLinkService.CreateRequest.class);
        verify(linkService).create(request.capture(), eq(person), eq(REACH));
        return request.getValue();
    }

    private static RecordingUnitStratigraphicRelationshipRequest request(long related, String position, Long type) {
        RecordingUnitStratigraphicRelationshipRequest request = new RecordingUnitStratigraphicRelationshipRequest();
        request.setRelatedRecordingUnitId(related);
        request.setPosition(position);
        request.setTypeConceptId(type);
        return request;
    }

    private static RelationDefinition definition(long id) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        return definition;
    }
}
