package fr.siamois.infrastructure.database.initializer.seeder.customform;



import java.io.Serializable;
import java.util.List;


public record CustomFormPanelDTO (
    String className,
    String name,
    List<CustomRowDTO> rows,
    boolean isSystemPanel // define by system or user?
) implements Serializable {}
