package fr.siamois.domain.services.attributeconverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.FullAddress;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Converter(autoApply = true)
public class FullAddressConverter implements AttributeConverter<FullAddress, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(FullAddress attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur lors de la sérialisation de FullAddress", e);
        }
    }

    @Override
    public FullAddress convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, FullAddress.class);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la désérialisation de FullAddress", e);
        }
    }
}