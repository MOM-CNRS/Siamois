package fr.siamois.infrastructure.api.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChronologyController {

    @GetMapping("/chronologies")
    public ResponseEntity<String> getChronologies() {
        String json = """
        [
          {
            "id": 0,
            "niveau": 1,
            "intitule": "Paléolithique",
            "tpq": -3000000,
            "taq": -9600
          },
          {
            "id": 1,
            "niveau": 2,
            "intitule": "Paléolithique Ancien",
            "tpq": -3000000,
            "taq": -300000
          },
          {
            "id": 2,
            "niveau": 2,
            "intitule": "Paléolithique Moyen",
            "tpq": -300000,
            "taq": -38000
          }
        ]
        """;

        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(json);
    }
}
