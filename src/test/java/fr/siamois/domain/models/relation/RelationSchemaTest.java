package fr.siamois.domain.models.relation;

import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No database: the project's whole PostgreSQL schema is generated as a script (as {@code ddl-auto: update}
 * would create it on a fresh base), so the relation tables' DDL can be checked, which Hibernate only reveals
 * at start-up otherwise.
 */
class RelationSchemaTest {

    private static String ddl;

    @BeforeAll
    static void generateSchema() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        // its listener is a Spring bean (no default constructor); Envers falls back to its own revision entity
        scanner.addExcludeFilter(new AnnotationTypeFilter(RevisionEntity.class));
        File script = File.createTempFile("siamois-schema", ".sql");
        script.deleteOnExit();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.integration.envers.enabled", "false")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script.getAbsolutePath())
                .build();
        MetadataSources sources = new MetadataSources(registry);
        for (BeanDefinition def : scanner.findCandidateComponents("fr.siamois")) {
            sources.addAnnotatedClass(Class.forName(def.getBeanClassName()));
        }
        Metadata metadata = sources.buildMetadata();
        metadata.buildSessionFactory().close();
        ddl = Files.readString(script.toPath()).toLowerCase(Locale.ROOT);
        // kept for a look at the generated DDL (target/ is not versioned)
        Files.writeString(Path.of("target", "relation-schema.sql"), ddl);
    }

    private static List<String> statementsOn(String table) {
        return ddl.lines().filter(l -> l.contains(" " + table + " ") || l.contains(" " + table + "(")).toList();
    }

    @Test
    void relationLink_hasOneEndPerSideCheckAndCascadingFks() {
        String create = statementsOn("relation_link").stream()
                .filter(l -> l.startsWith("create table")).findFirst().orElseThrow();
        assertThat(create).contains("ck_relation_link_one_end_per_side");
        assertThat(create).contains("fk_source_recording_unit_id", "fk_target_person_id", "fk_relation_definition_id");
        assertThat(create).doesNotContain("fk_source_person_id");

        List<String> fks = statementsOn("relation_link").stream().filter(l -> l.contains("foreign key")).toList();
        assertThat(fks).isNotEmpty().allMatch(l -> l.contains("on delete cascade") || l.contains("fk_source_type_concept_id")
                || l.contains("fk_target_type_concept_id"));
    }

    @Test
    void formConfigAnswer_isUniquePerRelationLinkAndFormConfig() {
        assertThat(ddl).contains("uk_form_config_answer_relation_link");
        assertThat(ddl).contains("fk_relation_link_id");
    }

    @Test
    void definitionTables_exist() {
        assertThat(ddl).contains("create table relation_definition ", "create table relation_definition_kind ",
                "create table relation_type_mapping ", "create table relation_rule ", "create table relation_rule_definition ");
        assertThat(ddl).contains("uk_relation_definition_code", "uk_relation_definition_kind", "uk_relation_type_mapping");
    }
}
