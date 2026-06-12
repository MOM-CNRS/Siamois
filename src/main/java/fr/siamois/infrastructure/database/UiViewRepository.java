package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.UiTableView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UiViewRepository extends JpaRepository<UiTableView, Long> {


}