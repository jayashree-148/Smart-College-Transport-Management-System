package kalvi.transport.service;

import java.util.ArrayList;
import java.util.List;
import kalvi.transport.model.Stop;

/**
 * Simulated GPS for one bus: it drives its route (stops ... campus) in a loop.
 * Simulated clock: 1 real second = {@code speed} trip-minutes. Real GPS pings could replace this later.
 */
public class BusSimulator {
    /** Where the bus is right now. */
    public record Position(double lat, double lng, int nextIndex, Stop nextStop, double kmh, int etaMinutes, double progress) {}

    private final List<Stop> path;           // waypoints (>= 2)
    private final double[] cum;              // cumulative planar length at each waypoint
    private final double totalKm;
    private final int loopMinutes;
    private final double offset;             // 0..1, so buses are spread along the route

    public BusSimulator(String busId, List<Stop> path, double distanceKm, int loopMinutes) {
        this.path = new ArrayList<>(path);
        this.cum = new double[path.size()];
        for (int i = 1; i < path.size(); i++) cum[i] = cum[i - 1] + seg(path.get(i - 1), path.get(i));
        this.totalKm = distanceKm;
        this.loopMinutes = Math.max(loopMinutes, 10);
        this.offset = (Math.abs(busId.hashCode()) % 1000) / 1000.0;
    }

    private static double seg(Stop a, Stop b) { return Math.hypot(b.lat() - a.lat(), b.lng() - a.lng()); }

    public Position at(long nowMillis, double speed) {
        double loopSeconds = loopMinutes / speed;                        // real seconds for one loop
        double t = ((nowMillis / 1000.0) / loopSeconds + offset) % 1.0;
        double target = t * cum[cum.length - 1];
        int i = 1;
        while (i < cum.length - 1 && cum[i] < target) i++;
        double segLen = cum[i] - cum[i - 1];
        double f = segLen == 0 ? 0 : (target - cum[i - 1]) / segLen;
        Stop a = path.get(i - 1), b = path.get(i);
        double lat = a.lat() + (b.lat() - a.lat()) * f, lng = a.lng() + (b.lng() - a.lng()) * f;
        double toNext = ((cum[i] - target) / cum[cum.length - 1]) * loopMinutes;
        double kmh = totalKm / (loopMinutes / 60.0);
        return new Position(lat, lng, i, b, Math.round(kmh * 10) / 10.0, (int) Math.ceil(toNext), t);
    }
}
