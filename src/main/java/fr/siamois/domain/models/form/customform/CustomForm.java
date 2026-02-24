package fr.siamois.domain.models.form.customform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnTransformer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "custom_form")
public class CustomForm implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_id", nullable = false)
    private Long id;

    @Column(name = "description")
    private String description;

    @Column(name = "name")
    private String name;

    @Column(name = "layout", columnDefinition = "jsonb")
    @Convert(converter = CustomFormLayoutConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private List<CustomFormPanel> layout;

    public static class Builder {

        private final CustomForm form = new CustomForm();
        private final List<CustomFormPanel> panels = new ArrayList<>();

        public Builder name(String name) {
            form.setName(name);
            return this;
        }

        public Builder description(String description) {
            form.setDescription(description);
            return this;
        }

        public Builder addPanel(CustomFormPanel panel) {
            panels.add(panel);
            return this;
        }

        public Builder addPanels(CustomFormPanel... panelArray) {
            panels.addAll(List.of(panelArray));
            return this;
        }

        public Builder addPanels(List<CustomFormPanel> panelList) {
            panels.addAll(panelList);
            return this;
        }

        public CustomForm build() {
            form.setLayout(panels);
            return form;
        }
    }



}
