package fr.siamois.domain.models.permissions;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This class represents a permission (Create an organization, access an organization info, manage members).
 * The scope of this permission is defined in the {@link Profile} object.
 */
@Entity
@Data
@Table(name = "permission", indexes = {
        @Index(columnList = "permission_code", name = "idx_permission_code")
})
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    @EqualsAndHashCode.Exclude
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "permission_code", nullable = false)
    private String code;

    @Override
    public String toString() {
        if (code == null) {
            return "";
        }
        return code;
    }
}
