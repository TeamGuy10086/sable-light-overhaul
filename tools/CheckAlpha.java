import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Prints the alpha profile of a sprite: corners must be transparent, the middle opaque. */
public final class CheckAlpha {
    public static void main(String[] args) throws Exception {
        for (String path : args) {
            BufferedImage image = ImageIO.read(new File(path));
            int size = image.getWidth();
            int corner = alpha(image, 0, 0);
            int center = alpha(image, size / 2, size / 2);
            int min = 255, max = 0, transparent = 0, opaque = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int a = alpha(image, x, y);
                    min = Math.min(min, a);
                    max = Math.max(max, a);
                    if (a == 0) transparent++;
                    if (a > 250) opaque++;
                }
            }
            System.out.printf("%s %dx%d corner=%d center=%d min=%d max=%d transparent=%d opaque=%d%n",
                    new File(path).getName(), size, image.getHeight(), corner, center, min, max, transparent, opaque);
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }
}
