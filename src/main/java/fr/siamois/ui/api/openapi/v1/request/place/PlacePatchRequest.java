package fr.siamois.ui.api.openapi.v1.request.place;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Mise à jour partielle d'un lieu. Champs absents ou null = inchangés.")
public class PlacePatchRequest {

    @Schema(description = "Nouveau statut de validation, absent = inchangé : INCOMPLETE (en cours), COMPLETE (terminé), "
            + "CANCELLED (annulé) avec le droit de modification ; VALIDATED (validé) — l'atteindre ou le quitter — "
            + "avec le droit validateur (_permissions.canValidate).")
    private ValidationStatus validationStatus;

    @Schema(description = "Nom du lieu")
    private String name;

    @Schema(description = "Identifiant du concept de type (SIASU.TYPE)")
    private Long typeConceptId;

    @Schema(description = "Adresse postale ou géolocalisée")
    private FullAddress address;

    @Schema(description = "Numéro de regroupement du lieu; null explicite supprime la valeur")
    private Integer placeNumber;

    @JsonIgnore
    private boolean placeNumberPresent;

    @JsonSetter("placeNumber")
    public void setPlaceNumber(Integer placeNumber) {
        this.placeNumber = placeNumber;
        this.placeNumberPresent = true;
    }

    @Schema(description = "Géométrie du lieu (GeoJSON), dans le SRID fourni ; aucune reprojection n'est effectuée. " +
            "Champ absent = inchangé ; null explicite = supprime la géométrie")
    private GeometryDTO geom;

    @JsonIgnore
    private boolean geomPresent;

    @JsonSetter("geom")
    public void setGeom(GeometryDTO geom) {
        this.geom = geom;
        this.geomPresent = true;
    }

    @Schema(description = "Code INSEE de la commune; null explicite supprime la valeur")
    private String inseeCode;

    @JsonIgnore
    private boolean inseeCodePresent;

    @JsonSetter("inseeCode")
    public void setInseeCode(String inseeCode) {
        this.inseeCode = inseeCode;
        this.inseeCodePresent = true;
    }

    @Schema(description = "Nom de la localité; null explicite supprime la valeur")
    private String localityName;

    @JsonIgnore
    private boolean localityNamePresent;

    @JsonSetter("localityName")
    public void setLocalityName(String localityName) {
        this.localityName = localityName;
        this.localityNamePresent = true;
    }

    @Schema(description = "Numéro de carte du SHOM; null explicite supprime la valeur")
    private String shomMapNumber;

    @JsonIgnore
    private boolean shomMapNumberPresent;

    @JsonSetter("shomMapNumber")
    public void setShomMapNumber(String shomMapNumber) {
        this.shomMapNumber = shomMapNumber;
        this.shomMapNumberPresent = true;
    }

    @Schema(description = "Rue de l'adresse; null explicite supprime la valeur")
    private String addressStreet;

    @JsonIgnore
    private boolean addressStreetPresent;

    @JsonSetter("addressStreet")
    public void setAddressStreet(String addressStreet) {
        this.addressStreet = addressStreet;
        this.addressStreetPresent = true;
    }

    @Schema(description = "Code postal de l'adresse; null explicite supprime la valeur")
    private String addressPostcode;

    @JsonIgnore
    private boolean addressPostcodePresent;

    @JsonSetter("addressPostcode")
    public void setAddressPostcode(String addressPostcode) {
        this.addressPostcode = addressPostcode;
        this.addressPostcodePresent = true;
    }

    @Schema(description = "Commune de l'adresse ou de la parcelle; null explicite supprime la valeur")
    private String addressCity;

    @JsonIgnore
    private boolean addressCityPresent;

    @JsonSetter("addressCity")
    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
        this.addressCityPresent = true;
    }

    @Schema(description = "Section cadastrale de la parcelle; null explicite supprime la valeur")
    private String parcelSection;

    @JsonIgnore
    private boolean parcelSectionPresent;

    @JsonSetter("parcelSection")
    public void setParcelSection(String parcelSection) {
        this.parcelSection = parcelSection;
        this.parcelSectionPresent = true;
    }

    @Schema(description = "Numéro de la parcelle; null explicite supprime la valeur")
    private String parcelNumber;

    @JsonIgnore
    private boolean parcelNumberPresent;

    @JsonSetter("parcelNumber")
    public void setParcelNumber(String parcelNumber) {
        this.parcelNumber = parcelNumber;
        this.parcelNumberPresent = true;
    }

    @Schema(description = "Commentaires; null explicite supprime la valeur")
    private String comments;

    @JsonIgnore
    private boolean commentsPresent;

    @JsonSetter("comments")
    public void setComments(String comments) {
        this.comments = comments;
        this.commentsPresent = true;
    }

    /** Whether the patch touches one of the place's plain text details (INSEE code, locality, SHOM map, comments). */
    @JsonIgnore
    public boolean isTextDetailsPresent() {
        return inseeCodePresent || localityNamePresent || shomMapNumberPresent || commentsPresent
                || addressStreetPresent || addressPostcodePresent || addressCityPresent
                || parcelSectionPresent || parcelNumberPresent;
    }
}
