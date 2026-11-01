package kalvi.transport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Command-line bridge used by the FastAPI backend.
 * Reads a line-based request on stdin, runs the SAME planner classes as the Swing app,
 * and prints the plan as JSON on stdout.
 *
 * Input lines (fields separated by '|'):
 *   STOP|id|name   ROAD|from|to|minutes|narrow(0/1)   BUS|id|STANDARD/MINI|capacity|available(0/1)
 *   STUDENT|id|name|stopId|authorized(0/1)   PRESENT|studentId   BREAKDOWN|busId|afterStops
 */
public class ApiBridge {
    public static void main(String[] args) throws IOException {
        Map<String, String> stops = new LinkedHashMap<>();
        Graph graph = new Graph();
        List<CollegeBus> buses = new ArrayList<>();
        List<Student> students = new ArrayList<>();
        Set<String> present = new HashSet<>();
        List<String[]> breakdowns = new ArrayList<>();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank()) continue;
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "STOP" -> stops.put(f[1], f[2]);
                case "ROAD" -> graph.addRoad(f[1], f[2], Integer.parseInt(f[3]), f[4].equals("1"));
                case "BUS" -> {
                    CollegeBus b = f[2].equals("MINI") ? new MiniBus(f[1], Integer.parseInt(f[3]))
                                                       : new StandardBus(f[1], Integer.parseInt(f[3]));
                    b.setAvailable(f[4].equals("1"));
                    buses.add(b);
                }
                case "STUDENT" -> students.add(new Student(f[1], f[2], f[3], f[4].equals("1")));
                case "PRESENT" -> present.add(f[1]);
                case "BREAKDOWN" -> breakdowns.add(new String[]{f[1], f[2]});
                default -> { }
            }
        }

        TripPlanner planner = new TripPlanner(graph, new NearestNextStopPolicy(), stops);
        planner.loadWaiting(students, present);
        planner.plan(buses);
        List<String> messages = new ArrayList<>();
        for (String[] b : breakdowns) messages.add(planner.breakdown(b[0], Integer.parseInt(b[1])));

        StringBuilder j = new StringBuilder("{");
        j.append("\"trips\":[");
        boolean first = true;
        for (DailyTrip t : planner.getTrips()) {
            if (!first) j.append(','); first = false;
            CollegeBus b = t.getBus();
            j.append("{\"busId\":").append(q(b.getId()))
             .append(",\"busType\":").append(q(b.type()))
             .append(",\"capacity\":").append(b.getCapacity())
             .append(",\"status\":").append(q(t.getStatus()))
             .append(",\"seatsUsed\":").append(t.getStudents().size())
             .append(",\"totalMinutes\":").append(t.getTotalMinutes())
             .append(",\"route\":").append(strList(t.fullRoute()))
             .append(",\"pickups\":[");
            boolean fp = true;
            for (DailyTrip.Pickup p : t.getPickups()) {
                if (!fp) j.append(','); fp = false;
                j.append("{\"stopId\":").append(q(p.getStopId()))
                 .append(",\"stopName\":").append(q(stops.getOrDefault(p.getStopId(), "")))
                 .append(",\"minutes\":").append(p.getMinutes())
                 .append(",\"students\":[");
                boolean fs = true;
                for (Student s : p.getBoarded()) {
                    if (!fs) j.append(','); fs = false;
                    j.append("{\"id\":").append(q(s.getId())).append(",\"name\":").append(q(s.getName())).append('}');
                }
                j.append("]}");
            }
            j.append("]}");
        }
        j.append("],\"unallocated\":[");
        first = true;
        for (Student s : planner.unallocated()) {
            if (!first) j.append(','); first = false;
            j.append("{\"id\":").append(q(s.getId())).append(",\"name\":").append(q(s.getName()))
             .append(",\"stopId\":").append(q(s.getStopId()))
             .append(",\"reason\":").append(q(planner.reasonFor(s))).append('}');
        }
        j.append("],\"skipped\":").append(strList(planner.getSkipped()));
        j.append(",\"emptyStops\":").append(strList(planner.getEmptyStops()));
        j.append(",\"messages\":").append(strList(messages)).append('}');

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.println(j);
        out.flush();
    }

    private static String strList(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(','); sb.append(q(l.get(i))); }
        return sb.append(']').toString();
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
