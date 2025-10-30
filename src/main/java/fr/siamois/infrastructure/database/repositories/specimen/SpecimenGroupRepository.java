package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.specimen.SpecimenGroup;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecimenGroupRepository extends CrudRepository<SpecimenGroup, Long>, RevisionRepository<SpecimenGroup, Long, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT sg.* FROM specimen_group sg JOIN specimen_group_attribution sga ON sg.specimen_group_id = sga.fk_specimen_group_id WHERE sga.fk_specimen_id = :specimenId"
    )
    List<SpecimenGroup> findAllSpecimenGroupsOfSpecimen(@Param("specimenId") Long specimenId);



}