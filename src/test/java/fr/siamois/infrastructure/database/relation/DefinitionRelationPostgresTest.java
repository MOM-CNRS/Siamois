package fr.siamois.infrastructure.database.relation;

import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.infrastructure.database.PostgresTestDatabase;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository.Row;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The values of a configurable relation field, read on PostgreSQL through {@link RelationFieldRepository}: each
 * side reads the same links, the qualifier seen from the target is its mirror in the rules of the link's project,
 * and a symmetric relation reads from both ends. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RelationFieldRepository.class)
class DefinitionRelationPostgresTest {

    private static final RelationEndKind UE = RelationEndKind.RECORDING_UNIT;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PostgresTestDatabase.get().getJdbcUrl());
        registry.add("spring.datasource.username", () -> PostgresTestDatabase.get().getUsername());
        registry.add("spring.datasource.password", () -> PostgresTestDatabase.get().getPassword());
    }

    @MockBean
    private SessionSettingsBean sessionSettingsBean;
    @MockBean
    private ConversionService conversionService;

    @Autowired
    private RelationFieldRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private long a;
    private long b;
    private long c;
    private long d;
    private long order;
    private long synchrony;
    private long recouvre;
    private long estRecouverte;
    private long estRecouverteIci;

    @BeforeEach
    void createFixtures() {
        long person = id("INSERT INTO person (username, password) VALUES ('d-' || gen_random_uuid(), 'x') RETURNING person_id");
        long institution = id("INSERT INTO institution (creation_date, identifier, institution_name) "
                + "VALUES (now(), 'd-' || gen_random_uuid(), 'd') RETURNING institution_id");
        long projectP = project(person, institution);
        long projectQ = project(person, institution);
        a = unit(person, institution, projectP, "UE A");
        b = unit(person, institution, projectP, "UE B");
        c = unit(person, institution, projectQ, "UE C");
        d = unit(person, institution, projectQ, "UE D");
        long type = id("INSERT INTO vocabulary_type (label) VALUES ('t') RETURNING vocabulary_type_id");
        long instance = id("INSERT INTO vocabulary_instance (base_uri) VALUES ('https://example.test') RETURNING vocabulary_instance_id");
        long vocabulary = id("INSERT INTO vocabulary (fk_type_id, fk_vocabulary_instance_id, external_id) VALUES ("
                + type + ", " + instance + ", 'd-' || gen_random_uuid()) RETURNING vocabulary_id");
        recouvre = concept(vocabulary);
        estRecouverte = concept(vocabulary);
        estRecouverteIci = concept(vocabulary);
        order = definition("COMPLEX", false);
        synchrony = definition("SIMPLE", true);

        long platform = id("INSERT INTO relation_rule (fk_relation_definition_id) VALUES (" + order
                + ") RETURNING relation_rule_id");
        jdbc.update("INSERT INTO relation_rule_mirror_concept (fk_relation_rule_id, fk_concept_id, fk_mirror_concept_id) "
                + "VALUES (?, ?, ?)", platform, recouvre, estRecouverte);
        long ofQ = id("INSERT INTO relation_rule (fk_relation_definition_id, fk_institution_id, fk_action_unit_id) VALUES ("
                + order + ", " + institution + ", " + projectQ + ") RETURNING relation_rule_id");
        jdbc.update("INSERT INTO relation_rule_mirror_concept (fk_relation_rule_id, fk_concept_id, fk_mirror_concept_id) "
                + "VALUES (?, ?, ?)", ofQ, recouvre, estRecouverteIci);

        link(order, projectP, a, b, recouvre, false);
        link(order, projectQ, c, d, recouvre, true);
        link(synchrony, projectP, Math.min(a, b), Math.max(a, b), null, false);
    }

    @Test
    void theSourceFieldReadsTheStoredQualifier() {
        DefinitionRelation posterior = new DefinitionRelation(order, RelationSide.SOURCE, false, true, UE);

        Row row = only(repository.previews(posterior, List.of(a), 5), a);

        assertThat(row.targetId()).isEqualTo(b);
        assertThat(row.label()).isEqualTo("UE B");
        assertThat(row.role()).isEqualTo("recording-units");
        assertThat(row.conceptId()).isEqualTo(recouvre);
        assertThat(row.uncertain()).isFalse();
    }

    @Test
    void theMirrorFieldReadsTheMirrorOfTheRulesOfTheLinksProject() {
        DefinitionRelation anterior = new DefinitionRelation(order, RelationSide.TARGET, false, true, UE);

        Map<Long, RelationFieldRepository.Preview> previews = repository.previews(anterior, List.of(b, d), 5);

        assertThat(only(previews, b).targetId()).isEqualTo(a);
        assertThat(only(previews, b).conceptId()).isEqualTo(estRecouverte);
        assertThat(only(previews, d).conceptId()).isEqualTo(estRecouverteIci);
        assertThat(only(previews, d).uncertain()).isTrue();
    }

    @Test
    void aSymmetricRelationReadsFromBothEnds() {
        DefinitionRelation synchronous = new DefinitionRelation(synchrony, RelationSide.SOURCE, true, false, UE);

        Map<Long, RelationFieldRepository.Preview> previews = repository.previews(synchronous, List.of(a, b), 5);

        assertThat(only(previews, a).targetId()).isEqualTo(b);
        assertThat(only(previews, b).targetId()).isEqualTo(a);
        assertThat(only(previews, a).conceptId()).isNull();
    }

    @Test
    void aPageAndItsCountFilterOnTheLabel() {
        DefinitionRelation posterior = new DefinitionRelation(order, RelationSide.SOURCE, false, true, UE);

        assertThat(repository.page(posterior, a, 0, 10, "ue b", true)).extracting(Row::targetId).containsExactly(b);
        assertThat(repository.count(posterior, a, "zzz")).isZero();
    }

    private static Row only(Map<Long, RelationFieldRepository.Preview> previews, long owner) {
        RelationFieldRepository.Preview preview = previews.get(owner);
        assertThat(preview).isNotNull();
        assertThat(preview.total()).isEqualTo(1);
        return preview.rows().get(0);
    }

    private void link(long definition, long project, long source, long target, Long qualifier, boolean uncertain) {
        jdbc.update("INSERT INTO relation_link (fk_relation_definition_id, fk_action_unit_id, source_kind, source_id, "
                        + "target_kind, target_id, fk_qualifier_concept_id, uncertain, simple) "
                        + "VALUES (?, ?, 'RECORDING_UNIT', ?, 'RECORDING_UNIT', ?, ?, ?, ?)",
                definition, project, source, target, qualifier, uncertain, definition == synchrony);
    }

    private long definition(String kind, boolean symmetric) {
        long source = field();
        long target = symmetric ? source : field();
        return id("INSERT INTO relation_definition (fk_source_field_id, fk_target_field_id, kind, is_symmetric) VALUES ("
                + source + ", " + target + ", '" + kind + "', " + symmetric + ") RETURNING relation_definition_id");
    }

    private long field() {
        return id("INSERT INTO custom_field (custom_field_id, answer_type, label) "
                + "VALUES (nextval('custom_field_id_seq'), 'RELATION', 'f') RETURNING custom_field_id");
    }

    private long concept(long vocabulary) {
        return id("INSERT INTO concept (fk_vocabulary_id) VALUES (" + vocabulary + ") RETURNING concept_id");
    }

    private long project(long person, long institution) {
        return id("INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, full_identifier, "
                + "identifier, name) VALUES (" + person + ", " + institution
                + ", 'INCOMPLETE', 'D-' || gen_random_uuid(), 'D-' || gen_random_uuid(), 'd') RETURNING action_unit_id");
    }

    private long unit(long person, long institution, long project, String label) {
        return id("INSERT INTO recording_unit (sync_revision, fk_created_by, fk_institution_id, fk_action_unit_id, "
                + "fk_author_id, validation_status, full_identifier) VALUES (0, " + person + ", " + institution + ", "
                + project + ", " + person + ", 'INCOMPLETE', '" + label + "') RETURNING recording_unit_id");
    }

    private long id(String sql) {
        Long id = jdbc.queryForObject(sql, Long.class);
        assertThat(id).isNotNull();
        return id;
    }
}
