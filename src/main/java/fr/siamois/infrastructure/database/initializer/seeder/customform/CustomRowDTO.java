package fr.siamois.infrastructure.database.initializer.seeder.customform;

import java.io.Serializable;
import java.util.List;

public record CustomRowDTO (List<CustomColDTO> columns) implements Serializable {}

