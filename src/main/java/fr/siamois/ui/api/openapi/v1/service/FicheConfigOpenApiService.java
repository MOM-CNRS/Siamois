package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.uiview.FicheConfig;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.infrastructure.database.FicheConfigRepository;
import fr.siamois.ui.api.openapi.v1.request.view.FicheConfigRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.FicheConfigResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tabs of a record type's fiche, configured per project (and per sub-type): what everybody sees on a
 * fiche nobody has arranged yet. Readable by every member of the organisation; writing needs
 * {@code *_EDIT_DEFAULT_VIEWS}. The configuration of a sub-type wins over the one of every sub-type.
 */
@Service
@RequiredArgsConstructor
public class FicheConfigOpenApiService {

    static final int MAX_TABS = 50;
    static final int MAX_TEXT_LENGTH = 200;
    static final int MAX_BYTES = 100_000;

    private final FicheConfigRepository ficheConfigRepository;
    private final ProfilePermissionService profilePermissionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public FicheConfigResponse get(UserInfo user, String resourceType, Long projectId, Long typeId) {
        requireResourceType(resourceType);
        boolean canEdit = profilePermissionService.hasDefaultViewEditPermission(user, projectId);
        Optional<FicheConfig> found = find(user, resourceType, projectId, typeId);
        if (found.isEmpty() && typeId != null) {
            found = find(user, resourceType, projectId, null);
        }
        return found
                .map(c -> new FicheConfigResponse(c.getTabs(), c.getTypeId(), c.getUpdatedAt(), canEdit))
                .orElseGet(() -> new FicheConfigResponse(null, null, null, canEdit));
    }

    @Transactional
    public FicheConfigResponse save(UserInfo user, FicheConfigRequest body) {
        requireResourceType(body.resourceType());
        requireWritePermission(user, body.projectId());
        requireTabs(body.tabs());
        FicheConfig config = find(user, body.resourceType(), body.projectId(), body.typeId())
                .orElseGet(() -> create(user, body));
        config.setTabs(body.tabs());
        config.setUpdatedBy(entityManager.getReference(Person.class, user.getUser().getId()));
        FicheConfig saved = ficheConfigRepository.save(config);
        return new FicheConfigResponse(saved.getTabs(), saved.getTypeId(), saved.getUpdatedAt(), true);
    }

    @Transactional
    public void delete(UserInfo user, String resourceType, Long projectId, Long typeId) {
        requireResourceType(resourceType);
        requireWritePermission(user, projectId);
        find(user, resourceType, projectId, typeId).ifPresent(ficheConfigRepository::delete);
    }

    private Optional<FicheConfig> find(UserInfo user, String resourceType, Long projectId, Long typeId) {
        return ficheConfigRepository.find(user.getInstitution().getId(), resourceType,
                projectId == null ? 0L : projectId, typeId == null ? 0L : typeId);
    }

    private FicheConfig create(UserInfo user, FicheConfigRequest body) {
        FicheConfig config = new FicheConfig();
        config.setInstitution(entityManager.getReference(Institution.class, user.getInstitution().getId()));
        config.setResourceType(body.resourceType());
        config.setTypeId(body.typeId());
        if (body.projectId() != null) {
            config.setProject(entityManager.getReference(ActionUnit.class, body.projectId()));
        }
        return config;
    }

    private void requireWritePermission(UserInfo user, Long projectId) {
        if (!profilePermissionService.hasDefaultViewEditPermission(user, projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Droit de modifier les vues par défaut requis");
        }
    }

    private static void requireResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType est obligatoire");
        }
    }

    /** The shape is the front end's; only what would hurt the server (size, missing or duplicate keys) is checked. */
    private static void requireTabs(Map<String, Object> tabs) {
        if (tabs == null || !(tabs.get("tabs") instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tabs doit contenir la liste « tabs »");
        }
        if (list.size() > MAX_TABS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trop d'onglets (" + MAX_TABS + " au plus)");
        }
        if (tabs.toString().length() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration trop volumineuse");
        }
        Set<String> keys = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> tab) || !(tab.get("key") instanceof String key) || key.isBlank()
                    || key.length() > MAX_TEXT_LENGTH || !keys.add(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chaque onglet a une clé unique (" + MAX_TEXT_LENGTH + " caractères au plus)");
            }
            if (tab.get("label") instanceof String label && label.length() > MAX_TEXT_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le nom d'un onglet fait " + MAX_TEXT_LENGTH + " caractères au plus");
            }
        }
    }
}
