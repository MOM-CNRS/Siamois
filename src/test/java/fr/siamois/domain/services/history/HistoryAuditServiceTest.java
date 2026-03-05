package fr.siamois.domain.services.history;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.EntityDTORegistry;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import jakarta.persistence.NoResultException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.AuditQueryCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryAuditServiceTest {

    @Mock
    AuditReader reader;
    @Mock
    EntityDTORegistry registry;
    @Mock
    ConversionService conversionService;

    @InjectMocks
    HistoryAuditService service;

    private final Map<Class<? extends AbstractEntityDTO>, Class<? extends TraceableEntity>> dtoToEntityMap = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dtoToEntityMap.put(RecordingUnitDTO.class, RecordingUnit.class);

    }

    @AfterEach
    void tearDown() {
        clearInvocations(reader, registry, conversionService);
    }

    @Test
    @DisplayName("findAllRevisionForEntity: should return all revisions for an entity")
    void findAllRevisionForEntity_basic() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(eq(RecordingUnit.class), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);

        InfoRevisionEntity e1 = new InfoRevisionEntity();
        InfoRevisionEntity e2 = new InfoRevisionEntity();
        e1.setEpochTimestamp(1212L);
        e2.setEpochTimestamp(3434L);

        // Create a mock ActionUnit
        ActionUnit actionUnit = mock(ActionUnit.class);
        when(actionUnit.getSpatialContext()).thenReturn(new HashSet<>());
        when(actionUnit.getMainLocation()).thenReturn(mock(SpatialUnit.class));
        when(registry.getEntityClass(eq(RecordingUnitDTO.class)))
                .thenReturn((Class<TraceableEntity>) dtoToEntityMap.get(RecordingUnitDTO.class));

        // Create RecordingUnit instances with the mock ActionUnit
        RecordingUnit ru1 = new RecordingUnit();
        ru1.setActionUnit(actionUnit);
        RecordingUnit ru2 = new RecordingUnit();
        ru2.setActionUnit(actionUnit);

        Object[] row1 = new Object[] { ru1, e1, RevisionType.ADD };
        Object[] row2 = new Object[] { ru2, e2, RevisionType.MOD };
        when(query.getResultList()).thenReturn(Arrays.asList(row1, row2));

        when(conversionService.convert(any(RecordingUnit.class), eq(RecordingUnitDTO.class)))
                .thenReturn(new RecordingUnitDTO());

        List<RevisionWithInfo<RecordingUnitDTO>> results = service.findAllRevisionForEntity(RecordingUnitDTO.class, 1L);

        assertEquals(2, results.size());
        verify(reader).createQuery();
        verify(queryCreator).forRevisionsOfEntity(RecordingUnit.class, false, true);
        verify(query).getResultList();
    }


    @Test
    @DisplayName("findAllRevisionForEntity: should return empty list if no revisions exist")
    void findAllRevisionForEntity_extreme_emptyList() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(registry.getEntityClass(eq(RecordingUnitDTO.class)))
                .thenReturn((Class<TraceableEntity>) dtoToEntityMap.get(RecordingUnitDTO.class));
        when(queryCreator.forRevisionsOfEntity(eq(RecordingUnit.class), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        List<RevisionWithInfo<RecordingUnitDTO>> results = service.findAllRevisionForEntity(RecordingUnitDTO.class, 42L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(query).getResultList();
    }

    @Test
    @DisplayName("findLastRevisionForEntity: should return the last revision for an entity")
    void findLastRevisionForEntity_basic() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(eq(RecordingUnit.class), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(registry.getEntityClass(eq(RecordingUnitDTO.class)))
                .thenReturn((Class<TraceableEntity>) dtoToEntityMap.get(RecordingUnitDTO.class));
        Object[] row = new Object[] { new RecordingUnit(), mock(InfoRevisionEntity.class), RevisionType.DEL };
        when(query.getSingleResult()).thenReturn(row);

        when(conversionService.convert(any(RecordingUnit.class), eq(RecordingUnitDTO.class)))
                .thenReturn(new RecordingUnitDTO());

        RevisionWithInfo<RecordingUnitDTO> result = service.findLastRevisionForEntity(RecordingUnitDTO.class, 1L);

        assertNotNull(result);
        verify(reader).createQuery();
        verify(query).getSingleResult();
    }

    @Test
    @DisplayName("findLastRevisionForEntity: should return null if no revision exists")
    void findLastRevisionForEntity_extreme_noResultThrows() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(registry.getEntityClass(eq(RecordingUnitDTO.class)))
                .thenReturn((Class<TraceableEntity>) dtoToEntityMap.get(RecordingUnitDTO.class));
        when(queryCreator.forRevisionsOfEntity(eq(RecordingUnit.class), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new NoResultException());

        assertNull(service.findLastRevisionForEntity(RecordingUnitDTO.class, 999L));
    }

    @Test
    @DisplayName("restoreEntity: should copy non-audited fields from base entity")
    void restoreEntity_shouldCopyNotAuditedFieldsFromBaseEntity() {
        TestEntity baseEntity = new TestEntity();
        baseEntity.auditedField = "updated";
        baseEntity.notAuditedField = "updatedNotAudited";

        TestEntity revisionEntity = new TestEntity();
        revisionEntity.auditedField = "revision";
        revisionEntity.notAuditedField = "revisionNotAudited";

        Person person = mock(Person.class);
        Institution institution = mock(Institution.class);

        RevisionWithInfo<TestEntity> revision = new RevisionWithInfo<>(
                revisionEntity,
                new InfoRevisionEntity(1L, 50L, person, institution),
                RevisionType.MOD
        );

        TestEntity result = service.restoreEntity(revision, baseEntity);

        assertEquals("revision", result.auditedField);
        assertEquals("updatedNotAudited", result.notAuditedField);
    }

    @Test
    @DisplayName("findAllContributorsFor: should return all unique contributors for an entity")
    void findAllContributorsFor_basic() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(eq(RecordingUnit.class), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.addProjection(any())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(registry.getEntityClass(eq(RecordingUnitDTO.class)))
                .thenReturn((Class<TraceableEntity>) dtoToEntityMap.get(RecordingUnitDTO.class));
        Person e1 = new Person();
        e1.setUsername("1"); e1.setPassword("1"); e1.setName("1"); e1.setLastname("1");
        Person e2 = new Person();
        e2.setUsername("2"); e2.setPassword("2"); e2.setName("2"); e2.setLastname("2");
        Person e1Duplicate = new Person();
        e1Duplicate.setUsername("1"); e1Duplicate.setPassword("1"); e1Duplicate.setName("1"); e1Duplicate.setLastname("1");

        when(query.getResultList()).thenReturn(Arrays.asList(e1, e2, null, e1Duplicate));

        List<Person> results = service.findAllContributorsFor(RecordingUnitDTO.class, 1L);

        assertEquals(2, results.size());
        assertTrue(results.containsAll(Arrays.asList(e1, e2)));
        verify(reader).createQuery();
        verify(queryCreator).forRevisionsOfEntity(RecordingUnit.class, false, false);
        verify(query).getResultList();
    }

    static class TestEntity {
        public String auditedField = "original";
        @NotAudited
        public String notAuditedField = "originalNotAudited";
    }
}
