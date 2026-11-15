import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Deterministic original flame texture; run from the project root with `java tools/GenerateFlame.java`. */
class GenerateFlame {
    static double clamp(double n) { return Math.max(0,Math.min(1,n)); }
    static double smooth(double low, double high, double n) {
        double t = clamp((n-low)/(high-low)); return t*t*(3-2*t);
    }
    public static void main(String[] args) throws Exception {
        var image = new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<64; y++) for(int x=0; x<128; x++) {
            double t = (x+0.5)/128.0, v = (y+0.5)/64.0-0.5;
            double bend = 0.027*Math.sin(11*t)*t + 0.018*Math.sin(26*t)*t;
            double width = (0.16+0.30*Math.sin(Math.PI*t))*Math.pow(1-t,0.85);
            width *= 0.88+0.08*Math.sin(37*t)+0.04*Math.cos(71*t);
            double radial = Math.abs(v-bend)/Math.max(0.001,width);
            double alpha = (1-smooth(0.62,1,radial))*smooth(0,0.045,t)*(1-smooth(0.87,1,t));
            // Split tongues toward the tip; no uniform ellipse or hard circular outline.
            double split = smooth(0.45,0.75,t)*Math.exp(-Math.pow((v-bend-0.025)/0.025,2))*0.75;
            alpha *= 1-split;
            double heat = clamp((1-t)*Math.exp(-2.8*radial*radial)*1.45);
            int a=(int)Math.round(alpha*255), r=255, g=(int)(95+160*heat), b=(int)(8+225*heat*heat);
            image.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        Path output=Path.of("src/main/resources/assets/sablelightoverhaul/textures/particle/auto_muzzle.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image,"png",output.toFile());
    }
}
