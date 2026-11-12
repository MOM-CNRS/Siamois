package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.ConceptChangeEvent;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LabelBean implements Serializable {

    private final transient LabelService labelService;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient ConceptLabelRepository conceptLabelRepository;


    // This caching mechanism is a simple in-memory cache to avoid repeated database calls during a user session.
    // However, it would be better to use Spring boot's embedded caching mechanism with a proper cache manager.
    // Can't do it right now because of the JSF managed bean session scope.
    private final Map<String, Map<ConceptDTO, String>> prefLabelCache = new HashMap<>();
    private final Map<Long, ConceptLabelDTO> idToLabelCache = new HashMap<>();
    private final transient ConversionService conversionService;

    @EventListener(ConceptChangeEvent.class)
    public void resetCache() {
        prefLabelCache.clear();
        idToLabelCache.clear();
    }

    private Optional<String> searchMatchingLangAndPrefLabel(String lang, ConceptDTO concept, List<ConceptPrefLabel> existingLabels) {
        if (prefLabelCache.containsKey(lang) && prefLabelCache.get(lang).containsKey(concept)) {
            return Optional.of(prefLabelCache.get(lang).get(concept));
        }

        if (existingLabels.isEmpty())
            existingLabels.addAll(conceptLabelRepository.findAllPrefLabelsByConcept(conversionService.convert(concept,Concept.class)));

        return existingLabels.stream()
                .filter(data -> data.getLangCode().equalsIgnoreCase(lang))
                .findFirst()
                .map(ConceptPrefLabel::getLabel);
    }

    private void addToCache(String lang, ConceptDTO concept, String label) {
        prefLabelCache.computeIfAbsent(lang, k -> new HashMap<>()).put(concept, label);
    }

    /**
     * Find the best matching pref label for the given concept based on the user's preferred language
     *
     * @param concept the concept to find the label for
     * @return the best matching label, or the concept's external ID if no label is found
     */
    @Nullable
    public String findLabelOf(@Nullable ConceptDTO concept) {
        if (concept == null) {
            return null;
        }
        String lang = resolveLangCode();
        UserInfo userInfo = resolveUserInfo();
        if (userInfo == null) {
            return labelService.findLabelOf(concept, lang).getLabel();
        }
        List<ConceptPrefLabel> labels = new ArrayList<>();

        Optional<String> preferred = searchMatchingLangAndPrefLabel(lang, concept, labels);
        if (preferred.isPresent()) {
            addToCache(lang, concept, preferred.get());
            return preferred.get();
        }

        return concept.getExternalId();
    }

    /**
     * Find the best matching pref label for the given concept entity based on the user's preferred language.
     * Converts to {@link ConceptDTO} first so the entity (and its lazy collections) is never touched
     * outside a Hibernate session.
     * Named distinctly from {@link #findLabelOf(ConceptDTO)} (rather than overloaded) because Jakarta EL's
     * method resolution evaluates coercibility against every same-named candidate, including the
     * {@code ConceptDTO} overload, and that coercibility check itself calls {@code Concept.toString()}
     * (via Lombok's generated toString touching the lazy {@code relatedConcepts} collection), throwing
     * {@code LazyInitializationException} outside a Hibernate session before the right overload is ever picked.
     *
     * @param concept the concept entity to find the label for
     * @return the best matching label, or the concept's external ID if no label is found
     */
    @Nullable
    public String findLabelOfConcept(@Nullable Concept concept) {
        if (concept == null) {
            return null;
        }
        return findLabelOf(conversionService.convert(concept, ConceptDTO.class));
    }

    public Optional<ConceptLabelDTO> findById(Long id) {
        if (idToLabelCache.containsKey(id)) {
            return Optional.of(idToLabelCache.get(id));
        }
        Optional<ConceptLabelDTO> label = Optional.ofNullable(conversionService.convert(conceptLabelRepository.findById(id), ConceptLabelDTO.class));
        label.ifPresent(l -> idToLabelCache.put(id, l));
        return label;
    }

    public String getCurrentUserLang() {
        return resolveLangCode();
    }

    /**
     * The current user: the session's when a web request is bound to this thread, else the execution
     * context's (a background task — a bulk action, an import — has no session, only the user bound by
     * OpenApiExecutionContext).
     */
    @Nullable
    private UserInfo resolveUserInfo() {
        UserInfo fromSession = hasBoundRequest() ? sessionSettingsBean.getUserInfo() : null;
        if (fromSession != null) {
            return fromSession;
        }
        return ExecutionContextHolder.get();
    }

    /**
     * Whether a web request is bound to this thread: only then does the session-scoped settings bean exist.
     */
    private static boolean hasBoundRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes;
    }

    private String resolveLangCode() {
        UserInfo info = resolveUserInfo();
        if (info != null && info.getLang() != null && !info.getLang().isBlank()) {
            return info.getLang();
        }
        return Locale.FRENCH.getLanguage();
    }

}
