package fr.siamois.domain.services.dataimport;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ImportSpecs;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.dataimport.ImportResult;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Runs the import parse/persist phases on a background thread so the UI can poll {@link ImportProgress}
 * for live status. Wraps the existing, still-synchronous {@link OOXMLImportService}/{@link ProjectDataSeeder}
 * rather than making them {@code @Async} directly, since both are also called synchronously elsewhere
 * (e.g. startup dataset seeding).
 */
@Service
@RequiredArgsConstructor
public class ImportAsyncRunner {

    private final OOXMLImportService importService;
    private final ProjectDataSeeder seeder;

    @Async("importTaskExecutor")
    public void parseAsync(byte[] fileBytes, OOXMLImportService.ImportScope scope, ActionUnitDTO project,
                            ImportProgress progress, Consumer<ImportResult> onSuccess, Consumer<Exception> onError) {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            ImportResult result = importService.importFromExcel(is, scope, project, progress);
            // Bean field writes must happen before the volatile progress.complete() write below,
            // so the polling thread is guaranteed (via the JMM happens-before edge on that volatile
            // write) to see them once it observes phase == DONE — not just eventually.
            onSuccess.accept(result);
            progress.complete();
        } catch (Exception e) {
            onError.accept(e);
            progress.fail(e.getMessage());
        }
    }

    @Async("importTaskExecutor")
    public void persistAsync(ImportSpecs specs, ActionUnitDTO project, ImportProgress progress,
                              Runnable onSuccess, Consumer<Exception> onError) {
        try {
            seeder.seedAll(specs, project, progress);
            onSuccess.run();
            progress.complete();
        } catch (Exception e) {
            onError.accept(e);
            progress.fail(e.getMessage());
        }
    }
}
