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
            "intitule": "Protohistoire",
            "tpq": -2300,
            "taq": -50
          },
          {
            "id": 1,
            "niveau": 2,
            "intitule": "Âge du Fer",
            "tpq": -800,
            "taq": -50
          },
          {
            "id": 2,
            "niveau": 3,
            "intitule": "Hallstatt",
            "tpq": -800,
            "taq": -450
          },
          {
            "id": 3,
            "niveau": 3,
            "intitule": "La tène",
            "tpq": -450,
            "taq": -50
          }
        ]
        """;

        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(json);
    }
}
