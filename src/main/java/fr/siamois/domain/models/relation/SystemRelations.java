package fr.siamois.domain.models.relation;

/**
 * The relation definitions the platform provides, and the concepts that name their sides and types.
 * <p>
 * The concepts live in a local, read-only vocabulary created at start-up (no external thesaurus call), so
 * that a definition never depends on a thesaurus an institution could edit or remove. They are referenced
 * by their external id, like the other system concepts, never by database id.
 * <p>
 * A side concept names the <em>role of the entity at that end</em>, and the field of the entity at the other
 * end is labelled with it: on a unit that is the source of an order link, the "Antérieur" field lists the
 * target end's entities (the source is the posterior one).
 */
public final class SystemRelations {

    private SystemRelations() {
    }

    public static final String VOCABULARY_BASE_URI = "https://thesaurus.mom.fr";
    public static final String VOCABULARY_ID = "siamois-relations";

    /** Order (asynchronous) stratigraphic relation between recording units. Source end = posterior, target = anterior. */
    public static final String ORDER_CODE = "SIAMOIS.RELATION.ORDER";
    /** Synchronous stratigraphic relation between recording units: one side, its own mirror. */
    public static final String SYNCHRONOUS_CODE = "SIAMOIS.RELATION.SYNCHRONOUS";

    // Sides
    public static final String SIDE_POSTERIOR = "side.posterior";
    public static final String SIDE_ANTERIOR = "side.anterior";
    public static final String SIDE_SYNCHRONOUS = "side.synchronous";

    // Types of the order relation, seen from the posterior (source) side...
    public static final String TYPE_CUTS = "order.cuts";
    public static final String TYPE_COVERS = "order.covers";
    public static final String TYPE_FILLS = "order.fills";
    public static final String TYPE_ABUTS = "order.abuts";
    // ... and from the anterior (target) side
    public static final String TYPE_CUT_BY = "order.cutBy";
    public static final String TYPE_COVERED_BY = "order.coveredBy";
    public static final String TYPE_FILLED_BY = "order.filledBy";
    public static final String TYPE_ABUTTED_BY = "order.abuttedBy";

    // The link form's fields: the attributes of a link itself, shared by both of its sides
    public static final String FIELD_UNCERTAIN = "field.uncertain";
    /** Fixed id of the system boolean field "uncertain" of the platform definitions' link form. */
    public static final long UNCERTAIN_FIELD_ID = -900L;
    public static final String UNCERTAIN_FIELD_LABEL = "relation.field.uncertain";

    // Types of the synchronous relation
    public static final String TYPE_EQUAL_TO = "synchronous.equalTo";
    public static final String TYPE_CONTEMPORARY_WITH = "synchronous.contemporaryWith";
    public static final String TYPE_BONDED_TO = "synchronous.bondedTo";
}
