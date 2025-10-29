package fr.siamois.infrastructure.api.controller;


import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.api.dto.CommuneDTO;
import fr.siamois.infrastructure.api.dto.MatiereDTO;
import fr.siamois.infrastructure.api.mapper.CommuneMapper;
import fr.siamois.infrastructure.api.mapper.MatiereMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listes")
public class MatiereController {

    public MatiereController() {

    }

    @GetMapping("/ListeMatiere")
    public ResponseEntity<List<MatiereDTO>> listMatiere() {
        List<ConceptLabel> list;

        Concept metal = new Concept();
        metal.setExternalId("4282395");
        metal.setId(23L);
        Concept monnaie = new Concept();
        monnaie.setExternalId("4287742");
        monnaie.setId(24L);

        ConceptLabel cl1 = new ConceptLabel();
        cl1.setConcept(metal);
        cl1.setValue("Métal");

        ConceptLabel cl2 = new ConceptLabel();
        cl2.setConcept(monnaie);
        cl2.setValue("Monnaie");

        list = List.of(cl1, cl2);

        List<MatiereDTO> dtos = list.stream().map(MatiereMapper::toDTO).toList();
        return ResponseEntity.ok(dtos);
    }
}
