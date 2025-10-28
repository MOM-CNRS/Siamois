package fr.siamois.infrastructure.api.controller;


import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/static/uploads")
public class StaticImageController {

    // ⚠️ Chemin absolu sur ton poste (à adapter)
    private static final String BASE_PATH = "C:/Users/admin/Pictures/uploads/";

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Path imagePath = Path.of(BASE_PATH, filename);
        File file = imagePath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        // on force le Content-Type = image/png
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}
