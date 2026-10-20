package fr.siamois.domain.models.uiview;

/**
 * What a {@link UiTableView} is: a view a user created ({@link #USER}), or the default view that
 * everyone sees for a record type until they change something ({@link #DEFAULT}).
 */
public enum UiViewKind {
    USER,
    DEFAULT
}
