package fr.siamois.infrastructure.api.dto.geoapi;

import lombok.Data;

import java.util.List;

@Data
public class CommuneReponse {
    String nom;
    String code;
    String codeDepartement;
    String siren;
    String codeEpci;
    String codeRegion;
    List<String> codePostaux;
}
