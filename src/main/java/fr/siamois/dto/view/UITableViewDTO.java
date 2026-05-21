package fr.siamois.dto.view;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class UITableViewDTO {

    private Long id;

    private String resourceType;

    /**
     * Full UI configuration
     */
    private TableViewState state;
}
