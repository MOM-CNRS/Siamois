package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationRuleSeverity;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkAttributesService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.domain.services.relation.RuleViolation;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.ApiError;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationEndRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkUpdateRequest;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationLinkOpenApiServiceTest {

    @Mock RelationLinkService relationLinkService;
    @Mock RelationLinkAttributesService attributesService;

    private RelationLinkOpenApiService service;
    private final PersonDTO person = new PersonDTO();
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        service = new RelationLinkOpenApiService(relationLinkService, attributesService);
        caller = new ProjectApiCaller(person, Set.of(1L), List.of());
    }

    private static RelationEndRequest end(String type, long id) {
        RelationEndRequest end = new RelationEndRequest();
        end.setResourceType(type);
        end.setResourceId(id);
        return end;
    }

    private static RelationLinkCreateRequest body() {
        RelationLinkCreateRequest body = new RelationLinkCreateRequest();
        body.setRelation("SIAMOIS.RELATION.ORDER");
        body.setSource(end("recording-units", 1));
        body.setTarget(end("recording-units", 2));
        return body;
    }

    private RelationLink link() {
        RelationDefinition definition = new RelationDefinition();
        definition.setCode("SIAMOIS.RELATION.ORDER");
        RelationLink link = new RelationLink();
        link.setId(7L);
        link.setDefinition(definition);
        RecordingUnit source = new RecordingUnit();
        source.setId(1L);
        RecordingUnit target = new RecordingUnit();
        target.setId(2L);
        link.attachSource(source);
        link.attachTarget(target);
        Concept cuts = new Concept();
        cuts.setId(20L);
        link.setSourceType(cuts);
        return link;
    }

    @Test
    void create_translatesTheRequestAndShapesTheResponse() {
        RelationLinkCreateRequest body = body();
        body.setSourceType(20L);
        body.setEditedFrom("TARGET");
        body.setAttributes(Map.of("-900", true));
        RelationLink link = link();
        when(relationLinkService.create(any(), eq(person), eq(Set.of(1L))))
                .thenReturn(new RelationLinkService.Result(link,
                        List.of(new RuleViolation(RelationRuleKind.PAIR_CARDINALITY, RelationRuleSeverity.WARNING, "déjà un lien"))));
        when(attributesService.read(link)).thenReturn(Map.of(-900L, true));

        RelationLinkResponse response = service.create(body, caller);

        ArgumentCaptor<RelationLinkService.CreateRequest> sent = ArgumentCaptor.forClass(RelationLinkService.CreateRequest.class);
        verify(relationLinkService).create(sent.capture(), eq(person), eq(Set.of(1L)));
        assertThat(sent.getValue().sourceKind()).isEqualTo(RelationEntityKind.RECORDING_UNIT);
        assertThat(sent.getValue().targetId()).isEqualTo(2L);
        assertThat(sent.getValue().editedFrom()).isEqualTo(RelationSide.TARGET);
        assertThat(sent.getValue().attributes()).containsEntry(-900L, true);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.relation()).isEqualTo("SIAMOIS.RELATION.ORDER");
        assertThat(response.source().resourceType()).isEqualTo("recording-units");
        assertThat(response.source().resourceId()).isEqualTo("1");
        assertThat(response.sourceType()).isEqualTo(20L);
        assertThat(response.targetType()).isNull();
        assertThat(response.attributes()).containsEntry("-900", true);
        assertThat(response.warnings()).extracting(RelationLinkResponse.Warning::rule).containsExactly("PAIR_CARDINALITY");
    }

    @Test
    void create_withoutWarningsHasNoWarningList() {
        RelationLink link = link();
        when(relationLinkService.create(any(), any(), any())).thenReturn(new RelationLinkService.Result(link, List.of()));
        when(attributesService.read(link)).thenReturn(Map.of());

        assertThat(service.create(body(), caller).warnings()).isNull();
    }

    @Test
    void create_aMissingPartIsABadRequest() {
        RelationLinkCreateRequest body = body();
        body.setTarget(null);

        assertThatThrownBy(() -> service.create(body, caller)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(null, caller)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_anUnknownResourceTypeIsABadRequest() {
        RelationLinkCreateRequest body = body();
        body.setSource(end("widgets", 1));

        assertThatThrownBy(() -> service.create(body, caller)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_aBadEditedFromOrAttributeKeyIsRefused() {
        RelationLinkCreateRequest side = body();
        side.setEditedFrom("middle");
        assertThatThrownBy(() -> service.create(side, caller)).isInstanceOf(ResponseStatusException.class);

        RelationLinkCreateRequest key = body();
        key.setAttributes(Map.of("not-a-number", true));
        assertThatThrownBy(() -> service.create(key, caller)).isInstanceOf(RelationLinkException.class);
    }

    @Test
    void update_passesTheTypeChange() {
        RelationLink link = link();
        when(relationLinkService.update(eq(7L), any(), eq(person), any())).thenReturn(new RelationLinkService.Result(link, List.of()));
        when(attributesService.read(link)).thenReturn(Map.of());
        RelationLinkUpdateRequest body = new RelationLinkUpdateRequest();
        body.setChangeTypes(true);
        body.setSourceType(20L);

        service.update(7L, body, caller);

        ArgumentCaptor<RelationLinkService.UpdateRequest> sent = ArgumentCaptor.forClass(RelationLinkService.UpdateRequest.class);
        verify(relationLinkService).update(eq(7L), sent.capture(), eq(person), any());
        assertThat(sent.getValue().changeTypes()).isTrue();
        assertThat(sent.getValue().sourceTypeId()).isEqualTo(20L);
        assertThat(sent.getValue().editedFrom()).isEqualTo(RelationSide.SOURCE);
    }

    @Test
    void delete_defaultsToTheSourceSide() {
        service.delete(7L, null, caller);

        verify(relationLinkService).delete(7L, RelationSide.SOURCE, person, Set.of(1L));
    }

    @Test
    void theExceptionHandlerMapsEachReasonToItsStatus() {
        RestExceptionHandler handler = new RestExceptionHandler();
        assertThat(status(handler, RelationLinkException.Reason.ENDPOINT_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(handler, RelationLinkException.Reason.MIRROR_READ_ONLY)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(handler, RelationLinkException.Reason.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(handler, RelationLinkException.Reason.DUPLICATE)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(handler, RelationLinkException.Reason.TYPE_NOT_ALLOWED)).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiError> rule = handler.relationLink(new RelationLinkException(RelationLinkException.Reason.RULE_VIOLATION,
                "cycle", List.of(new RuleViolation(RelationRuleKind.NO_CYCLE, RelationRuleSeverity.BLOCKING, "Ce lien créerait un cycle"))));
        assertThat(rule.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rule.getBody().error()).isEqualTo("conflict");
        assertThat(rule.getBody().details()).extracting(ApiError.Detail::field).containsExactly("NO_CYCLE");
    }

    private static HttpStatus status(RestExceptionHandler handler, RelationLinkException.Reason reason) {
        return HttpStatus.valueOf(handler.relationLink(new RelationLinkException(reason, "x")).getStatusCode().value());
    }
}
