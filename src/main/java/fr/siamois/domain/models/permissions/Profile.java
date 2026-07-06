package fr.siamois.domain.models.permissions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "profile", indexes = {
        @Index(columnList = "code", name = "idx_profile_code"),
        @Index(columnList = "fk_institution_id", name = "idx_profile_institution"),
        @Index(columnList = "fk_action_unit_id", name = "idx_profile_action_unit")
})
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private Long id;

    @NonNull
    @EqualsAndHashCode.Include
    private String code;

    @EqualsAndHashCode.Include
    private String name;

    @NotNull
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "scope", nullable = false)
    @EqualsAndHashCode.Include
    private PermissionScopeType scope;

    @ManyToMany
    @JoinTable(name = "profile_permission",
            joinColumns = @JoinColumn(name = "fk_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_permission_id"),
            indexes = {
                    @Index(columnList = "fk_profile_id", name = "idx_profile_permission_profile"),
                    @Index(columnList = "fk_permission_id", name = "idx_profile_permission_permission")
            }
    )
    @Builder.Default
    private final Set<Permission> permissions = new HashSet<>();

    /**
     * Institution is mandatory if the scope is ORGANISATION or PROJECT
     */
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution_id")
    @EqualsAndHashCode.Include
    private Institution institution;

    /**
     * Action unit is mandatory if the score is PROJECT
     */
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    @EqualsAndHashCode.Include
    private ActionUnit actionUnit;

}
