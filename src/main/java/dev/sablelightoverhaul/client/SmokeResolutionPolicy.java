package dev.sablelightoverhaul.client;

final class SmokeResolutionPolicy {
    private SmokeResolutionPolicy() {}

    static int choose(int performance,double area,int previous) {
        if (performance <= 0) return 1;
        if (performance >= 100) return 4;
        double scale = Math.pow(4, (50 - performance) / 50.0);
        double half = 4 * scale;
        double quarter = 12 * scale;
        if (area >= quarter * (previous == 4 ? .8 : 1)) return 4;
        if (area >= half * (previous >= 2 ? .8 : 1)) return 2;
        return 1;
    }

    static double clippedArea(double x,double y,double halfWidth,double halfHeight) {
        double width = Math.max(0, Math.min(1, x + halfWidth) - Math.max(-1, x - halfWidth));
        double height = Math.max(0, Math.min(1, y + halfHeight) - Math.max(-1, y - halfHeight));
        return width * height * .25;
    }

    static int choosePhoton(int performance,double area,int previous) {
        if (performance <= 0) return 1;
        if (performance >= 100) return 16;
        if (performance < 50) {
            double threshold = 4.0 * (50 - performance) / performance;
            return area >= threshold * (previous == 2 ? .8 : 1) ? 2 : 1;
        }
        if (performance > 50) {
            double scale = (100.0 - performance) / (performance - 50);
            if (reaches(area, 72 * scale, 16, previous)) return 16;
            if (reaches(area, 24 * scale, 8, previous)) return 8;
        }
        double quarter = 12 * Math.pow(4, (50 - performance) / 50.0);
        return reaches(area, quarter, 4, previous) ? 4 : 2;
    }

    private static boolean reaches(double area,double threshold,int tier,int previous) {
        return area >= threshold * (previous >= tier ? .8 : 1);
    }
}
