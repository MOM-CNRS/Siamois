package fr.siamois.domain.models.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.siamois.domain.models.FieldCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
@Entity
@Table(name = "person", schema = "public")
@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"})
public class Person implements UserDetails {

    // This limit allows the UI to be controlled
    public static final int NAME_MAX_LENGTH = 64;

    // In UNIX, usernames are limited to 32 characters
    // If the system should one day communicate with a UNIX system, it should respect this limit.
    public static final int USERNAME_MAX_LENGTH = 32;

    // https://www.rfc-editor.org/errata/eid1003
    // RFC 3696 applies a limit 320 characters from email adresses.
    // 64 chars for the local part and 255 chars for the domain part
    public static final int LOCAL_MAIL_MAX_LENGTH = 64;
    public static final int DOMAIN_MAIL_MAX_LENGTH = 255;
    public static final int MAIL_MAX_LENGTH = LOCAL_MAIL_MAX_LENGTH + 1 + DOMAIN_MAIL_MAX_LENGTH;

    public static final int PASSWORD_MAX_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "person_id", nullable = false)
    private Long id;

    @Column(name = "name", length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "lastname", length = NAME_MAX_LENGTH)
    private String lastname;

    @NotNull
    @Column(name = "username", length = USERNAME_MAX_LENGTH, unique = true, columnDefinition = "citext")
    private String username;

    // The password length shouldn't be set in the database as we don't know their size after hash.
    @NotNull
    @Column(name = "password", nullable = false, length = Integer.MAX_VALUE)
    @NotAudited
    private String password;

    @NotNull
    @Column(name = "mail", nullable = false, length = MAIL_MAX_LENGTH, unique = true, columnDefinition = "citext")
    private String email;

    @ColumnDefault("false")
    @Column(name = "pass_to_modify")
    private boolean passToModify = false;

    @ColumnDefault("false")
    @Column(name = "alert_mail")
    private boolean alertMail = false;

    @ColumnDefault("false")
    @Column(name = "is_super_admin")
    private boolean isSuperAdmin = false;

    @Column(name = "api_key", length = Integer.MAX_VALUE)
    private String apiKey;

    @ColumnDefault("false")
    @Column(name = "key_never_expire")
    private boolean keyNeverExpire = false;

    @Column(name = "key_expires_at")
    private OffsetDateTime keyExpiresAt;

    @ColumnDefault("false")
    @Column(name = "is_service_account")
    private boolean isServiceAccount = false;

    @Column(name = "key_description", length = Integer.MAX_VALUE)
    private String keyDescription;

    @ColumnDefault("false")
    @Column(name = "is_expired")
    private boolean isExpired = false;

    @ColumnDefault("false")
    @Column(name = "is_locked")
    private boolean isLocked = false;

    @ColumnDefault("true")
    @Column(name = "is_enabled")
    private boolean isEnabled = true;

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SystemRole> roles = new ArrayList<>();
        if (!this.isSuperAdmin)
            roles.add(new SystemRole("SUPER_ADMIN"));
        return roles;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return !isExpired;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return !isLocked;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @FieldCode
    public static final String USER_ROLE_FIELD_CODE = "SIAP.ROLE";

    // For displaying person full names in forms
    // Used in xhtml files
    public String displayName() {
        return name + " " + lastname;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person person)) return false;

        return Objects.equals(email, person.email)
                && Objects.equals(username, person.username)
                && Objects.equals(name, person.name)
                && Objects.equals(lastname, person.lastname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, username, name, lastname);
    }
}