package fr.siamois.ui.form.dto;

/**
 * A form column's width across breakpoints, mobile-first: {@code span} is the width below the
 * {@code md} breakpoint, {@code md} overrides it from {@code md} up, {@code lg} overrides it again
 * from {@code lg} up — either may be {@code null} to mean "no override at this tier", exactly like
 * a column that carried only {@code ui-g-12} with no {@code ui-md-}/{@code ui-lg-} class today.
 *
 * <p>This is the single structured source of truth two different renderers convert FROM:
 * {@link CustomColUiDto#getClassName()} turns it into the PrimeFaces {@code ui-g-N ui-md-N ui-lg-N}
 * classes {@code customFormPanelContent.xhtml}'s {@code p:column styleClass} binds to, and the
 * React frontend (entities/project/form.ts's {@code toPrimeFlexClass}) turns the same object into
 * PrimeFlex's {@code col-N md:col-N lg:col-N}. Neither side invents its own numbers — both read
 * this.</p>
 *
 * <p>Scoped today to {@code ActionUnitDetailsForm}/{@code RecordingUnitDetailsForm} — the only two
 * layouts that reach React via {@link FormUiDtoLayoutJson}. Every other form
 * (Container/Phase/Specimen/SpatialUnit/*NewForm) still builds its columns with the older
 * {@code CustomColUiDto.Builder#className(String)}, which {@code getClassName()} falls back to
 * verbatim when no {@code width} was set — converting those forms too is a separate, much larger
 * change (~140 call sites across 12 JSF-only files) that was deliberately left out of this one.</p>
 */
public record ColumnWidth(int span, Integer md, Integer lg) {

    /** ui-g-12 ui-md-6 ui-lg-3 — the default column: full width, half at md, a quarter at lg. */
    public static final ColumnWidth STANDARD = new ColumnWidth(12, 6, 3);
    /** ui-g-12 ui-md-6 ui-lg-6 — a wider column that only ever halves, never quarters. */
    public static final ColumnWidth HALF = new ColumnWidth(12, 6, 6);
    /** ui-g-12 ui-md-12 ui-lg-12 — always the full row (a textarea, a tree picker, a long field). */
    public static final ColumnWidth FULL = new ColumnWidth(12, 12, 12);

    public String toPrimeFacesClassName() {
        StringBuilder className = new StringBuilder("ui-g-").append(span);
        if (md != null) {
            className.append(" ui-md-").append(md);
        }
        if (lg != null) {
            className.append(" ui-lg-").append(lg);
        }
        return className.toString();
    }
}
