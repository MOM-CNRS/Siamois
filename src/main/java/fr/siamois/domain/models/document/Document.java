package fr.siamois.domain.models.document;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.springframework.util.MimeType;

@Data
@Entity
@Table(name = "siamois_document", schema = "public")
@Audited
public class Document extends DocumentParent implements ArkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id", nullable = false)
    private Long id;

    @FieldCode
    public static final String NATURE_FIELD_CODE = "SIAD.NATURE";

    @FieldCode
    public static final String SCALE_FIELD_CODE = "SIAD.SCALE";

    @FieldCode
    public static final  String FORMAT_FIELD_CODE = "SIAD.FORMAT";

    public String contentFileName() {
        return fileCode + "." + fileExtension();
    }

    public MimeType mimeTypeObject() {
        return MimeType.valueOf(mimeType);
    }

    public String fileExtension() {
        int i = fileName.lastIndexOf('.');
        return fileName.substring(i + 1);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}