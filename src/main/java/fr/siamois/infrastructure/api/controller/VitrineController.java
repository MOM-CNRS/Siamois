package fr.siamois.infrastructure.api.controller;


import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.infrastructure.api.dto.PrelevementDTO;
import fr.siamois.infrastructure.api.mapper.VitrineMapper;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class VitrineController {

    private final SpecimenRepository specimenRepository;

    public VitrineController(SpecimenRepository specimenRepository) {
        this.specimenRepository = specimenRepository;
    }

    // GET /api/vitrine
    @GetMapping("/vitrine")
    public ResponseEntity<List<PrelevementDTO>> listPrelevements() {
        List<Specimen> prelevements = (List<Specimen>) specimenRepository.findAll();
        List<PrelevementDTO> dtos = prelevements.stream()
                .map(VitrineMapper::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}

