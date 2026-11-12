package fr.siamois.ui.bean.dialog.project;

import lombok.Value;

import java.io.Serializable;

/** A place field of the project form: its id, which gives the server its sources, and its label. */
@Value
public class PlaceField implements Serializable {
    long id;
    String label;
}
