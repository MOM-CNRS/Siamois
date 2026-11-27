package fr.siamois.infrastructure.database.relation;

import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.infrastructure.database.PostgresTestDatabase;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The native queries of the configurable relations, on PostgreSQL: the cycle check along a definition's links, the
 * links in cycles, the pairs two definitions share, which projects read a rule set, and the project lock. Loading
 * the JPA context also checks every entity against the schema the changelog builds. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RelationLinkQueries.class)
class RelationLinkQueriesPostgresTest {

    private static final RelationEndKind UE = RelationEndKind.RECORDING_UNIT;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PostgresTestDatabase.get().getJdbcUrl());
        registry.add("spring.datasource.username", () -> PostgresTestDatabase.get().getUsername());
        registry.add("spring.datasource.password", () -> PostgresTestDatabase.get().getPassword());
    }

    /** Needed by the Envers revision listener, which records who made a change. */
    @MockBean
    private SessionSettingsBean sessionSettingsBean;
    @MockBean
    private ConversionService conversionService;

    @Autowired
    private RelationLinkQueries queries;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long institution;
    private long otherInstitution;
    private long project;
    private long order;
    private long synchrony;
    private long person;

    @BeforeEach
    void createFixtures() {
        person = id("INSERT INTO person (username, password) VALUES ('q-' || gen_random_uuid(), 'x') RETURNING person_id");
        institution = id("INSERT INTO institution (creation_date, identifier, institution_name) "
                + "VALUES (now(), 'q-' || gen_random_uuid(), 'q') RETURNING institution_id");
        otherInstitution = id("INSERT INTO institution (creation_date, identifier, institution_name) "
                + "VALUES (now(), 'q-' || gen_random_uuid(), 'q') RETURNING institution_id");
        project = project(person, institution);
        order = definition();
        synchrony = definition();
    }

    @Test
    void reachesFollowsTheLinksOfTheDefinitionOnly() {
        link(order, 1, 2, false);
        link(order, 2, 3, false);
        link(synchrony, 3, 4, false);

        assertThat(queries.reaches(order, UE, 1, UE, 3, true)).isTrue();
        assertThat(queries.reaches(order, UE, 3, UE, 1, true)).isFalse();
        assertThat(queries.reaches(order, UE, 1, UE, 4, false)).isFalse();
    }

    @Test
    void certainOnlyDoesNotFollowUncertainLinks() {
        link(order, 1, 2, true);
        link(order, 2, 3, false);

        assertThat(queries.reaches(order, UE, 1, UE, 3, true)).isFalse();
        assertThat(queries.reaches(order, UE, 1, UE, 3, false)).isTrue();
    }

    @Test
    void reachesEndsOnLinksThatAlreadyLoop() {
        link(order, 1, 2, false);
        link(order, 2, 1, false);

        assertThat(queries.reaches(order, UE, 1, UE, 9, false)).isFalse();
    }

    @Test
    void linksInCyclesAreOnlyThoseOnACycle() {
        long a = link(order, 1, 2, false);
        long b = link(order, 2, 3, false);
        long c = link(order, 3, 1, false);
        link(order, 3, 4, false);

        assertThat(queries.linksInCycles(order, List.of(project))).containsExactly(a, b, c);
    }

    @Test
    void linksSharingAPairWithAnotherDefinitionInEitherDirection() {
        long shared = link(order, 1, 2, false);
        link(order, 2, 3, false);
        link(synchrony, 2, 1, false);

        assertThat(queries.linksSharingAPairWith(order, synchrony, List.of(project))).containsExactly(shared);
        assertThat(queries.linkedEitherWay(List.of(synchrony), UE, 1, UE, 2)).isTrue();
        assertThat(queries.linkedEitherWay(List.of(synchrony), UE, 2, UE, 3)).isFalse();
    }

    @Test
    void countAtCountsOneSideOrBoth() {
        link(order, 1, 2, false);
        link(order, 3, 1, false);

        assertThat(queries.countAt(order, RelationSide.SOURCE, false, UE, 1)).isEqualTo(1);
        assertThat(queries.countAt(order, RelationSide.TARGET, false, UE, 1)).isEqualTo(1);
        assertThat(queries.countAt(order, RelationSide.SOURCE, true, UE, 1)).isEqualTo(2);
    }

    @Test
    void theProjectsReadingARuleSet() {
        long withOwnRules = project(person, institution);
        long ofOtherInstitution = project(person, otherInstitution);
        link(order, 1, 2, false);
        linkIn(withOwnRules, order, 1, 2);
        linkIn(ofOtherInstitution, order, 1, 2);
        jdbc.update("INSERT INTO relation_rule (fk_relation_definition_id, fk_institution_id, fk_action_unit_id) "
                + "VALUES (?, ?, ?)", order, institution, withOwnRules);
        jdbc.update("INSERT INTO relation_rule (fk_relation_definition_id, fk_institution_id) VALUES (?, ?)",
                order, otherInstitution);

        assertThat(queries.projectsReading(order, null, null)).containsExactly(project);
        assertThat(queries.projectsReading(order, institution, null)).containsExactly(project);
        assertThat(queries.projectsReading(order, otherInstitution, null)).containsExactly(ofOtherInstitution);
        assertThat(queries.projectsReading(order, institution, withOwnRules)).containsExactly(withOwnRules);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void theProjectLockSerializesTwoTransactions() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long lockedProject = 424242L;
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(s -> {
            queries.lockProject(lockedProject);
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(s ->
                queries.lockProject(lockedProject)));

        // still waiting on the first transaction's lock after a while
        assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
    }

    private long link(long definition, long sourceId, long targetId, boolean uncertain) {
        return id("INSERT INTO relation_link (fk_relation_definition_id, fk_action_unit_id, source_kind, source_id, "
                + "target_kind, target_id, uncertain, simple) VALUES (" + definition + ", " + project
                + ", 'RECORDING_UNIT', " + sourceId + ", 'RECORDING_UNIT', " + targetId + ", " + uncertain
                + ", false) RETURNING relation_link_id");
    }

    private void linkIn(long inProject, long definition, long sourceId, long targetId) {
        jdbc.update("INSERT INTO relation_link (fk_relation_definition_id, fk_action_unit_id, source_kind, source_id, "
                + "target_kind, target_id, simple) VALUES (?, ?, 'RECORDING_UNIT', ?, 'RECORDING_UNIT', ?, false)",
                definition, inProject, sourceId, targetId);
    }

    private long project(long person, long ofInstitution) {
        return id("INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, full_identifier, "
                + "identifier, name) VALUES (" + person + ", " + ofInstitution
                + ", 'INCOMPLETE', 'Q-' || gen_random_uuid(), 'Q-' || gen_random_uuid(), 'q') RETURNING action_unit_id");
    }

    private long definition() {
        long source = id("INSERT INTO custom_field (custom_field_id, answer_type, label) "
                + "VALUES (nextval('custom_field_id_seq'), 'RELATION', 's') RETURNING custom_field_id");
        long target = id("INSERT INTO custom_field (custom_field_id, answer_type, label) "
                + "VALUES (nextval('custom_field_id_seq'), 'RELATION', 't') RETURNING custom_field_id");
        return id("INSERT INTO relation_definition (fk_source_field_id, fk_target_field_id, kind) VALUES ("
                + source + ", " + target + ", 'COMPLEX') RETURNING relation_definition_id");
    }

    private long id(String sql) {
        Long id = jdbc.queryForObject(sql, Long.class);
        assertThat(id).isNotNull();
        return id;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
