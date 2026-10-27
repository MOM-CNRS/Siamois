package fr.siamois.domain.services.document;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Reduced copies of image files, for the list cards. Only what the JDK can decode (jpeg, png, gif,
 * bmp); anything else (webp, tiff, heic…) yields empty and the caller serves the original.
 */
@Component
public class ImageThumbnailer {

    public static final int MIN_WIDTH = 16;
    public static final int MAX_WIDTH = 1600;

    /** A JPEG at most {@code width} pixels wide, same ratio; never enlarges. Empty if the image cannot be decoded. */
    public Optional<byte[]> thumbnail(InputStream source, int width) throws IOException {
        int target = Math.max(MIN_WIDTH, Math.min(width, MAX_WIDTH));
        BufferedImage image = ImageIO.read(source);
        if (image == null) {
            return Optional.empty();
        }
        int w = Math.min(target, image.getWidth());
        int h = Math.max(1, Math.round((float) image.getHeight() * w / image.getWidth()));
        // JPEG has no alpha: transparent pixels land on white.
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, w, h, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return ImageIO.write(scaled, "jpg", out) ? Optional.of(out.toByteArray()) : Optional.empty();
    }
}
