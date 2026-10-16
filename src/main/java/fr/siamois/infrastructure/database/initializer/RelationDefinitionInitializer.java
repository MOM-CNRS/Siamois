package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.initializer.seeder.RelationDefinitionSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/** Seeds the platform's relation definitions (stratigraphic order and synchronism). */
@Slf4j
@Service
@Order(1) // after VocabularyTypeInitializer (-1): the local vocabulary needs the "Thesaurus" type
@RequiredArgsConstructor
public class RelationDefinitionInitializer implements DatabaseInitializer {

    private final RelationDefinitionSeeder relationDefinitionSeeder;

    @Override
    public void initialize() throws DatabaseDataInitException {
        relationDefinitionSeeder.seed();
        log.info("Platform relation definitions initialized");
    }
}
