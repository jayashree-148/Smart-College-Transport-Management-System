package kalvi.transport.dao;

import kalvi.transport.*;
import kalvi.transport.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fallback used ONLY when MySQL is unreachable (or --demo is given): reads the same data from data/demo/*.tsv
 * (exported from the sql/*.sql files) and keeps changes in memory. Same interface, so the web app cannot tell.
 */
public class DemoTransportDao implements TransportDao {
    private final Path dir;
    private final Map<String, List<Map<String, String>>> tables = new HashMap<>();
    private final List<String[]> events = new ArrayList<>();
    private final List<TripRow> trips = new ArrayList<>();
    private final Map<String, VehicleStatus> statusOverride = new HashMap<>();
    private final Map<String, Boolean> plannerAvailable = new HashMap<>();
    private int nextEventId = 1000, nextTripId = 1;

    public DemoTransportDao(Path dir) throws IOException {
        this.dir = dir;
        for (String t : List.of("pickup_stops", "stop_coordinates", "fleet_routes", "fleet_route_stops", "fleet_buses",
                "students", "fee_records", "attendance", "buses", "road_links", "event_log"))
            tables.put(t, read(t));
        for (Map<String, String> r : tables.get("event_log"))
            events.add(new String[]{String.valueOf(nextEventId++), r.get("event_time"), r.get("event_type"), r.get("details")});
        for (Map<String, String> r : tables.get("fleet_buses")) statusOverride.put(r.get("bus_id"), VehicleStatus.valueOf(r.get("status")));
        for (Map<String, String> r : tables.get("buses")) plannerAvailable.put(r.get("bus_id"), r.get("status").equals("AVAILABLE"));
    }

    /** Tiny TSV reader: first line = column names, "\N" = NULL. */
    private List<Map<String, String>> read(String table) throws IOException {
        List<String> lines = Files.readAllLines(dir.resolve(table + ".tsv"), StandardCharsets.UTF_8);
        String[] cols = lines.get(0).split("\t", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] v = lines.get(i).split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < cols.length; c++) row.put(cols[c], v[c].equals("\\N") ? null : v[c]);
            rows.add(row);
        }
        return rows;
    }

    private static LocalDate d(String s) { return s == null ? null : LocalDate.parse(s); }

    @Override public String mode() { return "DEMO FILES (data/demo) - MySQL not connected"; }
    @Override public boolean isMySql() { return false; }

    @Override
    public Map<String, Stop> stops() {
        Map<String, double[]> coords = new HashMap<>();
        for (Map<String, String> r : tables.get("stop_coordinates"))
            coords.put(r.get("stop_id"), new double[]{Double.parseDouble(r.get("latitude")), Double.parseDouble(r.get("longitude"))});
        List<Map<String, String>> rows = new ArrayList<>(tables.get("pickup_stops"));
        rows.sort(Comparator.comparing(r -> r.get("stop_id")));
        Map<String, Stop> m = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            double[] ll = coords.getOrDefault(r.get("stop_id"), new double[]{Double.NaN, Double.NaN});
            m.put(r.get("stop_id"), new Stop(r.get("stop_id"), r.get("stop_name"), ll[0], ll[1]));
        }
        return m;
    }

    @Override
    public List<Route> routes() {
        Map<String, Stop> stops = stops();
        Map<String, Route> routes = new LinkedHashMap<>();
        for (Map<String, String> r : tables.get("fleet_routes"))
            routes.put(r.get("route_id"), new Route(r.get("route_id"), r.get("route_name"), r.get("zone"),
                    Double.parseDouble(r.get("distance_km")), Integer.parseInt(r.get("est_minutes"))));
        List<Map<String, String>> rs = new ArrayList<>(tables.get("fleet_route_stops"));
        rs.sort(Comparator.comparing((Map<String, String> r) -> r.get("route_id"))
                .thenComparingInt(r -> Integer.parseInt(r.get("seq"))));
        for (Map<String, String> r : rs) {
            Route route = routes.get(r.get("route_id"));
            Stop s = stops.get(r.get("stop_id"));
            if (route != null && s != null) route.addStop(s);
        }
        return new ArrayList<>(routes.values());
    }

    @Override
    public List<Vehicle> vehicles() {
        List<Vehicle> l = new ArrayList<>();
        for (Map<String, String> r : tables.get("fleet_buses")) {
            String id = r.get("bus_id");
            l.add(new Vehicle(id, r.get("reg_no"), r.get("bus_type"), Integer.parseInt(r.get("capacity")),
                    r.get("make_model"), Integer.parseInt(r.get("year_mfg")), r.get("fuel"), r.get("route_id"),
                    r.get("shift_name"),
                    new Driver(r.get("driver_name"), r.get("driver_phone"), Integer.parseInt(r.get("driver_exp_years"))),
                    new Conductor(r.get("conductor_name"), r.get("conductor_phone")),
                    statusOverride.get(id), r.get("remarks"), Integer.parseInt(r.get("odometer_km")),
                    r.get("mileage_kmpl") == null ? null : Double.parseDouble(r.get("mileage_kmpl")),
                    d(r.get("last_service")), d(r.get("next_service")), d(r.get("insurance_expiry")),
                    d(r.get("fitness_expiry")), d(r.get("puc_expiry")), r.get("gps_device"),
                    Integer.parseInt(r.get("fill_pct"))));
        }
        l.sort(Comparator.comparing(Vehicle::getId));
        return l;
    }

    private final Map<String, Boolean> authOverride = new HashMap<>();
    private final Map<String, Map<String, String>> addedStudents = new LinkedHashMap<>();
    private final Map<String, Integer> addedFees = new HashMap<>();

    @Override
    public List<Student> students() {
        List<Student> l = new ArrayList<>();
        for (Map<String, String> r : tables.get("students"))
            l.add(new Student(r.get("student_id"), r.get("name"), r.get("stop_id"),
                    authOverride.getOrDefault(r.get("student_id"), r.get("authorized").equals("1"))));
        for (Map<String, String> r : addedStudents.values())
            l.add(new Student(r.get("student_id"), r.get("name"), r.get("stop_id"),
                    authOverride.getOrDefault(r.get("student_id"), true)));
        l.sort(Comparator.comparing(Student::getId));
        return l;
    }

    private final Map<String, Integer> paidOverride = new HashMap<>();

    @Override
    public Map<String, FeeRecord> fees() {
        Map<String, FeeRecord> m = new TreeMap<>();
        for (Map<String, String> r : tables.get("fee_records")) {
            String id = r.get("student_id");
            m.put(id, new FeeRecord(id, Integer.parseInt(r.get("amount_due")),
                    paidOverride.getOrDefault(id, Integer.valueOf(r.get("amount_paid")))));
        }
        for (Map.Entry<String, Integer> e : addedFees.entrySet())
            m.put(e.getKey(), new FeeRecord(e.getKey(), e.getValue(), paidOverride.getOrDefault(e.getKey(), 0)));
        return m;
    }

    private final Map<String, Boolean> attendanceOverride = new HashMap<>();   // key date|student

    @Override
    public Set<String> presentOn(LocalDate day) {
        Set<String> s = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, String> r : tables.get("attendance")) {
            String key = r.get("att_date") + "|" + r.get("student_id");
            seen.add(key);
            boolean present = attendanceOverride.getOrDefault(key, r.get("status").equals("PRESENT"));
            if (r.get("att_date").equals(day.toString()) && present) s.add(r.get("student_id"));
        }
        for (Map.Entry<String, Boolean> e : attendanceOverride.entrySet())
            if (e.getKey().startsWith(day + "|") && e.getValue() && !seen.contains(e.getKey())) s.add(e.getKey().split("\\|")[1]);
        return s;
    }

    @Override
    public LocalDate latestAttendanceDate() {
        LocalDate best = null;
        for (Map<String, String> r : tables.get("attendance")) {
            LocalDate x = LocalDate.parse(r.get("att_date"));
            if (best == null || x.isAfter(best)) best = x;
        }
        return best == null ? LocalDate.now() : best;
    }

    @Override
    public List<CollegeBus> plannerBuses() {
        List<CollegeBus> l = new ArrayList<>();
        for (Map<String, String> r : tables.get("buses")) {
            int cap = Integer.parseInt(r.get("capacity"));
            CollegeBus b = r.get("bus_type").equals("MINI") ? new MiniBus(r.get("bus_id"), cap) : new StandardBus(r.get("bus_id"), cap);
            b.setAvailable(plannerAvailable.get(r.get("bus_id")));
            l.add(b);
        }
        l.sort(Comparator.comparing(CollegeBus::getId));
        return l;
    }

    @Override
    public Graph graph() {
        Graph g = new Graph();
        for (Map<String, String> r : tables.get("road_links"))
            g.addRoad(r.get("from_stop"), r.get("to_stop"), Integer.parseInt(r.get("minutes")), r.get("narrow").equals("1"));
        return g;
    }

    @Override
    public synchronized List<String[]> events(int limit) {
        List<String[]> copy = new ArrayList<>(events);
        Collections.reverse(copy);
        return copy.size() > limit ? new ArrayList<>(copy.subList(0, limit)) : copy;
    }

    @Override
    public synchronized List<TripRow> savedTrips(LocalDate day) {
        List<TripRow> l = new ArrayList<>();
        for (TripRow t : trips) if (t.date().equals(day.toString())) l.add(t);
        return l;
    }

    @Override
    public synchronized void setVehicleStatus(String busId, VehicleStatus status) {
        statusOverride.put(busId, status);
        if (plannerAvailable.containsKey(busId)) plannerAvailable.put(busId, status != VehicleStatus.MAINTENANCE);
        log(status == VehicleStatus.MAINTENANCE ? "BREAKDOWN" : "BUS", busId + " status set to " + status);
    }

    @Override
    public synchronized void addStudent(String id, String name, String stopId, int feeDue) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("student_id", id); row.put("name", name); row.put("stop_id", stopId); row.put("authorized", "1");
        addedStudents.put(id, row);
        addedFees.put(id, feeDue);
        log("STUDENT", "Added " + id + " " + name + " at " + stopId);
    }

    @Override
    public synchronized void setStudentAuthorized(String studentId, boolean authorized) {
        authOverride.put(studentId, authorized);
        log("AUTHORIZATION", studentId + (authorized ? " authorized" : " not authorized") + " for transport");
    }

    @Override
    public synchronized void addFeePayment(String studentId, int amount) {
        FeeRecord f = fees().get(studentId);
        if (f != null) paidOverride.put(studentId, Math.min(f.getAmountDue(), f.getAmountPaid() + amount));
        log("FEE", studentId + " paid " + amount);
    }

    @Override
    public synchronized void markAttendance(String studentId, LocalDate day, boolean present) {
        attendanceOverride.put(day + "|" + studentId, present);
        log("ATTENDANCE", studentId + " marked " + (present ? "PRESENT" : "ABSENT") + " on " + day);
    }

    @Override
    public synchronized void saveTrips(LocalDate day, List<DailyTrip> list) {
        trips.removeIf(t -> t.date().equals(day.toString()));
        for (DailyTrip t : list)
            trips.add(new TripRow(nextTripId++, day.toString(), t.getBus().getId(), String.join(" > ", t.fullRoute()),
                    t.getTotalMinutes(), t.getStatus()));
        log("PICKUP", "Plan saved for " + day + " (" + list.size() + " trips)");
    }

    @Override
    public synchronized void log(String type, String details) {
        events.add(new String[]{String.valueOf(nextEventId++), LocalDateTime.now().withNano(0).toString().replace('T', ' '), type, details});
    }

    @Override public void close() { }

    // ---------------------------------------------------------------- login accounts (kept in data/demo/users.tsv so they survive a restart)
    private final Map<String, UserRecord> userMap = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean usersLoaded;

    private synchronized void loadUsers() {
        if (usersLoaded) return;
        usersLoaded = true;
        Path f = dir.resolve("users.tsv");
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] v = line.split("\t", -1);
                if (v.length < 7) continue;
                userMap.put(v[0], new UserRecord(v[0], v[1], v[2], v[3], Integer.parseInt(v[4]), Long.parseLong(v[5]), v[6]));
            }
        } catch (IOException | RuntimeException e) { System.err.println("[auth] could not read users.tsv: " + e.getMessage()); }
    }

    private void storeUsers() {
        List<String> out = new ArrayList<>();
        out.add("user_id\tfull_name\trole\tpassword_hash\tfailed_attempts\tlocked_until\tcreated_at");
        for (UserRecord u : userMap.values())
            out.add(String.join("\t", u.userId(), u.fullName().replace('\t', ' '), u.role(), u.passwordHash(),
                    String.valueOf(u.failedAttempts()), String.valueOf(u.lockedUntil()), u.createdAt()));
        try { Files.write(dir.resolve("users.tsv"), out, StandardCharsets.UTF_8); }
        catch (IOException e) { System.err.println("[auth] could not save users.tsv: " + e.getMessage()); }
    }

    @Override public synchronized UserRecord findUser(String userId) { loadUsers(); return userMap.get(userId); }
    @Override public synchronized List<UserRecord> users() { loadUsers(); return new ArrayList<>(userMap.values()); }
    @Override public synchronized void saveUser(UserRecord u) { loadUsers(); userMap.put(u.userId(), u); storeUsers(); }
    @Override public synchronized void deleteUser(String userId) { loadUsers(); userMap.remove(userId); storeUsers(); }
}
