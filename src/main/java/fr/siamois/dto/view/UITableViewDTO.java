package fr.siamois.dto.view;

import fr.siamois.dto.entity.PersonDTO;
import lombok.Data;

@Data
public class UITableViewDTO {

    private Long id;

    private String resourceType;

    private String title;

    /**
     * Full UI configuration
     */
    private TableViewState state;

    private PersonDTO owner;
}
