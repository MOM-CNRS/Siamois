package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.services.relation.RelationFieldSettingsService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationRuleService;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.domain.services.relation.RuleViolation;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;

/**
 * The relation sections of the field drawer of a project's tables &amp; fields screen (docs/modele-relations.md,
 * §3): for a new relation field, what it links and how; for a field that is a side of a relation, its identity (while
 * it has no link) and the rules the project follows. The drawer of a project changes that project's rules only.
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RelationFieldDrawerBean implements Serializable {

    static final String SIMPLE = "simple";
    static final String COMPLEX = "complex";

    /** A record kind offered as an end of a relation (a class rather than a record: the view reads it by getters). */
    @Getter
    @lombok.AllArgsConstructor
    public static class KindOption implements Serializable {
        private final String value;
        private final String label;
    }

    /** A concept offered in a mirror pair picker. */
    @Getter
    @lombok.AllArgsConstructor
    public static class Choice implements Serializable {
        private final String value;
        private final String label;
    }

    /** A mirror pair being edited: a qualifier as the source reads it, and as the other side reads it. */
    @Getter
    @Setter
    public static class PairDraft implements Serializable {
        private String concept;
        private String mirror;
    }

    private final transient RelationFieldSettingsService settingsService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient LabelService labelService;
    private final transient ConceptRepository conceptRepository;
    private final transient ProjectTableFieldSettingsBean settingsBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    // ---- a new relation field ----
    private String draftKind = SIMPLE;
    private boolean draftSymmetric;
    private boolean draftHierarchical;
    private String draftMirrorLabel;
    private List<String> draftOtherKinds = new ArrayList<>();

    // ---- an existing relation, as the project sees it ----
    private boolean open;
    private transient RelationFieldSettingsService.View view;
    private String identityKind;
    private boolean identityHierarchical;
    private boolean noCycle;
    private boolean ownUnique;
    private boolean otherUnique;
    private List<String> otherKinds = new ArrayList<>();
    private List<String> exclusions = new ArrayList<>();
    private List<PairDraft> pairs = new ArrayList<>();
    private transient RelationRuleService.RuleSpec loadedSpec;

    private String errorMessage;
    private List<String> violations = new ArrayList<>();

    public RelationFieldDrawerBean(RelationFieldSettingsService settingsService,
                                   FieldConfigurationService fieldConfigurationService,
                                   LabelService labelService,
                                   ConceptRepository conceptRepository,
                                   ProjectTableFieldSettingsBean settingsBean,
                                   SessionSettingsBean sessionSettingsBean,
                                   LangBean langBean) {
        this.settingsService = settingsService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.labelService = labelService;
        this.conceptRepository = conceptRepository;
        this.settingsBean = settingsBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        close();
        startCreation();
    }

    public void close() {
        open = false;
        view = null;
        loadedSpec = null;
        clearMessages();
    }

    /** A new field: the relation starts simple, between records of any kind, with a mirror to name. */
    public void startCreation() {
        draftKind = SIMPLE;
        draftSymmetric = false;
        draftHierarchical = false;
        draftMirrorLabel = null;
        draftOtherKinds = new ArrayList<>();
        clearMessages();
    }

    @EventListener(FieldOpenedEvent.class)
    public void onFieldOpened(FieldOpenedEvent event) {
        openFor(event.field());
    }

    /** Loads the relation a field is a side of; closes the sections for any other field. */
    public void openFor(TypeFieldFormConfig field) {
        close();
        if (field == null || field.getId() == null || !isRelationType(field.getType())) return;
        settingsService.view(field.getId(), projectId(), sessionSettingsBean.getAuthenticatedUser()).ifPresent(this::load);
    }

    static boolean isRelationType(FieldType type) {
        return type == FieldType.RELATION || type == FieldType.SELECT_MULTIPLE_STRATIGRAPHY;
    }

    private void load(RelationFieldSettingsService.View loaded) {
        view = loaded;
        open = true;
        RelationDefinition definition = loaded.definition();
        identityKind = definition.isSimple() ? SIMPLE : COMPLEX;
        identityHierarchical = definition.isHierarchical();
        RelationRuleSet rules = loaded.rules();
        noCycle = rules.noCycle();
        boolean source = loaded.side() == RelationSide.SOURCE;
        ownUnique = source ? rules.sourceUnique() : rules.targetUnique();
        otherUnique = source ? rules.targetUnique() : rules.sourceUnique();
        otherKinds = new ArrayList<>(loaded.kindsOf(otherSide()).stream().map(Enum::name).toList());
        exclusions = new ArrayList<>(rules.excludedDefinitionIds().stream().map(String::valueOf).toList());
        pairs = new ArrayList<>();
        new TreeMap<>(rules.mirrorByConcept()).forEach((concept, mirror) -> {
            PairDraft pair = new PairDraft();
            pair.setConcept(String.valueOf(concept));
            pair.setMirror(String.valueOf(mirror));
            pairs.add(pair);
        });
        loadedSpec = RelationRuleService.RuleSpec.of(rules);
    }

    // ---- what the view reads ----

    /** The kinds a relation can link: every kind of record of a project; places are not accepted yet. */
    public List<KindOption> getKindOptions() {
        List<KindOption> options = new ArrayList<>();
        for (RelationEndKind kind : RelationEndKind.values()) {
            if (kind != RelationEndKind.SPATIAL_UNIT) {
                options.add(new KindOption(kind.name(), langBean.msg("relation.kind." + kind.name())));
            }
        }
        return options;
    }

    /** Whether the drawer shows the relation's qualifier vocabulary (its "values" section). */
    public boolean isComplex() {
        if (open) return COMPLEX.equals(identityKind);
        return COMPLEX.equals(draftKind);
    }

    public boolean isIdentityEditable() {
        return open && view.identityEditable();
    }

    public boolean isSymmetric() {
        return open ? view.symmetric() : draftSymmetric;
    }

    public long getLinkCount() {
        return open ? view.linkCount() : 0;
    }

    public String getOtherFieldLabel() {
        return open ? labelOf(view.otherField()) : null;
    }

    /** The identity, read only: its nature, and whether it is hierarchical. */
    public String getIdentityLabel() {
        String kind = langBean.msg("relation.kind." + identityKind);
        return identityHierarchical ? kind + " · " + langBean.msg("relation.hierarchical") : kind;
    }

    /** Why the identity cannot change: it has links, or it is a system relation. */
    public String getIdentityHint() {
        if (getLinkCount() > 0) return langBean.msg("relation.identityFrozen", getLinkCount());
        return langBean.msg("relation.identitySystem");
    }

    public boolean isOwnRules() {
        return open && view.ownRules();
    }

    /** "Règles propres au projet", or where the rules it follows come from. */
    public String getRulesOriginLabel() {
        if (!open) return null;
        if (view.ownRules()) return langBean.msg("relation.rules.own");
        return langBean.msg("relation.rules.inherited." + view.rulesOrigin());
    }

    /** The other relations, which this one can be declared exclusive with. */
    public List<KindOption> getExclusionOptions() {
        if (!open) return List.of();
        return settingsService.otherDefinitions(view.definition().getId()).stream()
                .map(d -> new KindOption(String.valueOf(d.getId()), labelOf(d)))
                .toList();
    }

    /** The qualifiers the relation's own field offers, and those its mirror field offers. */
    public List<Choice> getOwnQualifiers() {
        if (!open) return List.of();
        CustomField own = view.side() == RelationSide.SOURCE ? view.definition().getSourceField() : view.definition().getTargetField();
        return withPaired(choicesOf(own), pairs.stream().map(PairDraft::getConcept).toList());
    }

    public List<Choice> getMirrorQualifiers() {
        if (!open) return List.of();
        return withPaired(choicesOf(view.otherField()), pairs.stream().map(PairDraft::getMirror).toList());
    }

    /**
     * The choices, plus the concepts already paired that the field no longer offers (another vocabulary, a concept
     * disabled since): the pair is shown as it is, and saving keeps it unless it is removed.
     */
    private List<Choice> withPaired(List<Choice> choices, List<String> paired) {
        Set<String> offered = new HashSet<>();
        choices.forEach(c -> offered.add(c.getValue()));
        List<Choice> all = new ArrayList<>(choices);
        for (String id : paired) {
            if (id == null || id.isBlank() || !offered.add(id)) continue;
            String label = conceptRepository.findById(Long.valueOf(id))
                    .map(concept -> labelService.findLabelOf(concept, langBean.getLanguageCode()).getLabel())
                    .orElse(id);
            all.add(new Choice(id, label));
        }
        return all;
    }

    // ---- editing ----

    public void addPair() {
        pairs.add(new PairDraft());
    }

    public void removePair(PairDraft pair) {
        pairs.remove(pair);
    }

    /**
     * Creates the relation of a field the drawer is creating: the field itself, the field of the other side and the
     * definition, at once. A refusal is reported by the exception, and nothing is created.
     */
    public void createField(ConfigurableTable table, String typeName, String name, String description) {
        RelationKind kind = COMPLEX.equals(draftKind) ? RelationKind.COMPLEX : RelationKind.SIMPLE;
        List<RelationEndKind> kinds = draftOtherKinds.stream().map(RelationEndKind::valueOf).toList();
        String mirror = draftSymmetric ? null : defaultMirrorLabel(name);
        settingsService.createRelationField(projectId(), table, typeName, name, description,
                new RelationFieldSettingsService.Draft(kind, draftSymmetric, draftSymmetric ? false : draftHierarchical,
                        mirror, kinds),
                sessionSettingsBean.getAuthenticatedUser());
    }

    /** The mirror's label: the one typed, else the field's own followed by "(miroir)". */
    String defaultMirrorLabel(String name) {
        if (draftMirrorLabel != null && !draftMirrorLabel.isBlank()) return draftMirrorLabel.trim();
        return name + " " + langBean.msg("relation.mirror.suffix");
    }

    /**
     * The field's Save, for an existing relation: its identity when it changed and can change, then the project's
     * rules when they changed. Unchanged rules are not saved, so that a project does not stop inheriting them just
     * by saving the field.
     *
     * @return false when a change was refused: the reason is in the drawer
     */
    public boolean saveIfOpen() {
        if (!open) return true;
        clearMessages();
        try {
            RelationDefinition definition = view.definition();
            RelationKind kind = COMPLEX.equals(identityKind) ? RelationKind.COMPLEX : RelationKind.SIMPLE;
            if (view.identityEditable() && (kind != definition.getKind() || identityHierarchical != definition.isHierarchical())) {
                settingsService.saveIdentity(definition.getId(), kind, identityHierarchical, projectId(),
                        sessionSettingsBean.getAuthenticatedUser());
            }
            RelationRuleService.RuleSpec spec = draftSpec();
            if (!sameRules(spec, loadedSpec)) {
                settingsService.saveRules(definition.getId(), projectId(), spec, sessionSettingsBean.getAuthenticatedUser());
            }
            reload();
            return true;
        } catch (RelationLinkException e) {
            report(e);
            return false;
        }
    }

    /** Drops the project's own rules: it follows the rules it inherited again. */
    public void resetRules() {
        if (!open) return;
        clearMessages();
        try {
            settingsService.resetRules(view.definition().getId(), projectId(), sessionSettingsBean.getAuthenticatedUser());
            reload();
        } catch (RelationLinkException e) {
            report(e);
        }
    }

    /** The rules as the drawer shows them, as the rule service takes them. */
    RelationRuleService.RuleSpec draftSpec() {
        boolean source = view.side() == RelationSide.SOURCE;
        boolean sourceUnique = source ? ownUnique : otherUnique;
        boolean targetUnique = source ? otherUnique : ownUnique;
        if (view.symmetric()) {
            sourceUnique = ownUnique;
            targetUnique = false;
        }
        List<RelationRuleSet.AcceptedKind> kinds = new ArrayList<>();
        // the field's own side keeps what it accepted; the drawer edits the other side's
        for (RelationRuleSet.AcceptedKind kept : view.rules().kindsOf(view.side())) {
            if (!view.symmetric()) kinds.add(kept);
        }
        RelationSide other = otherSide();
        for (String kind : otherKinds) {
            Long subtype = view.rules().kindsOf(other).stream()
                    .filter(k -> k.kind().name().equals(kind)).map(RelationRuleSet.AcceptedKind::subtypeId)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            kinds.add(new RelationRuleSet.AcceptedKind(other, RelationEndKind.valueOf(kind), subtype));
        }
        Map<Long, Long> mirrors = new LinkedHashMap<>();
        for (PairDraft pair : pairs) {
            if (pair.getConcept() == null || pair.getConcept().isBlank() || pair.getMirror() == null || pair.getMirror().isBlank()) continue;
            Long concept = Long.valueOf(pair.getConcept());
            Long mirror = Long.valueOf(pair.getMirror());
            // seen from the target, the pair is typed the other way round: the stored concept is the source's
            if (view.side() == RelationSide.SOURCE || view.symmetric()) mirrors.put(concept, mirror);
            else mirrors.put(mirror, concept);
        }
        List<Long> excluded = exclusions.stream().map(Long::valueOf).sorted().toList();
        return new RelationRuleService.RuleSpec(noCycle || view.definition().isHierarchical(), sourceUnique, targetUnique,
                kinds, mirrors, excluded);
    }

    /** Whether two rule sets say the same, whatever the order of their lists. */
    static boolean sameRules(RelationRuleService.RuleSpec a, RelationRuleService.RuleSpec b) {
        if (a == null || b == null) return a == b;
        return a.noCycle() == b.noCycle() && a.sourceUnique() == b.sourceUnique() && a.targetUnique() == b.targetUnique()
                && new HashSet<>(a.kinds()).equals(new HashSet<>(b.kinds()))
                && a.mirrorByConcept().equals(b.mirrorByConcept())
                && new HashSet<>(a.excludedDefinitionIds()).equals(new HashSet<>(b.excludedDefinitionIds()));
    }

    private RelationSide otherSide() {
        if (view.symmetric()) return RelationSide.SOURCE;
        return view.side() == RelationSide.SOURCE ? RelationSide.TARGET : RelationSide.SOURCE;
    }

    private void reload() {
        RelationFieldSettingsService.View current = view;
        long fieldId = current.side() == RelationSide.SOURCE ? current.definition().getSourceField().getId()
                : current.definition().getTargetField().getId();
        close();
        settingsService.view(fieldId, projectId(), sessionSettingsBean.getAuthenticatedUser()).ifPresent(this::load);
    }

    /** How many links of a broken rule the drawer names; the others are counted. */
    static final int LINKS_SHOWN = 20;

    private void report(RelationLinkException e) {
        violations = new ArrayList<>();
        for (RuleViolation violation : e.getViolations()) {
            violations.add(violation.message() + linksOf(violation.linkIds()));
        }
        errorMessage = violations.isEmpty() ? e.getMessage() : langBean.msg("relation.rules.refused");
    }

    private String linksOf(List<Long> linkIds) {
        if (linkIds.isEmpty()) return "";
        String shown = String.join(", ", linkIds.stream().limit(LINKS_SHOWN).map(String::valueOf).toList());
        String more = linkIds.size() > LINKS_SHOWN ? " " + langBean.msg("relation.rules.moreLinks", linkIds.size() - LINKS_SHOWN) : "";
        return " (" + langBean.msg("relation.rules.links") + " " + shown + more + ")";
    }

    private void clearMessages() {
        errorMessage = null;
        violations = new ArrayList<>();
    }

    private List<Choice> choicesOf(CustomField field) {
        if (!(field instanceof CustomFieldConcept conceptField)) return List.of();
        try {
            Map<String, String> byId = new LinkedHashMap<>();
            for (ConceptAutocompleteDTO dto : fieldConfigurationService.fetchAutocomplete(conceptField, "", projectId())) {
                byId.putIfAbsent(String.valueOf(dto.getConceptLabelToDisplay().getConcept().getId()),
                        dto.getConceptLabelToDisplay().getLabel());
            }
            return byId.entrySet().stream().map(e -> new Choice(e.getKey(), e.getValue())).toList();
        } catch (NoConfigForFieldException e) {
            log.debug("No vocabulary configured for field {}", field.getId(), e);
            return List.of();
        }
    }

    private String labelOf(RelationDefinition definition) {
        String source = labelOf(definition.getSourceField());
        if (definition.isSymmetric()) return source;
        return source + " / " + labelOf(definition.getTargetField());
    }

    /** A system field's label is a message key; a project's field is labelled as typed. */
    private String labelOf(CustomField field) {
        if (field == null) return null;
        return Boolean.TRUE.equals(field.getIsSystemField()) ? langBean.msg(field.getLabel()) : field.getLabel();
    }

    private long projectId() {
        return settingsBean.getProject().getId();
    }
}
