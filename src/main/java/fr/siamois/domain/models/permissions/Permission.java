package fr.siamois.domain.models.permissions;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

/**
 * This class represents a permission (Create an organization, access an organization info, manage members).
 * The scope of this permission is defined in the {@link Profile} object.
 */
@Entity
@Data
@Table(name = "permission", indexes = {
        @Index(columnList = "permission_code", name = "idx_permission_code")
})
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    @EqualsAndHashCode.Exclude
    private Long id;

    @NotNull
    @NonNull
    @EqualsAndHashCode.Include
    @Column(name = "permission_code", nullable = false)
    private String code;

    @Override
    public String toString() {
        return code;
    }
}
