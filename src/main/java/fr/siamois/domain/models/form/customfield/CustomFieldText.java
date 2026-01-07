package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.function.Supplier;


@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@DiscriminatorValue("TEXT")
@NoArgsConstructor
@Table(name = "custom_field")
@SuperBuilder
public class CustomFieldText extends CustomField {

    @Column(name = "is_text_area")
    private Boolean isTextArea;

    private transient Supplier<String> autoGenerationFunction;

    public String generateAutoValue() {
        return autoGenerationFunction != null ? autoGenerationFunction.get() : null;
    }// function to autogenerate the value

}
