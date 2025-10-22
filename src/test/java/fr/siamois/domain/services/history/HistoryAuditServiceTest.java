package fr.siamois.domain.services.history;

import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.domain.models.history.RevisionWithInfo;
import jakarta.persistence.NoResultException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.AuditQueryCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryAuditServiceTest {

    @Mock
    AuditReader reader;

    @InjectMocks
    HistoryAuditService service;

    @AfterEach
    void tearDown() {
        clearInvocations(reader);
    }

    @Test
    void findAllRevisionForEntity_basic() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(any(), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);

        InfoRevisionEntity e1 = new InfoRevisionEntity();
        InfoRevisionEntity e2 = new InfoRevisionEntity();

        e1.setEpochTimestamp(1212L);
        e2.setEpochTimestamp(3434L);

        Object[] row1 = new Object[] { "entity1-v1", e1, RevisionType.ADD };
        Object[] row2 = new Object[] { "entity1-v2", e2, RevisionType.MOD };
        when(query.getResultList()).thenReturn(Arrays.asList(row1, row2));

        List<?> results = service.findAllRevisionForEntity(String.class, 1L);

        assertEquals(2, results.size());
        verify(reader).createQuery();
        verify(queryCreator).forRevisionsOfEntity(String.class,false, true);
        verify(query).getResultList();
    }

    @Test
    void findAllRevisionForEntity_extreme_emptyList() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(any(), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        List<RevisionWithInfo<String>> results = service.findAllRevisionForEntity(String.class, 42L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(query).getResultList();
    }

    @Test
    void findLastRevisionForEntity_basic() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(any(), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);

        Object[] row = new Object[] { "last-entity", mock(fr.siamois.domain.models.history.InfoRevisionEntity.class), RevisionType.DEL };
        when(query.getSingleResult()).thenReturn(row);

        Object result = service.findLastRevisionForEntity(String.class, 1L);

        assertNotNull(result);
        verify(reader).createQuery();
        verify(query).getSingleResult();
    }

    @Test
    void findLastRevisionForEntity_extreme_noResultThrows() {
        AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
        AuditQuery query = mock(AuditQuery.class);
        when(reader.createQuery()).thenReturn(queryCreator);
        when(queryCreator.forRevisionsOfEntity(any(), anyBoolean(), anyBoolean())).thenReturn(query);
        when(query.add(any())).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new NoResultException());

        assertThrows(NoResultException.class, () -> service.findLastRevisionForEntity(String.class, 999L));
        verify(query).getSingleResult();
    }
}