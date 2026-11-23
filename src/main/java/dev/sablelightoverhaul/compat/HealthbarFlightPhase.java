package dev.sablelightoverhaul.compat;

import dev.sablehealthbar.api.flight.ClientFlightFrame;
import dev.sablehealthbar.api.flight.FlightSnapshot;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** The 0.8.216 helpers are optional when loading with the previous Healthbar API. */
public final class HealthbarFlightPhase {
    private static final MethodHandle PHASE = find(FlightSnapshot.class, "phaseFifths", int.class);
    private static final MethodHandle VISIBLE = find(ClientFlightFrame.class, "visible", boolean.class, double.class);

    private HealthbarFlightPhase() {}

    private static MethodHandle find(Class<?> owner, String name, Class<?> result, Class<?>... arguments) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(result, arguments));
        } catch (NoSuchMethodException | IllegalAccessException unavailable) {
            return null;
        }
    }

    public static int firstSegment(FlightSnapshot snapshot) {
        if (snapshot.age() != 1 || PHASE == null) return 0;
        try {
            return (int)PHASE.invokeExact(snapshot);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static boolean isAutocannon(FlightSnapshot snapshot) {
        return switch (snapshot.profile().family()) {
            case "cbc-autocannon", "cbc:at-twin", "cbc:external-autocannon-ap" -> true;
            default -> false;
        };
    }

    public static boolean isDirectAutocannon(FlightSnapshot snapshot) {
        return snapshot.profile().family().equals("cbc:external-autocannon-ap");
    }

    public static boolean allowsProjectileLight(FlightSnapshot snapshot) {
        return !isDirectAutocannon(snapshot);
    }

    public static boolean visible(ClientFlightFrame frame, double partial) {
        if (frame.current().age() > 1 || VISIBLE == null) return true;
        try {
            return (boolean)VISIBLE.invokeExact(frame, partial);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
