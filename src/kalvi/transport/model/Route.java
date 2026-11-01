package kalvi.transport.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** A bus route: an ordered chain of stops (LinkedList keeps pickup order). */
public class Route {
    private final String id;
    private final String name;
    private final String zone;
    private final double distanceKm;
    private final int estMinutes;
    private final LinkedList<Stop> stops = new LinkedList<>();

    public Route(String id, String name, String zone, double distanceKm, int estMinutes) {
        this.id = id; this.name = name; this.zone = zone;
        this.distanceKm = distanceKm; this.estMinutes = estMinutes;
    }

    public void addStop(Stop s) { stops.addLast(s); }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getZone() { return zone; }
    public double getDistanceKm() { return distanceKm; }
    public int getEstMinutes() { return estMinutes; }
    public List<Stop> getStops() { return Collections.unmodifiableList(stops); }
    public Stop first() { return stops.peekFirst(); }
    public Stop last() { return stops.peekLast(); }
}
