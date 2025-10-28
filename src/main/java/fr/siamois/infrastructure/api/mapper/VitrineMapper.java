package fr.siamois.infrastructure.api.mapper;




import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.infrastructure.api.dto.CartelDTO;
import fr.siamois.infrastructure.api.dto.MediaDTO;
import fr.siamois.infrastructure.api.dto.PhotographieDTO;
import fr.siamois.infrastructure.api.dto.PrelevementDTO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VitrineMapper {

    private VitrineMapper() {}

    public static PrelevementDTO toDTO(Specimen p) {

        CartelDTO mockupCartel = new CartelDTO(
                0L,
                "Mockup cartel content",
                1L
        );

        Set<Document> mockupDocuments = new HashSet<>();
        Document photo = new Document();
        photo.setId(1L);
        photo.setUrl("227a1-copie.jpg");
        mockupDocuments.add(photo);

        return new PrelevementDTO(
                p.getId(),
                p.getType().getId(),
                1L,
                p.getFullIdentifier(),
                List.of(4L),
                0L,
                toPhotographiesDTO(mockupDocuments),
                List.of(mockupCartel),
                toMediasDTO()
        );
    }

    private static List<PhotographieDTO> toPhotographiesDTO(Set<Document> documents) {
        return documents == null ? List.of() :
                documents.stream()
                        .map(doc -> new PhotographieDTO(doc.getId(), doc.getUrl()))
                        .toList();
    }

    private static List<MediaDTO> toMediasDTO() {
        return  List.of();
    }
}

