package fr.siamois.domain.services.ark;

import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.ark.NoArkConfigException;
import fr.siamois.domain.models.exceptions.ark.TooManyGenerationsException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.ArkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Service for managing ARK (Archival Resource Key) generation and validation.
 * This service provides methods to generate unique ARK qualifiers,
 */
@Service
public class ArkService {

    private static final String VALID_CHAR_STR = "0123456789bcdfghjklmnpqrstvwxz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_GENERATIONS = 100;

    private final NoidCheckService noidCheckService;
    private final ArkRepository arkRepository;
    private final InstitutionService institutionService;
    private final ConversionService conversionService;

    private ServletUriComponentsBuilder builder;

    /**
     * Autowired constructor for ArkService.
     *
     * @param noidCheckService   the service for NOID (Name of Identifier) checks
     * @param arkRepository      the repository for ARK entities
     * @param institutionService the service for managing institutions
     */
    @Autowired
    public ArkService(NoidCheckService noidCheckService,
                      ArkRepository arkRepository,
                      InstitutionService institutionService, ConversionService conversionService) {
        this.noidCheckService = noidCheckService;
        this.arkRepository = arkRepository;
        this.institutionService = institutionService;
        this.conversionService = conversionService;
    }

    /**
     * Constructor for ArkService with a custom ServletUriComponentsBuilder for unit tests.
     *
     * @param noidCheckService   the service for NOID (Name of Identifier) checks
     * @param arkRepository      the repository for ARK entities
     * @param institutionService the service for managing institutions
     * @param builder            the ServletUriComponentsBuilder to use for building URIs
     */
    public ArkService(NoidCheckService noidCheckService,
                      ArkRepository arkRepository,
                      InstitutionService institutionService, ConversionService conversionService,
                      ServletUriComponentsBuilder builder) {
        this.noidCheckService = noidCheckService;
        this.arkRepository = arkRepository;
        this.institutionService = institutionService;
        this.conversionService = conversionService;
        this.builder = builder;
    }

    private char getRandomChar() {
        return VALID_CHAR_STR.charAt(RANDOM.nextInt(VALID_CHAR_STR.length()));
    }

    /**
     * Generates a random ARK qualifier of the specified length.
     *
     * @param length the length of the ARK qualifier to generate
     * @return a random ARK qualifier string
     */
    private String randomArkQualifier(int length) {
        StringBuilder stringBuilder = new StringBuilder();
        while (stringBuilder.length() < length) {
            stringBuilder.append(getRandomChar());
        }
        String tmp = stringBuilder.toString();
        String controlChar = noidCheckService.calculateCheckDigit(tmp);

        stringBuilder.append("-").append(controlChar);

        return stringBuilder.toString();
    }

    /**
     * Checks if a given ARK qualifier does not exist in the specified institution.
     *
     * @param institution the institution to check against
     * @param qualifier   the ARK qualifier to check
     * @return true if the qualifier does not exist in the institution, false otherwise
     */
    public boolean qualifierNotExistInInstitution(Institution institution, String qualifier) {
        return arkRepository.findByInstitutionAndQualifier(institution.getId(), qualifier).isEmpty();
    }

    /**
     * Generates a new ARK and saves it to the repository.
     *
     * @param settings the institution settings containing ARK configuration
     * @return the generated and saved ARK
     * @throws NoArkConfigException        if the institution does not have ARK configuration
     * @throws TooManyGenerationsException if the maximum number of ARK generations is reached without finding a unique qualifier.
     *                                     At this point, either the institution's ARK configuration is incorrect, or the ARK size is too small.
     */
    public Ark generateAndSave(InstitutionSettings settings) throws NoArkConfigException, TooManyGenerationsException {
        Institution institution = settings.getInstitution();
        if (settings.getArkNaan() == null) {
            throw new NoArkConfigException(institution);
        }

        boolean isValid;
        int interationCount = 0;
        String randomArkQualifier;

        do {
            randomArkQualifier = randomArkQualifier(settings.getArkSize());
            isValid = qualifierNotExistInInstitution(institution, randomArkQualifier);
            interationCount++;
        } while (!isValid && interationCount < MAX_GENERATIONS);

        if (interationCount == MAX_GENERATIONS) {
            throw new TooManyGenerationsException(MAX_GENERATIONS, institution);
        }

        Ark ark = new Ark();
        ark.setCreatingInstitution(institution);
        ark.setQualifier(randomArkQualifier);

        return arkRepository.save(ark);
    }

    /**
     * Finds the URI of the ARK based on the given Ark object.
     *
     * @param ark the Ark object for which to find the URI
     * @return the URI of the ARK as a String
     */
    public String getUriOf(Ark ark) {

        InstitutionSettings settings = institutionService.createOrGetSettingsOf(
                Objects.requireNonNull(conversionService.convert(ark.getCreatingInstitution(), InstitutionDTO.class)));
        if (!settings.hasEnabledArkConfig()) {
            throw new IllegalStateException(String.format("Institution n°%s should have ark settings.", settings.getId()));
        }

        String qualifier = ark.getQualifier();
        if (Boolean.TRUE.equals(settings.getArkIsUppercase()))
            qualifier = qualifier.toUpperCase();

        ServletUriComponentsBuilder currentBuilder;
        if (builder == null) {
            currentBuilder = ServletUriComponentsBuilder.fromCurrentContextPath();
        } else {
            currentBuilder = builder.cloneBuilder();
        }

        return currentBuilder
                .path("/api/ark:/")
                .path(settings.getArkNaan())
                .path("/")
                .path(qualifier)
                .toUriString();
    }


}
