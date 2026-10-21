package fr.siamois.domain.services.document;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageThumbnailerTest {

    private final ImageThumbnailer thumbnailer = new ImageThumbnailer();

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void thumbnail_keepsTheRatio() throws IOException {
        byte[] result = thumbnailer.thumbnail(new ByteArrayInputStream(png(800, 400)), 200).orElseThrow();

        BufferedImage image = decode(result);
        assertEquals(200, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void thumbnail_neverEnlarges() throws IOException {
        byte[] result = thumbnailer.thumbnail(new ByteArrayInputStream(png(100, 50)), 480).orElseThrow();

        assertEquals(100, decode(result).getWidth());
    }

    @Test
    void thumbnail_clampsTheRequestedWidth() throws IOException {
        byte[] result = thumbnailer.thumbnail(new ByteArrayInputStream(png(4000, 2000)), 100_000).orElseThrow();

        assertEquals(ImageThumbnailer.MAX_WIDTH, decode(result).getWidth());
    }

    @Test
    void thumbnail_isEmptyForWhatTheJdkCannotDecode() throws IOException {
        assertTrue(thumbnailer.thumbnail(new ByteArrayInputStream("not an image".getBytes()), 200).isEmpty());
    }
}
