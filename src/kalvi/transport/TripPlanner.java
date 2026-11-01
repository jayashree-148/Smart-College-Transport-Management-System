package kalvi.transport;

import java.util.*;

/** Builds capacity-feasible pickup trips and handles breakdown reassignment. */
public class TripPlanner {
    public static final String DEPOT = "CAMPUS";

    private final Graph graph;
    private final PickupPlanningPolicy policy;
    private final Map<String, String> stopNames;
    private final Map<String, ArrayDeque<Student>> waiting = new TreeMap<>();   // FIFO queue per stop
    private final List<DailyTrip> trips = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();                      // absent / unauthorized notes
    private final List<String> emptyStops = new ArrayList<>();

    public TripPlanner(Graph graph, PickupPlanningPolicy policy, Map<String, String> stopNames) {
        this.graph = graph;
        this.policy = policy;
        this.stopNames = stopNames;
    }

    /** Only present AND authorized students wait at their stop. Fee status is NOT used here. */
    public void loadWaiting(Collection<Student> students, Set<String> presentIds) {
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort(Comparator.comparing(Student::getId));
        for (Student s : sorted) {
            if (!s.isAuthorized()) { skipped.add(s + " - not authorized for transport"); continue; }
            if (!presentIds.contains(s.getId())) { skipped.add(s + " - absent"); continue; }
            waiting.computeIfAbsent(s.getStopId(), k -> new ArrayDeque<>()).addLast(s);
        }
        for (String node : new TreeSet<>(graph.nodes()))
            if (!node.equals(DEPOT) && !waiting.containsKey(node)) emptyStops.add(node);
    }

    public void plan(List<CollegeBus> buses) {
        for (CollegeBus bus : buses) {
            if (!bus.isAvailable()) continue;
            DailyTrip t = new DailyTrip(bus, DEPOT);
            trips.add(t);
            extend(t);
            finish(t);
        }
    }

    private void extend(DailyTrip t) {
        while (t.seatsLeft() > 0 && !waiting.isEmpty()) {
            Graph.ShortestPaths sp = graph.shortestPaths(t.position(), t.getBus());
            String next = policy.nextStop(sp, waiting.keySet());
            if (next == null) break;                                  // nothing reachable for this bus
            t.travelTo(sp.pathTo(next), sp.distance(next), next);
            ArrayDeque<Student> q = waiting.get(next);
            while (t.seatsLeft() > 0 && !q.isEmpty()) t.boardStudent(q.pollFirst());
            if (q.isEmpty()) waiting.remove(next);
        }
    }

    private void finish(DailyTrip t) {
        Graph.ShortestPaths back = graph.shortestPaths(t.position(), t.getBus());
        if (!t.position().equals(DEPOT)) t.returnToCampus(back.pathTo(DEPOT), back.distance(DEPOT));
        t.completeTrip();
    }

    /**
     * Simulates a breakdown after 'afterStops' pickups. Already-boarded students stay with the broken bus;
     * only waiting students go back to their stop queues and are re-planned on the other buses.
     */
    public String breakdown(String busId, int afterStops) {
        DailyTrip broken = null;
        for (DailyTrip t : trips) if (t.getBus().getId().equals(busId)) broken = t;
        if (broken == null) return "No active trip for " + busId;
        if (broken.getStatus().equals("BROKEN_DOWN")) return busId + " has already broken down";
        broken.getBus().setAvailable(false);
        Map<String, List<Student>> cancelled = broken.breakDownAfter(afterStops);
        int returned = 0;
        for (Map.Entry<String, List<Student>> e : cancelled.entrySet()) {
            List<Student> list = e.getValue();
            ArrayDeque<Student> q = waiting.computeIfAbsent(e.getKey(), k -> new ArrayDeque<>());
            for (int i = list.size() - 1; i >= 0; i--) q.addFirst(list.get(i));   // keep FIFO order
            returned += list.size();
        }
        for (DailyTrip t : trips) {
            if (t == broken) continue;
            t.reopen();
            extend(t);
            finish(t);
        }
        return busId + " broke down after " + broken.getPickups().size() + " pickup(s); "
                + broken.getStudents().size() + " boarded student(s) kept, "
                + returned + " waiting student(s) sent back for reassignment.";
    }

    public List<String> getSkipped() { return Collections.unmodifiableList(skipped); }
    public List<String> getEmptyStops() { return Collections.unmodifiableList(emptyStops); }

    public List<DailyTrip> getTrips() { return Collections.unmodifiableList(trips); }

    public List<Student> unallocated() {
        List<Student> out = new ArrayList<>();
        for (ArrayDeque<Student> q : waiting.values()) out.addAll(q);
        out.sort(Comparator.comparing(Student::getId));
        return out;
    }

    public String reasonFor(Student s) {
        for (DailyTrip t : trips) {
            if (!t.getBus().isAvailable()) continue;
            if (graph.shortestPaths(DEPOT, t.getBus()).reachable(s.getStopId()))
                return "all reachable buses are full";
        }
        return "stop not reachable by any available bus";
    }

    private String name(String stopId) { return stopId + " " + stopNames.getOrDefault(stopId, ""); }

    public String report() {
        StringBuilder sb = new StringBuilder();
        for (DailyTrip t : trips) {
            CollegeBus b = t.getBus();
            sb.append(String.format("%s [%s, %d seats]  status=%s  seats used %d/%d  time %d min%n",
                    b.getId(), b.type(), b.getCapacity(), t.getStatus(), t.getStudents().size(),
                    b.getCapacity(), t.getTotalMinutes()));
            sb.append("  Route: ").append(String.join(" -> ", t.fullRoute())).append('\n');
            for (DailyTrip.Pickup p : t.getPickups()) {
                sb.append("  ").append(name(p.getStopId())).append(": ");
                List<String> ids = new ArrayList<>();
                for (Student s : p.getBoarded()) ids.add(s.getId());
                sb.append(ids).append('\n');
            }
            sb.append('\n');
        }
        List<Student> un = unallocated();
        sb.append("UNALLOCATED STUDENTS: ").append(un.size()).append('\n');
        for (Student s : un) sb.append("  ").append(s).append(" at ").append(s.getStopId())
                .append(" - ").append(reasonFor(s)).append('\n');
        sb.append("\nEMPTY STOPS SKIPPED: ").append(emptyStops).append('\n');
        sb.append("NOT SERVED (").append(skipped.size()).append("):\n");
        for (String s : skipped) sb.append("  ").append(s).append('\n');
        return sb.toString();
    }
}
