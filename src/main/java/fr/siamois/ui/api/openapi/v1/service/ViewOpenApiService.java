package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.domain.models.uiview.UiViewKind;
import fr.siamois.domain.models.uiview.UserViewState;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.infrastructure.database.UserViewStateRepository;
import fr.siamois.infrastructure.database.repositories.BookmarkRepository;
import fr.siamois.ui.api.openapi.v1.request.view.DefaultViewRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewStateRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.DefaultViewResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewStateResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Saved list views of an organisation, the default view of a record type, and what each user changed on
 * a view. Every view is visible to every member of its organisation (the caller's organisation has been
 * checked against their JWT scope by the controller); only the owner modifies or deletes a user view, and
 * a default view needs {@code *_EDIT_DEFAULT_VIEWS}.
 */
@Service
@RequiredArgsConstructor
public class ViewOpenApiService {

    private static final int MAX_LIMIT = 200;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String VIEW_KEY_PREFIX_SAVED = "view:";

    private final UiViewRepository uiViewRepository;
    private final UserViewStateRepository userViewStateRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ProfilePermissionService profilePermissionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public ViewListResponse list(UserInfo user, String resourceType, String query, int limit, int offset) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        int page = Math.max(0, offset) / size;
        String pattern = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Page<UiTableView> result = uiViewRepository.search(
                user.getInstitution().getId(), UiViewKind.USER,
                resourceType == null ? "" : resourceType, pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        return new ViewListResponse(
                result.getContent().stream().map(v -> toResource(v, user)).toList(),
                result.getTotalElements(), size, page * size);
    }

    @Transactional(readOnly = true)
    public ViewResource get(UserInfo user, Long id) {
        return toResource(requireView(user, id), user);
    }

    @Transactional
    public ViewResource create(UserInfo user, ViewWriteRequest body) {
        requireWritable(body.resourceType(), body.title(), body.state());
        UiTableView view = new UiTableView();
        view.setKind(UiViewKind.USER);
        view.setOwner(entityManager.getReference(Person.class, user.getUser().getId()));
        view.setInstitution(entityManager.getReference(Institution.class, user.getInstitution().getId()));
        apply(view, body);
        return toResource(uiViewRepository.save(view), user);
    }

    @Transactional
    public ViewResource update(UserInfo user, Long id, ViewWriteRequest body) {
        UiTableView view = requireOwnedUserView(user, id);
        requireWritable(body.resourceType(), body.title(), body.state());
        apply(view, body);
        return toResource(uiViewRepository.save(view), user);
    }

    @Transactional
    public void delete(UserInfo user, Long id) {
        UiTableView view = requireOwnedUserView(user, id);
        userViewStateRepository.deleteForAllUsers(user.getInstitution().getId(), VIEW_KEY_PREFIX_SAVED + id);
        bookmarkRepository.deleteAllOfView(user.getInstitution().getId(), id);
        uiViewRepository.delete(view);
    }

    @Transactional(readOnly = true)
    public DefaultViewResponse getDefault(UserInfo user, String resourceType, Long projectId, Long typeId) {
        requireResourceType(resourceType);
        ViewResource view = findDefault(user, resourceType, projectId, typeId)
                .map(v -> toResource(v, user)).orElse(null);
        return new DefaultViewResponse(view, profilePermissionService.hasDefaultViewEditPermission(user, projectId));
    }

    @Transactional
    public ViewResource saveDefault(UserInfo user, DefaultViewRequest body) {
        requireResourceType(body.resourceType());
        requireState(body.state());
        if (!profilePermissionService.hasDefaultViewEditPermission(user, body.projectId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Droit de modifier les vues par défaut requis");
        }
        UiTableView view = findDefault(user, body.resourceType(), body.projectId(), body.typeId())
                .orElseGet(() -> newDefault(user, body));
        view.setState(body.state());
        return toResource(uiViewRepository.save(view), user);
    }

    @Transactional(readOnly = true)
    public ViewStateResponse getState(UserInfo user, String viewKey) {
        requireViewKey(viewKey);
        return new ViewStateResponse(viewKey, userViewStateRepository
                .findByPersonIdAndInstitutionIdAndViewKey(user.getUser().getId(), user.getInstitution().getId(), viewKey)
                .map(UserViewState::getState).orElse(null));
    }

    @Transactional
    public ViewStateResponse putState(UserInfo user, ViewStateRequest body) {
        requireViewKey(body.viewKey());
        if (body.state() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state est obligatoire");
        }
        Long personId = user.getUser().getId();
        Long institutionId = user.getInstitution().getId();
        UserViewState row = userViewStateRepository
                .findByPersonIdAndInstitutionIdAndViewKey(personId, institutionId, body.viewKey())
                .orElseGet(() -> {
                    UserViewState fresh = new UserViewState();
                    fresh.setPerson(entityManager.getReference(Person.class, personId));
                    fresh.setInstitution(entityManager.getReference(Institution.class, institutionId));
                    fresh.setViewKey(body.viewKey());
                    return fresh;
                });
        row.setState(body.state());
        userViewStateRepository.save(row);
        return new ViewStateResponse(body.viewKey(), body.state());
    }

    @Transactional
    public void deleteState(UserInfo user, String viewKey) {
        requireViewKey(viewKey);
        userViewStateRepository.deleteOne(user.getUser().getId(), user.getInstitution().getId(), viewKey);
    }

    private Optional<UiTableView> findDefault(UserInfo user, String resourceType, Long projectId, Long typeId) {
        return uiViewRepository.findDefault(user.getInstitution().getId(), UiViewKind.DEFAULT, resourceType,
                projectId == null ? 0L : projectId, typeId == null ? 0L : typeId);
    }

    private UiTableView newDefault(UserInfo user, DefaultViewRequest body) {
        UiTableView view = new UiTableView();
        view.setKind(UiViewKind.DEFAULT);
        view.setInstitution(entityManager.getReference(Institution.class, user.getInstitution().getId()));
        view.setResourceType(body.resourceType());
        view.setTitle(body.resourceType());
        if (body.projectId() != null) {
            view.setProject(entityManager.getReference(ActionUnit.class, body.projectId()));
        }
        view.setTypeId(body.typeId());
        return view;
    }

    private void apply(UiTableView view, ViewWriteRequest body) {
        view.setResourceType(body.resourceType());
        view.setTitle(body.title().trim());
        view.setScopeEntityType(body.scopeEntityType());
        TableViewState state = body.state();
        state.setVersion(TableViewState.CURRENT_VERSION);
        view.setState(state);
    }

    private UiTableView requireView(UserInfo user, Long id) {
        return uiViewRepository.findByIdAndInstitutionId(id, user.getInstitution().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vue introuvable"));
    }

    private UiTableView requireOwnedUserView(UserInfo user, Long id) {
        UiTableView view = requireView(user, id);
        if (!canEdit(view, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le propriétaire peut modifier cette vue");
        }
        return view;
    }

    private static boolean canEdit(UiTableView view, UserInfo user) {
        return view.getKind() == UiViewKind.USER && view.getOwner() != null
                && Objects.equals(view.getOwner().getId(), user.getUser().getId());
    }

    private static void requireWritable(String resourceType, String title, TableViewState state) {
        requireResourceType(resourceType);
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title est obligatoire (200 caractères maximum)");
        }
        requireState(state);
    }

    private static void requireResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType est obligatoire");
        }
    }

    private static void requireState(TableViewState state) {
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state est obligatoire");
        }
    }

    private static void requireViewKey(String viewKey) {
        if (viewKey == null || viewKey.isBlank() || viewKey.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewKey est obligatoire (300 caractères maximum)");
        }
    }

    private static ViewResource toResource(UiTableView v, UserInfo user) {
        Person owner = v.getOwner();
        return new ViewResource(
                v.getId(), v.getResourceType(), v.getTitle(), v.getKind().name(), v.getScopeEntityType(),
                v.getProject() == null ? null : v.getProject().getId(), v.getTypeId(),
                v.getState(),
                owner == null ? null : owner.getId(),
                owner == null ? null : (owner.getName() + " " + owner.getLastname()).trim(),
                canEdit(v, user), v.getCreatedAt(), v.getUpdatedAt());
    }
}
