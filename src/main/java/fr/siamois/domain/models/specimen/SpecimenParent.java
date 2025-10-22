package fr.siamois.domain.models.specimen;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.Objects;

@Data
@MappedSuperclass
@Audited
public abstract class SpecimenParent extends TraceableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_specimen_category")
    protected Concept category; // lot, object, echantillon

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_specimen_type")
    protected Concept type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_collection_method")
    protected Concept collectionMethod;

    @Column(name = "collection_date")
    protected OffsetDateTime collectionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_recording_unit_id")
    protected RecordingUnit recordingUnit;

    @NotNull
    @Column(name = "identifier")
    protected Integer identifier;

    @NotNull
    @Column(name = "full_identifier")
    protected String fullIdentifier;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Specimen that = (Specimen) o;
        return Objects.equals(fullIdentifier, that.fullIdentifier);  // Compare based on RecordingUnit
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullIdentifier);  // Hash based on RecordingUnit
    }

    // utils
    public String displayFullIdentifier() {
        if(getFullIdentifier() == null) {
            if(getRecordingUnit().getFullIdentifier() == null) {
                throw new NullActionUnitIdentifierException("Recording identifier must be set");
            }
            return getRecordingUnit().getFullIdentifier() + "-" + (getIdentifier() == null ? "?" : getIdentifier());
        }
        else {
            return getFullIdentifier();
        }
    }

}
