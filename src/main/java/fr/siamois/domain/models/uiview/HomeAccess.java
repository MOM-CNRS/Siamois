package fr.siamois.domain.models.uiview;

import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * An access to data on the home page of an organisation: a card opening a table on a view (a table, and
 * the type to show, are what the view holds). Configured by who may edit the organisation's default views;
 * seen by every member. Deleting the view takes its access with it.
 */
@Entity
@Table(name = "home_access",
        uniqueConstraints = @UniqueConstraint(name = "ux_home_access_view", columnNames = "fk_view"))
@Getter
@Setter
@NoArgsConstructor
public class HomeAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution", nullable = false)
    private Institution institution;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_view", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UiTableView view;

    /** What the card says: the name of the type it shows (or of the table); the view's title when absent. */
    @Column(length = 200)
    private String label;

    /** Cards are shown in this order. */
    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    private Instant createdAt;
}
