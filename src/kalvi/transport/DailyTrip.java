package kalvi.transport;

import java.util.*;

/** One bus's trip for one day: composition of assigned students and an ordered route. */
public class DailyTrip {

    /** A single pickup visit. */
    public static final class Pickup {
        private final String stopId;
        private final List<Student> boarded = new ArrayList<>();
        private final int routeLength;
        private final int minutes;

        Pickup(String stopId, int routeLength, int minutes) {
            this.stopId = stopId; this.routeLength = routeLength; this.minutes = minutes;
        }
        public String getStopId() { return stopId; }
        public int getMinutes() { return minutes; }
        public List<Student> getBoarded() { return Collections.unmodifiableList(boarded); }
    }

    private final CollegeBus bus;
    private final String depot;
    private final List<Student> students = new ArrayList<>();
    private final Set<String> studentIds = new HashSet<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<String> route = new ArrayList<>();
    private List<String> returnLeg = new ArrayList<>();
    private int outMinutes;
    private int returnMinutes;
    private String status = "PLANNED";

    public DailyTrip(CollegeBus bus, String depot) {
        this.bus = bus;
        this.depot = depot;
        route.add(depot);
    }

    public int seatsLeft() { return bus.getCapacity() - students.size(); }

    /** Adds a student unless the bus is full, the trip is closed, or the student is already on it. */
    public boolean assignStudent(Student s) {
        if (status.equals("BROKEN_DOWN") || status.equals("COMPLETED")) return false;
        if (seatsLeft() <= 0) return false;                 // no overbooking
        if (!studentIds.add(s.getId())) return false;       // no duplicate boarding
        students.add(s);
        return true;
    }

    /** Boards a student at the stop most recently visited. */
    public boolean boardStudent(Student s) {
        if (pickups.isEmpty() || !assignStudent(s)) return false;
        pickups.get(pickups.size() - 1).boarded.add(s);
        return true;
    }

    public void travelTo(List<String> path, int minutes, String stopId) {
        route.addAll(path);
        outMinutes += minutes;
        pickups.add(new Pickup(stopId, route.size(), outMinutes));
    }

    public void returnToCampus(List<String> path, int minutes) {
        returnLeg = new ArrayList<>(path);
        returnMinutes = minutes;
    }

    public void completeTrip() { if (!status.equals("BROKEN_DOWN")) status = "COMPLETED"; }

    /** Re-opens a finished trip so more stops can be appended (after another bus broke down). */
    public void reopen() {
        returnLeg = new ArrayList<>();
        returnMinutes = 0;
        if (status.equals("COMPLETED")) status = "PLANNED";
    }

    /**
     * Bus breaks down after 'keep' pickups. Students already boarded stay assigned to this bus;
     * the later (not yet reached) pickups are cancelled and returned for reassignment.
     */
    public Map<String, List<Student>> breakDownAfter(int keep) {
        keep = Math.max(0, Math.min(keep, pickups.size()));
        Map<String, List<Student>> cancelled = new LinkedHashMap<>();
        for (int i = pickups.size() - 1; i >= keep; i--) {
            Pickup p = pickups.remove(i);
            cancelled.put(p.stopId, new ArrayList<>(p.boarded));
            for (Student s : p.boarded) { students.remove(s); studentIds.remove(s.getId()); }
        }
        int len = keep == 0 ? 1 : pickups.get(keep - 1).routeLength;
        outMinutes = keep == 0 ? 0 : pickups.get(keep - 1).minutes;
        while (route.size() > len) route.remove(route.size() - 1);
        returnLeg = new ArrayList<>();
        returnMinutes = 0;
        status = "BROKEN_DOWN";
        return cancelled;
    }

    public String position() { return route.get(route.size() - 1); }
    public CollegeBus getBus() { return bus; }
    public List<Student> getStudents() { return Collections.unmodifiableList(students); }
    public List<Pickup> getPickups() { return Collections.unmodifiableList(pickups); }
    public int getTotalMinutes() { return outMinutes + returnMinutes; }
    public String getStatus() { return status; }

    public List<String> fullRoute() {
        List<String> all = new ArrayList<>(route);
        all.addAll(returnLeg);
        return all;
    }
    public String getDepot() { return depot; }
}
