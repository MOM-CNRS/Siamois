package fr.siamois.domain.models.uiview;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * What one user changed on one view (filters, sort, columns, page size, list/tree mode, active tab…),
 * kept so they find the same state next time. The view itself is never modified by it.
 *
 * <p>{@code viewKey} is {@code view:<id>} for a saved view, {@code ctx:<resourceType>:<context>} for
 * the implicit default list of a context, {@code detail:<resourceType>:<id>} for one record (its active tab and what each tab kept),
 * {@code ctx:<resourceType>:<parentType>:<parentId>-tab} for a list inside a record's tab.
 * The shape of {@code state} belongs to the front end.</p>
 */
@Entity
@Table(name = "user_view_state",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_person", "fk_institution", "view_key"}))
@Getter
@Setter
@NoArgsConstructor
public class UserViewState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution", nullable = false)
    private Institution institution;

    @Column(name = "view_key", nullable = false, length = 300)
    private String viewKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> state;

    @UpdateTimestamp
    private Instant updatedAt;
}
