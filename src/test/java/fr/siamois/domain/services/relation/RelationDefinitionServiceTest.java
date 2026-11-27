package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.services.relation.RelationDefinitionService.CreateRequest;
import fr.siamois.domain.services.relation.RelationDefinitionService.IdentityUpdate;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.domain.services.relation.RelationRuleService.RuleSpec;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelationDefinitionServiceTest {

    private static final RuleSpec NO_RULES = new RuleSpec(false, false, false, List.of(), Map.of(), List.of());

    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationLinkRepository linkRepository = mock(RelationLinkRepository.class);
    private final CustomFieldRepository fieldRepository = mock(CustomFieldRepository.class);
    private final RelationRuleService ruleService = mock(RelationRuleService.class);
    private final RelationConfigAuthorizer configAuthorizer = mock(RelationConfigAuthorizer.class);
    private final RelationFieldIndex fieldIndex = mock(RelationFieldIndex.class);

    private final RelationDefinitionService service = new RelationDefinitionService(definitionRepository,
            linkRepository, fieldRepository, ruleService, configAuthorizer, fieldIndex);

    private final PersonDTO caller = new PersonDTO();

    @BeforeEach
    void setUp() {
        when(configAuthorizer.canEditDefinition(anyBoolean(), any(), eq(caller))).thenReturn(true);
        when(definitionRepository.findByField(anyLong())).thenReturn(Optional.empty());
        when(definitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        field(-329L, new CustomFieldRelation());
    }

    @Test
    void createsTheOtherSideAndThePlatformDefaultRulesFromAField() {
        RelationDefinition definition = service.create(new CreateRequest(-329L, null, "Antérieur à",
                RelationKind.SIMPLE, false, false, null, NO_RULES), null, caller);

        assertThat(definition.getSourceField().getId()).isEqualTo(-329L);
        assertThat(definition.getTargetField()).isInstanceOf(CustomFieldRelation.class);
        assertThat(definition.getTargetField().getLabel()).isEqualTo("Antérieur à");
        verify(ruleService).saveWithoutPermission(definition, RelationScope.platform(), NO_RULES);
        verify(fieldIndex).evict();
    }

    @Test
    void aSymmetricRelationUsesItsFieldForBothSides() {
        RelationDefinition definition = service.create(new CreateRequest(-329L, null, null,
                RelationKind.SIMPLE, true, false, null, NO_RULES), null, caller);

        assertThat(definition.getTargetField()).isSameAs(definition.getSourceField());
        assertThat(definition.isSymmetric()).isTrue();
    }

    @Test
    void refusesAFieldThatIsNotARelationOrAlreadyASide() {
        field(-1L, new CustomFieldText());
        assertInvalid(() -> service.create(new CreateRequest(-1L, null, "x", RelationKind.SIMPLE, false, false,
                null, NO_RULES), null, caller));

        when(definitionRepository.findByField(-329L)).thenReturn(Optional.of(new RelationDefinition()));
        assertInvalid(() -> service.create(new CreateRequest(-329L, null, "x", RelationKind.SIMPLE, false, false,
                null, NO_RULES), null, caller));
    }

    @Test
    void theQualifierIsASingleVocabularyFieldOfAComplexRelation() {
        field(-2L, new CustomFieldSelectOne());
        field(-3L, new CustomFieldText());

        assertInvalid(() -> service.create(new CreateRequest(-329L, null, "x", RelationKind.SIMPLE, false, false,
                -2L, NO_RULES), null, caller));
        assertInvalid(() -> service.create(new CreateRequest(-329L, null, "x", RelationKind.COMPLEX, false, false,
                -3L, NO_RULES), null, caller));
        RelationDefinition definition = service.create(new CreateRequest(-329L, null, "x", RelationKind.COMPLEX,
                false, false, -2L, NO_RULES), null, caller);
        assertThat(definition.getQualifierField().getId()).isEqualTo(-2L);
    }

    @Test
    void aComplexRelationFieldCanBeItsOwnQualifier() {
        RelationDefinition definition = service.create(new CreateRequest(-329L, null, "x", RelationKind.COMPLEX,
                false, false, -329L, NO_RULES), 7L, caller);

        assertThat(definition.getQualifierField().getId()).isEqualTo(-329L);
    }

    @Test
    void aRelationOfSystemFieldsAsksForThePlatformAdministrator() {
        CustomFieldRelation system = new CustomFieldRelation();
        system.setIsSystemField(true);
        field(-500L, system);
        when(configAuthorizer.canEditDefinition(eq(true), eq(7L), eq(caller))).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateRequest(-500L, null, "x", RelationKind.SIMPLE, false, false,
                null, NO_RULES), 7L, caller)).isInstanceOf(RelationLinkException.class);
    }

    @Test
    void onlyThePlatformAdministratorEditsDefinitions() {
        when(configAuthorizer.canEditDefinition(anyBoolean(), any(), eq(caller))).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateRequest(-329L, null, "x", RelationKind.SIMPLE, false,
                false, null, NO_RULES), null, caller)).isInstanceOfSatisfying(RelationLinkException.class,
                e -> assertThat(e.getReason()).isEqualTo(Reason.FORBIDDEN));
    }

    @Test
    void theIdentityOfADefinitionWithLinksDoesNotChange() {
        RelationDefinition definition = existing(10L);
        when(linkRepository.existsByDefinitionId(10L)).thenReturn(true);

        assertInvalid(() -> service.updateIdentity(10L, new IdentityUpdate(RelationKind.SIMPLE, true, null), null, caller));
        service.updateIdentity(10L, new IdentityUpdate(RelationKind.SIMPLE, false, null), null, caller);
        verify(definitionRepository).save(definition);
    }

    @Test
    void aDefinitionWithLinksCannotBeDeleted() {
        RelationDefinition definition = existing(10L);
        when(linkRepository.existsByDefinitionId(10L)).thenReturn(true);

        assertInvalid(() -> service.delete(10L, null, caller));

        when(linkRepository.existsByDefinitionId(10L)).thenReturn(false);
        service.delete(10L, null, caller);
        verify(ruleService).deleteAllOf(definition);
        verify(definitionRepository).delete(definition);
    }

    private RelationDefinition existing(long id) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        definition.setKind(RelationKind.SIMPLE);
        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));
        return definition;
    }

    private void field(long id, CustomField field) {
        field.setId(id);
        when(fieldRepository.findById(id)).thenReturn(Optional.of(field));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RelationLinkException.class,
                e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_CONFIGURATION));
    }
}
