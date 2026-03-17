package fr.siamois.ui.api.openapi.v1.generic.response;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Relationship<T> {

    private T data;


}
