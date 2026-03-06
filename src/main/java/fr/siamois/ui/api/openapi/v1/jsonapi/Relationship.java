package fr.siamois.ui.api.openapi.v1.jsonapi;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Relationship<T> {

    private T data;

}
