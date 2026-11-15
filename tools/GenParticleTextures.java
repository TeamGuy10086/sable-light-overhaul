import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws the cloud sprites for sablelightoverhaul:puff.
 *
 * <p>Not shipped in the jar: run it with {@code java tools/GenParticleTextures.java <outputDirectory>}
 * to regenerate them. The shapes are a sum of a few soft blobs, which is what makes a puff read as
 * volume rather than as a blurred dot, and the seed is fixed so reruns are byte-identical.
 *
 * <p>Two sprites come out: {@code dust_puff} (tighter, higher contrast, for kicked-up ground dust)
 * and {@code smoke_puff} (looser, softer, for a vent plume). Both are white with an alpha mask, so
 * the particle's colour parameter tints them.
 */
public final class GenParticleTextures {
    private static final int SIZE = 64;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        write(new File(out, "dust_puff.png"), 5, 0.30, 1.9, 0.92, 20250913L);
        write(new File(out, "smoke_puff.png"), 9, 0.38, 1.35, 0.80, 773311L);
        System.out.println("wrote " + new File(out, "dust_puff.png") + " and " + new File(out, "smoke_puff.png"));
    }

    private static void write(File file, int blobs, double radius, double sharpen, double peak, long seed)
            throws Exception {
        Random random = new Random(seed);
        double[][] centers = new double[blobs][3];
        for (int i = 0; i < blobs; i++) {
            centers[i][0] = SIZE * (0.32 + random.nextDouble() * 0.36);
            centers[i][1] = SIZE * (0.32 + random.nextDouble() * 0.36);
            centers[i][2] = SIZE * radius * (0.75 + random.nextDouble() * 0.7);
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double sum = 0;
                for (double[] blob : centers) {
                    double dx = x + 0.5 - blob[0], dy = y + 0.5 - blob[1];
                    sum += Math.exp(-(dx * dx + dy * dy) / (2 * blob[2] * blob[2]));
                }
                // Normalize by the blob count, then sharpen: a plain sum is a flat blob, the power
                // pulls the edge in and keeps a soft falloff.
                double value = Math.pow(Math.min(1.0, sum / blobs * 1.55), sharpen);
                // A hard cutoff at the sprite border would show as a square, so fade the last pixels.
                value *= edgeFade(x) * edgeFade(y);
                int alpha = (int) Math.round(Math.min(1.0, value) * peak * 255);
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        ImageIO.write(image, "png", file);
    }

    private static double edgeFade(int coordinate) {
        double distance = Math.min(coordinate + 0.5, SIZE - coordinate - 0.5);
        return Math.max(0.0, Math.min(1.0, distance / 6.0));
    }
}
