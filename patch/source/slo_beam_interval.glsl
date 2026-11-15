// Camera ray / finite forward frustum. Radius(z) = aperture + z*tanHalf.
// Q(t) = (dot(w + t*ray, axis))^2 - cosHalf^2 * length(w + t*ray)^2 >= 0.
// Clip the axial slab first: it removes the rear nappe and leaves at most one interval.
vec2 slo_beam_interval(float vd, float wd, float vw, float ww,
                       float cos2, float reach, float scene, float aperture) {
    float lo = 0.02;
    float hi = scene;
    if (vd == 0.0) {
        if (wd <= 0.0 || wd >= reach) return vec2(0.0, 0.0);
    } else {
        float mouth = -wd / vd;
        float end = (reach - wd) / vd;
        lo = max(lo, min(mouth, end));
        hi = min(hi, max(mouth, end));
    }
    if (hi <= lo) return vec2(0.0, 0.0);

    float qa = vd * vd - cos2;
    float radial = cos2 * max(aperture, 0.0);
    float slope = sqrt(max((1.0 - cos2) / cos2, 0.0));
    float qb = 2.0 * (vd * wd - cos2 * vw + radial * slope * vd);
    float qc = wd * wd - cos2 * ww + radial * (2.0 * slope * wd + max(aperture, 0.0));
    if (qa == 0.0) {
        // A ray parallel to a cone generator has a linear, rather than quadratic, boundary.
        if (qb > 0.0) lo = max(lo, -qc / qb);
        else if (qb < 0.0) hi = min(hi, -qc / qb);
        else if (qc < 0.0) return vec2(0.0, 0.0);
    } else {
        float disc = qb * qb - 4.0 * qa * qc;
        if (disc <= 0.0) {
            if (qa < 0.0) return vec2(0.0, 0.0);
        } else {
            // Avoid subtracting almost equal numbers when one intersection is near the camera.
            float root = sqrt(disc);
            float q = -0.5 * (qb + (qb >= 0.0 ? root : -root));
            float e0 = q / qa;
            float e1 = qc / q;
            float first = min(e0, e1);
            float last = max(e0, e1);
            if (qa < 0.0) {
                lo = max(lo, first);
                hi = min(hi, last);
            } else {
                // For an upward-opening quadratic Q >= 0 is OUTSIDE the two roots.
                // The forward axial slab cannot intersect both disconnected branches.
                if (min(hi, first) > lo) hi = min(hi, first);
                else lo = max(lo, last);
            }
        }
    }
    if (hi <= lo) return vec2(0.0, 0.0);
    return vec2(lo, hi);
}
