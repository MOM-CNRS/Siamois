package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@DiscriminatorValue("DATETIME")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldDateTime extends CustomField {

    private Boolean showTime;

    private LocalDateTime min;

    private LocalDateTime max;

}
