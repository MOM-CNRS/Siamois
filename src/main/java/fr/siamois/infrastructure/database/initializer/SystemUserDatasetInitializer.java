package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Order(-11)
@RequiredArgsConstructor
@Slf4j
public class SystemUserDatasetInitializer implements DatabaseInitializer {

    /** The identifier of the organisation created on an empty database. */
    public static final String DEFAULT_INSTITUTION_IDENTIFIER = "siamois";

    private final InstitutionRepository institutionRepository;

    @Override
    public void initialize() throws DatabaseDataInitException {
        initializeAdminOrganization();
    }

    void initializeAdminOrganization() {
        if (processExistingInstitution()) return;

        Institution institution = new Institution();
        institution.setName("Organisation par défaut");
        institution.setDescription("DEFAULT");
        institution.setIdentifier(DEFAULT_INSTITUTION_IDENTIFIER);

        institutionRepository.save(institution);

        log.info("Created institution {}", institution.getIdentifier());
    }

    protected boolean processExistingInstitution() {
        Optional<Institution> optInstitution = institutionRepository.findInstitutionByIdentifier(DEFAULT_INSTITUTION_IDENTIFIER);
        return optInstitution.isPresent();
    }

}
