package fr.siamois.ui.api.openapi.v1.resource.person;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonResource extends PersonResourceIdentifier {

    @Schema(description = "Prénom")
    private String name;

    @Schema(description = "Nom de famille")
    private String lastname;

    @Schema(description = "Adresse e-mail")
    private String email;

    @Schema(description = "Identifiant de connexion")
    private String username;

    public PersonResource(String name, String lastname, String email, String username) {
        super("persons", null);
        this.name = name;
        this.lastname = lastname;
        this.email = email;
        this.username = username;
    }
}
