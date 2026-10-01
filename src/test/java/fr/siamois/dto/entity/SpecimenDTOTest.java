package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpecimenDTOTest {

    private SpecimenDTO original() {
        SpecimenDTO o = new SpecimenDTO();
        o.setId(7L);
        o.setIdentifier(3);
        o.setFullIdentifier("UE1-3");
        o.setOtherIdentifier("ANCIEN-3");
        o.setIsolationNumber("ISO-9");
        o.setType(new ConceptDTO());
        o.setCategory(new ConceptDTO());
        o.setRecordingUnit(new RecordingUnitSummaryDTO());
        o.setActionUnit(new ActionUnitSummaryDTO());
        o.setCreatedByInstitution(new InstitutionDTO());
        o.setAuthors(new ArrayList<>(List.of(new PersonDTO())));
        o.setCollectors(new ArrayList<>(List.of(new PersonDTO())));
        o.setMaterial(new HashSet<>(Set.of(new ConceptDTO())));
        o.setDescription("silex");
        o.setComments("à revoir");
        o.setTaq(100);
        o.setTpq(50);
        o.setNumberOfElements(2);
        o.setParents(new HashSet<>(Set.of(new SpecimenSummaryDTO())));
        o.setContainers(new HashSet<>(Set.of(new ContainerDTO())));
        o.setPhases(new HashSet<>(Set.of(new PhaseDTO())));
        MeasurementAnswerDTO weight = new MeasurementAnswerDTO();
        weight.setId(5L);
        weight.setNumericValue(12.5);
        o.setWeight(weight);
        return o;
    }

    @Test
    void copy_keepsTheDescriptiveDataOnTheSameRecordingUnit() {
        SpecimenDTO o = original();

        SpecimenDTO copy = new SpecimenDTO(o);

        assertThat(copy.getRecordingUnit()).isSameAs(o.getRecordingUnit());
        assertThat(copy.getActionUnit()).isSameAs(o.getActionUnit());
        assertThat(copy.getCreatedByInstitution()).isSameAs(o.getCreatedByInstitution());
        assertThat(copy.getType()).isSameAs(o.getType());
        assertThat(copy.getCategory()).isSameAs(o.getCategory());
        assertThat(copy.getDescription()).isEqualTo("silex");
        assertThat(copy.getComments()).isEqualTo("à revoir");
        assertThat(copy.getTaq()).isEqualTo(100);
        assertThat(copy.getTpq()).isEqualTo(50);
        assertThat(copy.getNumberOfElements()).isEqualTo(2);
        assertThat(copy.getMaterial()).isEqualTo(o.getMaterial());
    }

    @Test
    void copy_isNewAndKeepsWhatNamesOneParticularFind_out() {
        SpecimenDTO copy = new SpecimenDTO(original());

        assertThat(copy.getId()).isNull();
        assertThat(copy.getIdentifier()).isNull();
        assertThat(copy.getFullIdentifier()).isNull();
        assertThat(copy.getOtherIdentifier()).isNull();
        assertThat(copy.getIsolationNumber()).isNull();
        assertThat(copy.getParents()).isNull();
        assertThat(copy.getChildren()).isNull();
        assertThat(copy.getContainers()).isNull();
        assertThat(copy.getPhases()).isNull();
    }

    @Test
    void copy_ownsItsCollectionsAndItsWeight() {
        SpecimenDTO o = original();

        SpecimenDTO copy = new SpecimenDTO(o);
        copy.getAuthors().clear();
        copy.getMaterial().clear();

        assertThat(o.getAuthors()).hasSize(1);
        assertThat(o.getMaterial()).hasSize(1);
        assertThat(copy.getWeight()).isNotSameAs(o.getWeight());
        assertThat(copy.getWeight().getId()).isNull();
        assertThat(copy.getWeight().getNumericValue()).isEqualTo(12.5);
    }
}
