package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.mapper.InstitutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The two permission checks of the relations: writing a link, and configuring a relation. */
@ExtendWith(MockitoExtension.class)
class RelationAuthorizersTest {

    private static final long INSTITUTION = 3L;
    private static final long PROJECT = 7L;

    @Mock private ProfilePermissionService permissions;
    @Mock private InstitutionMapper institutionMapper;
    @Mock private InstitutionRepository institutionRepository;

    private RelationWriteAuthorizer writeAuthorizer;
    private RelationConfigAuthorizer configAuthorizer;

    private final PersonDTO person = person();
    private final Institution institution = institution();
    private final InstitutionDTO institutionDto = new InstitutionDTO();

    @BeforeEach
    void setUp() {
        writeAuthorizer = new RelationWriteAuthorizer(permissions, institutionMapper);
        configAuthorizer = new RelationConfigAuthorizer(permissions, institutionRepository, institutionMapper);
        lenient().when(institutionMapper.convert(institution)).thenReturn(institutionDto);
    }

    // ---- writing a link ----

    @ParameterizedTest
    @EnumSource(value = RelationEndKind.class, names = {"RECORDING_UNIT", "SPECIMEN", "PHASE", "CONTAINER", "DOCUMENT", "STUDY"})
    void aRecordOfAProjectTakesTheProjectPermissionOfItsKind(RelationEndKind kind) {
        when(permissions.hasProjectPermission(any(UserInfo.class), eq(PROJECT), anyString(), anyString(), anyString()))
                .thenReturn(true);

        assertThat(writeAuthorizer.canEdit(endpoint(kind), person)).isTrue();
    }

    @Test
    void aProjectTakesItsWritePermission() {
        when(permissions.hasActionUnitWritePermission(person, institutionDto, PROJECT)).thenReturn(true);

        assertThat(writeAuthorizer.canEdit(endpoint(RelationEndKind.ACTION_UNIT), person)).isTrue();
    }

    @Test
    void aPlaceAnAnonymousPersonOrARecordOfNoInstitutionCannotBeEdited() {
        assertThat(writeAuthorizer.canEdit(endpoint(RelationEndKind.SPATIAL_UNIT), person)).isFalse();
        assertThat(writeAuthorizer.canEdit(endpoint(RelationEndKind.RECORDING_UNIT), new PersonDTO())).isFalse();
        assertThat(writeAuthorizer.canEdit(null, person)).isFalse();
        assertThat(writeAuthorizer.canEdit(endpoint(RelationEndKind.RECORDING_UNIT), null)).isFalse();
        RelationEndpoint orphan = new RelationEndpoint(RelationEndKind.RECORDING_UNIT, 1L, new RecordingUnit(),
                PROJECT, null, null);
        assertThat(writeAuthorizer.canEdit(orphan, person)).isFalse();
    }

    // ---- configuring a relation ----

    @Test
    void definitionsAndThePlatformDefaultBelongToThePlatformAdministrator() {
        when(permissions.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(true);

        assertThat(configAuthorizer.canEditDefinitions(person)).isTrue();
        assertThat(configAuthorizer.canEditRules(RelationScope.platform(), person)).isTrue();
        verifyNoInteractions(institutionRepository);
    }

    @Test
    void anInstitutionsRulesTakeItsSettingsPermission() {
        when(institutionRepository.findById(INSTITUTION)).thenReturn(Optional.of(institution));
        when(permissions.hasOrganizationPermission(person, institutionDto, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(true);

        assertThat(configAuthorizer.canEditRules(RelationScope.institution(INSTITUTION), person)).isTrue();
    }

    @Test
    void aProjectsRulesTakeItsSettingsPermission() {
        when(institutionRepository.findById(INSTITUTION)).thenReturn(Optional.of(institution));
        when(permissions.hasProjectSettingsPermission(any(UserInfo.class), eq(PROJECT))).thenReturn(true);

        assertThat(configAuthorizer.canEditRules(RelationScope.project(INSTITUTION, PROJECT), person)).isTrue();
    }

    @Test
    void anUnknownInstitutionGrantsNothing() {
        when(institutionRepository.findById(INSTITUTION)).thenReturn(Optional.empty());

        assertThat(configAuthorizer.canEditRules(RelationScope.project(INSTITUTION, PROJECT), person)).isFalse();
    }

    private RelationEndpoint endpoint(RelationEndKind kind) {
        RecordingUnit entity = new RecordingUnit();
        entity.setCreatedByInstitution(institution);
        long id = kind == RelationEndKind.ACTION_UNIT ? PROJECT : 1L;
        return new RelationEndpoint(kind, id, entity, PROJECT, null, INSTITUTION);
    }

    private static PersonDTO person() {
        PersonDTO person = new PersonDTO();
        person.setId(5L);
        return person;
    }

    private static Institution institution() {
        Institution institution = new Institution();
        institution.setId(INSTITUTION);
        return institution;
    }
}
