package dev.sablelightoverhaul.client;
/**
 * The little a particle has to expose to be offered as a host.
 *
 * <p>Only one implementation exists -- CBC's cannon smoke, subclassed by a mixin -- and that is the point:
 * this is the shape an offer needs, not a general particle interface. The previous position is part of it
 * because a host reports both ends of its step and the alternative would be to invent one.
 */
public interface TrackedSmoke {
    double slo$x(); double slo$y(); double slo$z();
    double slo$px(); double slo$py(); double slo$pz();
    default boolean slo$tankControlled() { return false; }
    default boolean slo$muzzleManaged() { return false; }
    double slo$size(); double slo$heat(); boolean slo$alive();
}
