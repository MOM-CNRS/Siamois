package fr.siamois.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GeoPlatResult {
    private double x;         // longitude
    private double y;         // latitude
    private String country;
    private String city;
    private String oldcity;
    private String kind;
    private String zipcode;
    private String street;
    private boolean metropole;
    private String fulltext;
    private int classification;
}
