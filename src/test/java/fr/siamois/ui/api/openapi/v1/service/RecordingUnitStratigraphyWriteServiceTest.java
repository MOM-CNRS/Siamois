package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipUpdateRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecordingUnitStratigraphyWriteServiceTest {

    private static final Set<Long> SCOPE = Set.of(1L);

    private final RecordingUnitService units = mock(RecordingUnitService.class);
    private final StratigraphicRelationshipRepository relationships = mock(StratigraphicRelationshipRepository.class);
    private final ConceptRepository concepts = mock(ConceptRepository.class);
    private final ProfilePermissionService permissions = mock(ProfilePermissionService.class);
    private final RecordingUnitStratigraphyWriteService service =
            new RecordingUnitStratigraphyWriteService(units, relationships, concepts, permissions);
    private final PersonDTO person = new PersonDTO();

    private final RecordingUnit owner = unit(10L, 5L);
    private final RecordingUnit other = unit(20L, 5L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
        expose(owner);
        expose(other);
        when(permissions.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(true);
    }

    private static RecordingUnit unit(long id, long actionUnitId) {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(id);
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(actionUnitId);
        unit.setActionUnit(actionUnit);
        return unit;
    }

    private void expose(RecordingUnit unit) {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(unit.getId());
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        dto.setCreatedByInstitution(institution);
        when(units.findAccessibleRecordingUnitWithEntity(eq(String.valueOf(unit.getId())), eq(SCOPE), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(unit, dto));
    }

    private static RecordingUnitStratigraphicRelationshipRequest request(String position) {
        RecordingUnitStratigraphicRelationshipRequest request = new RecordingUnitStratigraphicRelationshipRequest();
        request.setRelatedRecordingUnitId(20L);
        request.setPosition(position);
        return request;
    }

    private StratigraphicRelationship saved() {
        ArgumentCaptor<StratigraphicRelationship> captor = ArgumentCaptor.forClass(StratigraphicRelationship.class);
        verify(relationships).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aPosteriorUnitIsStoredAfterTheOwner() {
        service.add("10", request("posterior"), person, SCOPE);

        StratigraphicRelationship saved = saved();
        assertThat(saved.getUnit1()).isSameAs(owner);
        assertThat(saved.getUnit2()).isSameAs(other);
        assertThat(saved.getIsAsynchronous()).isTrue();
        assertThat(saved.getUncertain()).isFalse();
    }

    @Test
    void anAnteriorUnitIsStoredBeforeTheOwner() {
        RecordingUnitStratigraphicRelationshipRequest request = request("anterior");
        request.setTypeConceptId(7L);
        request.setUncertain(true);
        Concept concept = new Concept();
        when(concepts.findById(7L)).thenReturn(Optional.of(concept));

        service.add("10", request, person, SCOPE);

        StratigraphicRelationship saved = saved();
        assertThat(saved.getUnit1()).isSameAs(other);
        assertThat(saved.getUnit2()).isSameAs(owner);
        assertThat(saved.getConcept()).isSameAs(concept);
        // The concept reads from the owner (the second unit here) toward the other.
        assertThat(saved.getConceptDirection()).isFalse();
        assertThat(saved.getUncertain()).isTrue();
    }

    @Test
    void aSynchronousRelationshipIsNotAsynchronous() {
        service.add("10", request("synchronous"), person, SCOPE);

        assertThat(saved().getIsAsynchronous()).isFalse();
    }

    @Test
    void refusesAnAlreadyRelatedPairWhicheverItsOrder() {
        when(relationships.findByUnit1AndUnit2(other, owner)).thenReturn(Optional.of(new StratigraphicRelationship()));

        assertThatThrownBy(() -> service.add("10", request("posterior"), person, SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(relationships, never()).save(any());
    }

    @Test
    void refusesACycle() {
        // other is posterior to owner already is the wrong way: owner -> X -> other, and we add other as anterior to owner.
        RecordingUnit between = unit(30L, 5L);
        StratigraphicRelationship ownerToBetween = relation(owner, between);
        StratigraphicRelationship betweenToOther = relation(between, other);
        when(relationships.findAllInvolvingRecordingUnitId(10L)).thenReturn(List.of(ownerToBetween));
        when(relationships.findAllInvolvingRecordingUnitId(30L)).thenReturn(List.of(ownerToBetween, betweenToOther));
        when(relationships.findAllInvolvingRecordingUnitId(20L)).thenReturn(List.of(betweenToOther));

        // other would be before owner, though owner -> between -> other.
        assertThatThrownBy(() -> service.add("10", request("anterior"), person, SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void refusesUnitsOfTwoOperations() {
        RecordingUnit elsewhere = unit(20L, 6L);
        expose(elsewhere);

        assertThatThrownBy(() -> service.add("10", request("posterior"), person, SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void refusesWithoutWritePermission() {
        when(permissions.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(false);

        assertThatThrownBy(() -> service.add("10", request("posterior"), person, SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updatesTheTypeAndTheUncertaintyOfARelationship() {
        StratigraphicRelationship existing = relation(other, owner);
        when(relationships.findByUnit1AndUnit2(owner, other)).thenReturn(Optional.empty());
        when(relationships.findByUnit1AndUnit2(other, owner)).thenReturn(Optional.of(existing));
        RecordingUnitStratigraphicRelationshipUpdateRequest body = new RecordingUnitStratigraphicRelationshipUpdateRequest();
        body.setUncertain(true);

        service.update("10", 20L, body, person, SCOPE);

        assertThat(existing.getUncertain()).isTrue();
        assertThat(existing.getConcept()).isNull();
        verify(relationships).save(existing);
    }

    @Test
    void removesARelationshipWhicheverItsOrder() {
        StratigraphicRelationship existing = relation(other, owner);
        when(relationships.findByUnit1AndUnit2(owner, other)).thenReturn(Optional.empty());
        when(relationships.findByUnit1AndUnit2(other, owner)).thenReturn(Optional.of(existing));

        service.remove("10", 20L, person, SCOPE);

        verify(relationships).delete(existing);
    }

    @Test
    void removingAnUnknownRelationshipIsA404() {
        when(relationships.findByUnit1AndUnit2(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove("10", 20L, person, SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static StratigraphicRelationship relation(RecordingUnit first, RecordingUnit second) {
        StratigraphicRelationship relationship = new StratigraphicRelationship();
        relationship.setUnit1(first);
        relationship.setUnit2(second);
        relationship.setIsAsynchronous(true);
        return relationship;
    }
}
