package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.uiview.HomeAccess;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.infrastructure.database.HomeAccessRepository;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessOrderRequest;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessResource;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The data accesses of an organisation's home page: seen by every member, added and removed by who may edit the
 * organisation's default views ({@code ORGANIZATION_EDIT_DEFAULT_VIEWS}, or the instance counterpart). Adding one
 * creates the view it opens, owned by whoever added it and visible, like any view, to the whole organisation.
 * Removing one leaves its view in place.
 */
@Service
@RequiredArgsConstructor
public class HomeAccessOpenApiService {

    private static final int MAX_LABEL_LENGTH = 200;

    private final HomeAccessRepository homeAccessRepository;
    private final ViewOpenApiService viewService;
    private final ProfilePermissionService profilePermissionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public HomeAccessListResponse list(UserInfo user) {
        List<HomeAccessResource> data = homeAccessRepository.findAllOf(user.getInstitution().getId()).stream()
                .map(HomeAccessOpenApiService::toResource)
                .toList();
        return new HomeAccessListResponse(data, canEdit(user));
    }

    @Transactional
    public HomeAccessResource create(UserInfo user, HomeAccessRequest body) {
        requireEditor(user);
        Long institutionId = user.getInstitution().getId();
        Long viewId;
        String resourceType = body.resourceType();
        String title = body.title();
        TableViewState state = body.state();
        if (body.viewId() != null) {
            ViewResource existing = viewService.get(user, body.viewId());
            if (homeAccessRepository.existsByViewIdAndInstitutionId(existing.id(), institutionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette vue est déjà à la une");
            }
            viewId = existing.id();
            resourceType = existing.resourceType();
            title = existing.title();
            state = existing.state();
        } else {
            viewId = viewService.create(user, new ViewWriteRequest(
                    body.organizationId(), resourceType, title, null, state)).id();
        }
        String label = body.label() == null || body.label().isBlank() ? title : body.label().trim();
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom d'une carte fait " + MAX_LABEL_LENGTH + " caractères au plus");
        }
        HomeAccess access = new HomeAccess();
        access.setInstitution(entityManager.getReference(Institution.class, institutionId));
        access.setView(entityManager.getReference(UiTableView.class, viewId));
        access.setLabel(label);
        access.setPosition(homeAccessRepository.maxPosition(institutionId) + 1);
        HomeAccess saved = homeAccessRepository.save(access);
        return new HomeAccessResource(saved.getId(), viewId, resourceType, title, label,
                state == null ? null : state.getListFilters());
    }

    @Transactional
    public void delete(UserInfo user, Long id) {
        requireEditor(user);
        HomeAccess access = homeAccessRepository.findByIdAndInstitutionId(id, user.getInstitution().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accès introuvable"));
        homeAccessRepository.delete(access);
    }

    /** Reorders the organisation's cards: the cards named take the given order, any other follows them. */
    @Transactional
    public void reorder(UserInfo user, HomeAccessOrderRequest body) {
        requireEditor(user);
        List<Long> ids = body.ids() == null ? List.of() : body.ids();
        List<HomeAccess> all = homeAccessRepository.findAllOf(user.getInstitution().getId());
        int position = 1;
        for (Long id : ids) {
            for (HomeAccess access : all) {
                if (access.getId().equals(id)) {
                    access.setPosition(position++);
                }
            }
        }
        for (HomeAccess access : all) {
            if (!ids.contains(access.getId())) {
                access.setPosition(position++);
            }
        }
        homeAccessRepository.saveAll(all);
    }

    private boolean canEdit(UserInfo user) {
        return profilePermissionService.hasDefaultViewEditPermission(user, null);
    }

    private void requireEditor(UserInfo user) {
        if (!canEdit(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Droit de modifier les vues par défaut requis");
        }
    }

    private static HomeAccessResource toResource(HomeAccess access) {
        UiTableView view = access.getView();
        String label = access.getLabel() == null || access.getLabel().isBlank() ? view.getTitle() : access.getLabel();
        return new HomeAccessResource(access.getId(), view.getId(), view.getResourceType(), view.getTitle(), label,
                view.getState() == null ? null : view.getState().getListFilters());
    }
}
