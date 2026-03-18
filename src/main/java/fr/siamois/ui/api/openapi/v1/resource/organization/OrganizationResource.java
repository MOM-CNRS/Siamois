package fr.siamois.ui.api.openapi.v1.resource.organization;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class OrganizationResource
        extends OrganizationResourceIdentifier {

    private String name;
    private String description;
    private String identifier;

}
