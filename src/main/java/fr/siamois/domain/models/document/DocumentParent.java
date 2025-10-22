package fr.siamois.domain.models.document;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.util.Objects;


@Data
@MappedSuperclass
@Audited
public abstract class DocumentParent extends TraceableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @Column(name = "title", length = MAX_TITLE_LENGTH)
    protected String title;

    @Column(name = "doc_description", length = MAX_DESCRIPTION_LENGTH)
    protected String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_nature")
    protected Concept nature;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_scale")
    protected Concept scale;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_format")
    protected Concept format;

    @Column(name = "url", length = Integer.MAX_VALUE)
    protected String url;

    @Column(name = "file_name")
    protected String fileName;

    @Column(name = "mime_type", length = Integer.MAX_VALUE)
    protected String mimeType;

    protected Long size;
    @Column(name = "file_internal_code", length = FILE_INTERNAL_CODE_LENGTH, unique = true)
    protected String fileCode;

    @Column(name = "md5_sum")
    protected String md5Sum;

    protected String storedFileName;

    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int FILE_INTERNAL_CODE_LENGTH = 10;
    public static final int MAX_DESCRIPTION_LENGTH = 1024;
    public static final int MAX_TITLE_LENGTH = 50;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocumentParent that)) return false;
        return Objects.equals(md5Sum, that.md5Sum);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(md5Sum);
    }
}
