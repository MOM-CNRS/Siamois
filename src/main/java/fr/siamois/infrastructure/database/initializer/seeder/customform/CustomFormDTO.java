package fr.siamois.infrastructure.database.initializer.seeder.customform;



import java.io.Serializable;
import java.util.List;


public record CustomFormDTO (
     String description,
     String name,
     List<CustomFormPanelDTO> layout
) implements Serializable {}
