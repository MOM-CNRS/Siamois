package fr.siamois.dto.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class FullAddress implements Serializable {
    private String label;    // fulltext
    private String street;   // street
    private String postcode; // zipcode
    private String city;     // city
    private Double lon;      // x
    private Double lat;      // y
}
