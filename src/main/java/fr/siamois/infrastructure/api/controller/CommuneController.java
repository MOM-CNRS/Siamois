package fr.siamois.infrastructure.api.controller;


import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.infrastructure.api.dto.CommuneDTO;
import fr.siamois.infrastructure.api.mapper.CommuneMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommuneController {

    private final SpatialUnitService spatialUnitService;

    public CommuneController(SpatialUnitService spatialUnitService) {
        this.spatialUnitService = spatialUnitService;
    }

    // GET /api/communes
    @GetMapping("/communes")
    public ResponseEntity<List<CommuneDTO>> listCommunes() {
        List<SpatialUnit> units;

        units = spatialUnitService.findAll();

        List<CommuneDTO> dtos = units.stream().map(CommuneMapper::toDto).toList();
        return ResponseEntity.ok(dtos);
    }
}
