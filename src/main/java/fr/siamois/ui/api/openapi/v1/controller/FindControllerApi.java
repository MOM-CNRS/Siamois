package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.FindResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/finds")
@Tag(name = "Mobilier", description = "Endpoints des unités des mobiliers")
public class FindControllerApi {


    @Operation(summary = "La liste des mobiliers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/")
    public ResponseEntity<FindListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "Un mobilier via son identifiant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    public ResponseEntity<FindResponse> getById(
            @PathVariable Long id
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

}
