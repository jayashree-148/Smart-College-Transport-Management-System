package kalvi.transport.service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import kalvi.transport.*;
import kalvi.transport.dao.TransportDao;
import kalvi.transport.model.*;
import static kalvi.transport.web.Json.obj;

/**
 * Business logic. Talks to the database ONLY through the TransportDao interface and builds the in-memory
 * data structures (HashMap index, TreeMap groups, PriorityQueue alerts, Graph + Dijkstra, FIFO queues).
 * Every public method returns plain Maps/Lists that Json can print.
 */
public class TransportService {
    private final TransportDao dao;
    private final long startedAt = System.currentTimeMillis();
    private final double simSpeed = Double.parseDouble(System.getProperty("sim.speed", "1"));
    private final Map<String, BusSimulator> simulators = new HashMap<>();

    public TransportService(TransportDao dao) { this.dao = dao; }

    /** Called after the database was switched: forget the cached bus simulators. */
    public synchronized void reset() { simulators.clear(); }

    private LocalDate today() { return LocalDate.now(); }

    // =====================================================================================
    //  planner (Dijkstra + nearest-next-stop + FIFO queues): classes from the original project
    // =====================================================================================
    private TripPlanner buildPlan(String breakdownBus, int afterStops) {
        Map<String, Stop> stops = dao.stops();
        Map<String, String> names = new HashMap<>();
        stops.forEach((k, v) -> names.put(k, v.name()));
        TripPlanner planner = new TripPlanner(dao.graph(), new NearestNextStopPolicy(), names);
        planner.loadWaiting(dao.students(), dao.presentOn(dao.latestAttendanceDate()));
        planner.plan(dao.plannerBuses());
        if (breakdownBus != null && !breakdownBus.isBlank()) planner.breakdown(breakdownBus, afterStops);
        return planner;
    }

    public Map<String, Object> plan(String breakdownBus, int afterStops, boolean save) {
        TripPlanner planner = buildPlan(breakdownBus, afterStops);
        Map<String, Stop> stops = dao.stops();
        List<Object> trips = new ArrayList<>();
        int no = 1;
        for (DailyTrip t : planner.getTrips()) {
            List<Object> pickups = new ArrayList<>();
            for (DailyTrip.Pickup p : t.getPickups()) {
                List<Object> studs = new ArrayList<>();
                for (Student s : p.getBoarded()) studs.add(obj("id", s.getId(), "name", s.getName()));
                Stop st = stops.get(p.getStopId());
                pickups.add(obj("stopId", p.getStopId(), "stopName", st == null ? "" : st.name(),
                        "minutes", p.getMinutes(), "students", studs));
            }
            List<String> route = t.fullRoute();
            trips.add(obj("tripNo", no++, "busId", t.getBus().getId(), "busType", t.getBus().type(),
                    "capacity", t.getBus().getCapacity(), "status", t.getStatus(),
                    "seatsUsed", t.getStudents().size(), "totalMinutes", t.getTotalMinutes(),
                    "route", route, "routeNames", route.stream().map(id -> stops.get(id) == null ? id : stops.get(id).name()).toList(),
                    "pickups", pickups));
        }
        List<Object> un = new ArrayList<>();
        for (Student s : planner.unallocated())
            un.add(obj("id", s.getId(), "name", s.getName(), "stopId", s.getStopId(), "reason", planner.reasonFor(s)));
        String message = "";
        if (breakdownBus != null && !breakdownBus.isBlank()) message = breakdownBus + " breakdown simulated after " + afterStops + " pickup(s)";
        if (save) dao.saveTrips(dao.latestAttendanceDate(), planner.getTrips());
        return obj("date", dao.latestAttendanceDate(), "trips", trips, "unallocated", un,
                "skipped", planner.getSkipped(), "emptyStops", planner.getEmptyStops(),
                "message", message, "saved", save,
                "algorithm", "Dijkstra shortest path (min-heap PriorityQueue) + nearest-next-stop greedy policy, FIFO queue per stop");
    }

    // =====================================================================================
    //  dashboard
    // =====================================================================================
    public Map<String, Object> dashboard() {
        List<Vehicle> vehicles = dao.vehicles();
        List<Route> routes = dao.routes();
        List<Student> students = dao.students();
        Map<String, FeeRecord> fees = dao.fees();
        Map<String, Stop> stops = dao.stops();
        TripPlanner planner = buildPlan(null, 0);

        // fleet status: EnumMap counter
        EnumMap<VehicleStatus, Integer> counts = new EnumMap<>(VehicleStatus.class);
        for (VehicleStatus s : VehicleStatus.values()) counts.put(s, 0);
        for (Vehicle v : vehicles) counts.merge(v.getStatus(), 1, Integer::sum);

        // students per stop: HashMap counter
        Map<String, Integer> studentsAtStop = new HashMap<>();
        for (Student s : students) studentsAtStop.merge(s.getStopId(), 1, Integer::sum);
        Map<String, Integer> busesOnRoute = new HashMap<>();
        for (Vehicle v : vehicles) if (v.getRouteId() != null) busesOnRoute.merge(v.getRouteId(), 1, Integer::sum);

        // route overview: the planner's trips drawn on the map
        List<Object> overview = new ArrayList<>();
        for (DailyTrip t : planner.getTrips()) {
            List<Object> pts = new ArrayList<>();
            for (String id : t.fullRoute()) {
                Stop s = stops.get(id);
                if (s != null) pts.add(obj("id", id, "name", s.name(), "lat", s.lat(), "lng", s.lng()));
            }
            overview.add(obj("busId", t.getBus().getId(), "status", t.getStatus(), "points", pts));
        }
        Stop depot = stops.get(TripPlanner.DEPOT);

        List<Object> routeList = new ArrayList<>();
        for (Route r : routes) {
            int studs = 0;
            for (Stop s : r.getStops()) studs += studentsAtStop.getOrDefault(s.id(), 0);
            routeList.add(obj("id", r.getId(), "name", r.getName(), "zone", r.getZone(), "stops", r.getStops().size(),
                    "km", r.getDistanceKm(), "buses", busesOnRoute.getOrDefault(r.getId(), 0), "students", studs));
        }

        // recent trips (stack-like: newest first via ArrayDeque.push)
        Deque<Object> recent = new ArrayDeque<>();
        int no = 1;
        for (DailyTrip t : planner.getTrips()) {
            String label = t.getStudents().isEmpty() ? "Idle" : switch (t.getStatus()) {
                case "COMPLETED" -> "Completed"; case "BROKEN_DOWN" -> "Breakdown"; default -> "Planned"; };
            recent.push(obj("trip", "TRIP-" + no++, "busId", t.getBus().getId(), "students", t.getStudents().size(),
                    "minutes", t.getTotalMinutes(), "status", label));
        }
        List<Object> recentTrips = new ArrayList<>(recent);
        Collections.reverse(recentTrips);            // TRIP-1 first, as in the design

        List<Alert> alerts = alerts();
        List<Object> topAlerts = alerts.stream().limit(4).map(this::alertJson).collect(Collectors.toList());

        return obj(
            "kpis", obj("routes", routes.size(), "vehicles", vehicles.size(), "students", students.size(),
                        "collected", feeTotals(fees).get("collected")),
            "overview", obj("depot", depot == null ? null : obj("lat", depot.lat(), "lng", depot.lng(), "name", depot.name()),
                            "trips", overview),
            "fleetStatus", obj("total", vehicles.size(), "inTransit", counts.get(VehicleStatus.ACTIVE),
                               "idle", counts.get(VehicleStatus.SPARE), "broken", counts.get(VehicleStatus.MAINTENANCE)),
            "routeList", routeList,
            "fees", feeTotals(fees),
            "recentTrips", recentTrips,
            "alerts", topAlerts, "alertCount", alerts.size(),
            "date", dao.latestAttendanceDate());
    }

    private Map<String, Object> feeTotals(Map<String, FeeRecord> fees) {
        long due = 0, paid = 0;
        int paidN = 0, partial = 0, pending = 0;
        for (FeeRecord f : fees.values()) {
            due += f.getAmountDue(); paid += f.getAmountPaid();
            switch (f.status()) { case "PAID" -> paidN++; case "PARTIAL" -> partial++; default -> pending++; }
        }
        return obj("due", due, "collected", paid, "pending", due - paid,
                   "percent", due == 0 ? 0 : Math.round(paid * 1000.0 / due) / 10.0,
                   "paidCount", paidN, "partialCount", partial, "pendingCount", pending);
    }

    // =====================================================================================
    //  alerts: PriorityQueue<Alert> pops the most urgent first
    // =====================================================================================
    public List<Alert> alerts() {
        LocalDate today = today();
        PriorityQueue<Alert> pq = new PriorityQueue<>();
        for (Vehicle v : dao.vehicles()) {
            String who = v.getId() + " (" + v.getRegNo() + ")";
            if (v.getStatus() == VehicleStatus.MAINTENANCE)
                pq.add(new Alert(Alert.Severity.WARNING, "WORKSHOP", who + " is in the workshop", v.getRemarks(), null));
            checkDoc(pq, today, who, "Insurance", v.getInsuranceExpiry());
            checkDoc(pq, today, who, "Fitness certificate", v.getFitnessExpiry());
            checkDoc(pq, today, who, "Pollution (PUC) certificate", v.getPucExpiry());
            long svc = Vehicle.daysUntil(v.getNextService(), today);
            if (svc < 0) pq.add(new Alert(Alert.Severity.WARNING, "SERVICE", who + " service overdue",
                    "Was due " + v.getNextService() + " (" + (-svc) + " days ago)", v.getNextService()));
            else if (svc <= 14) pq.add(new Alert(Alert.Severity.INFO, "SERVICE", who + " service due soon",
                    "Due " + v.getNextService() + " (in " + svc + " days)", v.getNextService()));
        }
        for (String[] e : dao.events(200))
            if (e[2].equals("BREAKDOWN"))
                pq.add(new Alert(Alert.Severity.WARNING, "BREAKDOWN", e[3], "Logged " + e[1], parseDate(e[1])));
        long pending = dao.fees().values().stream().filter(f -> f.status().equals("PENDING")).count();
        if (pending > 0) pq.add(new Alert(Alert.Severity.INFO, "FEE", "Fee dues pending",
                pending + " student(s) have not paid anything yet", null));

        List<Alert> ordered = new ArrayList<>();
        while (!pq.isEmpty()) ordered.add(pq.poll());          // heap order = urgency order
        return ordered;
    }

    private static LocalDate parseDate(String ts) {
        try { return LocalDate.parse(ts.substring(0, 10)); } catch (RuntimeException e) { return null; }
    }

    private void checkDoc(PriorityQueue<Alert> pq, LocalDate today, String who, String doc, LocalDate expiry) {
        long d = Vehicle.daysUntil(expiry, today);
        if (d < 0) pq.add(new Alert(Alert.Severity.CRITICAL, "DOCUMENT", who + ": " + doc + " expired",
                "Expired on " + expiry + " (" + (-d) + " days ago)", expiry));
        else if (d <= 30) pq.add(new Alert(Alert.Severity.WARNING, "DOCUMENT", who + ": " + doc + " expires soon",
                "Expires " + expiry + " (in " + d + " days)", expiry));
    }

    private Map<String, Object> alertJson(Alert a) {
        return obj("severity", a.getSeverity(), "kind", a.getKind(), "title", a.getTitle(),
                   "detail", a.getDetail(), "date", a.getDate());
    }

    public Map<String, Object> alertsPage(String severity) {
        List<Object> rows = new ArrayList<>();
        int crit = 0, warn = 0, info = 0;
        for (Alert a : alerts()) {
            switch (a.getSeverity()) { case CRITICAL -> crit++; case WARNING -> warn++; default -> info++; }
            if (severity == null || severity.isBlank() || a.getSeverity().name().equalsIgnoreCase(severity)) rows.add(alertJson(a));
        }
        return obj("critical", crit, "warning", warn, "info", info, "alerts", rows);
    }

    // =====================================================================================
    //  vehicles (HashMap index, sorted list + binary search, filtering, paging)
    // =====================================================================================
    private Map<String, Object> vehicleRow(Vehicle v, Map<String, Route> routes) {
        Route r = v.getRouteId() == null ? null : routes.get(v.getRouteId());
        return obj("id", v.getId(), "regNo", v.getRegNo(), "type", v.getType(), "capacity", v.getCapacity(),
                "makeModel", v.getMakeModel(), "year", v.getYear(), "fuel", v.getFuel(),
                "routeId", v.getRouteId(), "routeName", r == null ? "Java planner (live)" : r.getName(),
                "shift", v.getShift(), "status", v.getStatus(), "statusLabel", v.getStatus().label(),
                "driver", v.getDriver().getName(), "driverPhone", v.getDriver().getPhone(),
                "conductor", v.getConductor().getName(), "odometerKm", v.getOdometerKm(), "fillPct", v.getFillPct());
    }

    private static <T> Map<String, T> byId(Collection<T> items, java.util.function.Function<T, String> key) {
        Map<String, T> m = new LinkedHashMap<>();
        for (T t : items) m.put(key.apply(t), t);
        return m;
    }

    public Map<String, Object> vehicles(String q, String status, String route, String sort, String dir, int page, int size) {
        Map<String, Route> routes = byId(dao.routes(), Route::getId);
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<Vehicle> list = dao.vehicles().stream()
                .filter(v -> status == null || status.isBlank() || v.getStatus().name().equalsIgnoreCase(status))
                .filter(v -> route == null || route.isBlank() || route.equals(v.getRouteId()))
                .filter(v -> needle.isEmpty() || (v.getId() + " " + v.getRegNo() + " " + v.getMakeModel() + " " + v.getDriver().getName()
                        + " " + v.getConductor().getName() + " " + v.getFuel()).toLowerCase().contains(needle))
                .collect(Collectors.toCollection(ArrayList::new));
        Comparator<Vehicle> cmp = switch (sort == null ? "id" : sort) {
            case "regNo" -> Comparator.comparing(Vehicle::getRegNo);
            case "capacity" -> Comparator.comparingInt(Vehicle::getCapacity);
            case "year" -> Comparator.comparingInt(Vehicle::getYear);
            case "odometer" -> Comparator.comparingInt(Vehicle::getOdometerKm);
            case "driver" -> Comparator.comparing(v -> v.getDriver().getName());
            case "status" -> Comparator.comparing(Vehicle::getStatus);
            default -> Comparator.comparing(Vehicle::getId);
        };
        list.sort("desc".equals(dir) ? cmp.reversed() : cmp);
        int total = list.size();
        int from = Math.min(Math.max(page, 0) * size, total), to = Math.min(from + size, total);
        List<Object> rows = new ArrayList<>();
        for (Vehicle v : list.subList(from, to)) rows.add(vehicleRow(v, routes));
        return obj("total", total, "page", page, "size", size, "rows", rows);
    }

    public Map<String, Object> vehicle(String idOrReg) {
        List<Vehicle> all = dao.vehicles();
        Map<String, Vehicle> index = byId(all, Vehicle::getId);                  // HashMap lookup O(1)
        Vehicle v = index.get(idOrReg.toUpperCase());
        if (v == null) {                                                          // binary search on registration number
            List<Vehicle> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparing(Vehicle::getRegNo));
            int i = Collections.binarySearch(sorted, null, (a, b) -> {
                String x = a == null ? idOrReg : a.getRegNo(), y = b == null ? idOrReg : b.getRegNo();
                return x.compareToIgnoreCase(y);
            });
            if (i >= 0) v = sorted.get(i);
        }
        if (v == null) throw new NoSuchElementException("No vehicle " + idOrReg);

        Map<String, Route> routes = byId(dao.routes(), Route::getId);
        Route r = v.getRouteId() == null ? null : routes.get(v.getRouteId());
        LocalDate today = today();
        List<Object> docs = new ArrayList<>();
        docs.add(docJson("Insurance", v.getInsuranceExpiry(), today));
        docs.add(docJson("Fitness certificate", v.getFitnessExpiry(), today));
        docs.add(docJson("Pollution (PUC)", v.getPucExpiry(), today));
        List<Object> stops = new ArrayList<>();
        if (r != null) for (Stop s : r.getStops()) stops.add(obj("id", s.id(), "name", s.name(), "lat", s.lat(), "lng", s.lng()));
        Map<String, Object> m = vehicleRow(v, routes);
        m.putAll(obj("mileage", v.getMileage(), "remarks", v.getRemarks(), "gpsDevice", v.getGpsDevice(),
                "driverExp", v.getDriver().getExperienceYears(), "conductorPhone", v.getConductor().getPhone(),
                "lastService", v.getLastService(), "nextService", v.getNextService(),
                "serviceDays", Vehicle.daysUntil(v.getNextService(), today), "documents", docs,
                "routeStops", stops, "routeKm", r == null ? null : r.getDistanceKm(),
                "routeMinutes", r == null ? null : r.getEstMinutes()));
        return m;
    }

    private Map<String, Object> docJson(String name, LocalDate expiry, LocalDate today) {
        long d = Vehicle.daysUntil(expiry, today);
        return obj("name", name, "expiry", expiry, "daysLeft", d, "state", d < 0 ? "EXPIRED" : d <= 30 ? "DUE_SOON" : "VALID");
    }

    public Map<String, Object> setVehicleStatus(String id, String value) {
        VehicleStatus s;
        try { s = VehicleStatus.valueOf(value.toUpperCase()); }
        catch (RuntimeException e) { throw new IllegalArgumentException("status must be ACTIVE, MAINTENANCE or SPARE"); }
        if (dao.vehicles().stream().noneMatch(v -> v.getId().equals(id))) throw new NoSuchElementException("No vehicle " + id);
        dao.setVehicleStatus(id, s);
        return obj("ok", true, "id", id, "status", s);
    }

    // =====================================================================================
    //  routes / map / crew / students / fees
    // =====================================================================================
    public Map<String, Object> routes() { return obj("routes", routeRows()); }

    private List<Map<String, Object>> routeRows() {
        List<Vehicle> vehicles = dao.vehicles();
        Map<String, List<Vehicle>> byRoute = new TreeMap<>();                        // TreeMap = routes sorted by id
        for (Vehicle v : vehicles) if (v.getRouteId() != null) byRoute.computeIfAbsent(v.getRouteId(), k -> new ArrayList<>()).add(v);
        Map<String, Integer> studentsAtStop = new HashMap<>();
        for (Student s : dao.students()) studentsAtStop.merge(s.getStopId(), 1, Integer::sum);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Route r : dao.routes()) {
            List<Vehicle> vs = byRoute.getOrDefault(r.getId(), List.of());
            int seats = vs.stream().mapToInt(Vehicle::getCapacity).sum();
            List<Object> stops = new ArrayList<>();
            int studs = 0;
            for (Stop s : r.getStops()) {
                studs += studentsAtStop.getOrDefault(s.id(), 0);
                stops.add(obj("id", s.id(), "name", s.name(), "lat", s.lat(), "lng", s.lng()));
            }
            rows.add(obj("id", r.getId(), "name", r.getName(), "zone", r.getZone(), "km", r.getDistanceKm(),
                    "minutes", r.getEstMinutes(), "stops", stops, "buses", vs.stream().map(Vehicle::getId).toList(),
                    "busCount", vs.size(), "seats", seats, "students", studs));
        }
        return rows;
    }

    public Map<String, Object> map() {
        Map<String, Stop> stops = dao.stops();
        Stop depot = stops.get(TripPlanner.DEPOT);
        List<Object> all = new ArrayList<>();
        for (Stop s : stops.values())
            if (!Double.isNaN(s.lat())) all.add(obj("id", s.id(), "name", s.name(), "lat", s.lat(), "lng", s.lng()));
        return obj("depot", depot == null ? null : obj("lat", depot.lat(), "lng", depot.lng(), "name", depot.name()),
                   "stops", all, "routes", routeRows());
    }

    public Map<String, Object> crew(String role) {
        List<Object> rows = new ArrayList<>();
        for (Vehicle v : dao.vehicles()) {
            Person p = "Conductor".equalsIgnoreCase(role) ? v.getConductor() : v.getDriver();     // polymorphism: Person
            rows.add(obj("name", p.getName(), "phone", p.getPhone(), "role", p.role(),
                    "experience", p instanceof Driver d ? d.getExperienceYears() : null,
                    "busId", v.getId(), "regNo", v.getRegNo(), "shift", v.getShift(), "status", v.getStatus(),
                    "statusLabel", v.getStatus().label(), "routeId", v.getRouteId()));
        }
        return obj("role", "Conductor".equalsIgnoreCase(role) ? "Conductor" : "Driver", "total", rows.size(), "rows", rows);
    }

    public Map<String, Object> students() {
        LocalDate d = dao.latestAttendanceDate();
        Set<String> present = dao.presentOn(d);
        Map<String, Stop> stops = dao.stops();
        Map<String, FeeRecord> fees = dao.fees();
        List<Object> rows = new ArrayList<>();
        for (Student s : dao.students()) {
            FeeRecord f = fees.get(s.getId());
            Stop st = stops.get(s.getStopId());
            rows.add(obj("id", s.getId(), "name", s.getName(), "stopId", s.getStopId(), "stopName", st == null ? "" : st.name(),
                    "authorized", s.isAuthorized(), "present", present.contains(s.getId()),
                    "feeDue", f == null ? 0 : f.getAmountDue(), "feePaid", f == null ? 0 : f.getAmountPaid(),
                    "feeStatus", f == null ? "NONE" : f.status()));
        }
        return obj("date", d, "total", rows.size(), "rows", rows);
    }

    public Map<String, Object> markAttendance(String studentId, boolean present) {
        if (dao.students().stream().noneMatch(s -> s.getId().equals(studentId))) throw new NoSuchElementException("No student " + studentId);
        dao.markAttendance(studentId, dao.latestAttendanceDate(), present);
        return obj("ok", true);
    }

    public Map<String, Object> fees() {
        Map<String, FeeRecord> fees = dao.fees();
        Map<String, Student> students = byId(dao.students(), Student::getId);
        List<Object> rows = new ArrayList<>();
        for (FeeRecord f : fees.values()) {
            Student s = students.get(f.getStudentId());
            rows.add(obj("studentId", f.getStudentId(), "name", s == null ? "" : s.getName(), "due", f.getAmountDue(),
                    "paid", f.getAmountPaid(), "balance", f.getAmountDue() - f.getAmountPaid(), "status", f.status()));
        }
        return obj("summary", feeTotals(fees), "rows", rows);
    }

    public Map<String, Object> payFee(String studentId, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (!dao.fees().containsKey(studentId)) throw new NoSuchElementException("No fee record for " + studentId);
        dao.addFeePayment(studentId, amount);
        return obj("ok", true);
    }

    // =====================================================================================
    //  live tracking (simulated GPS)
    // =====================================================================================
    public Map<String, Object> tracking() {
        long now = System.currentTimeMillis();
        Map<String, Stop> stops = dao.stops();
        Stop depot = stops.get(TripPlanner.DEPOT);
        Map<String, Route> routes = byId(dao.routes(), Route::getId);
        Map<String, DailyTrip> tripByBus = new HashMap<>();
        TripPlanner planner = null;
        List<Object> buses = new ArrayList<>();
        int moving = 0;
        for (Vehicle v : dao.vehicles()) {
            if (v.getStatus() != VehicleStatus.ACTIVE) continue;
            BusSimulator sim = simulators.get(v.getId());
            if (sim == null || v.getRouteId() == null) {
                List<Stop> path = new ArrayList<>();
                double km; int minutes;
                if (v.getRouteId() != null && routes.containsKey(v.getRouteId())) {
                    Route r = routes.get(v.getRouteId());
                    path.addAll(r.getStops());
                    km = r.getDistanceKm(); minutes = r.getEstMinutes();
                } else {                                                      // BUS-01..03: use the planner's route
                    if (planner == null) {
                        planner = buildPlan(null, 0);
                        for (DailyTrip t : planner.getTrips()) tripByBus.put(t.getBus().getId(), t);
                    }
                    DailyTrip t = tripByBus.get(v.getId());
                    if (t == null) continue;
                    for (String id : t.fullRoute()) if (stops.get(id) != null) path.add(stops.get(id));
                    km = Math.max(5, path.size() * 2.2); minutes = Math.max(20, t.getTotalMinutes());
                }
                if (depot != null && !path.isEmpty() && !path.get(path.size() - 1).id().equals(depot.id())) path.add(depot);
                path.removeIf(s -> Double.isNaN(s.lat()));
                if (path.size() < 2) continue;
                sim = new BusSimulator(v.getId(), path, km, minutes);
                simulators.put(v.getId(), sim);
            }
            BusSimulator.Position p = sim.at(now, simSpeed);
            moving++;
            buses.add(obj("id", v.getId(), "regNo", v.getRegNo(), "routeId", v.getRouteId(), "driver", v.getDriver().getName(),
                    "lat", p.lat(), "lng", p.lng(), "kmh", p.kmh(), "nextStop", p.nextStop().name(), "etaMin", p.etaMinutes(),
                    "fillPct", v.getFillPct(), "capacity", v.getCapacity(), "progress", Math.round(p.progress() * 1000) / 10.0));
        }
        return obj("time", now, "speed", simSpeed, "moving", moving, "buses", buses,
                   "note", "Simulated GPS: each bus loops along its route. A real GPS feed would replace BusSimulator.");
    }

    // =====================================================================================
    //  reports
    // =====================================================================================
    public Map<String, Object> reports() {
        List<Vehicle> vs = dao.vehicles();
        Map<String, Integer> fuel = new TreeMap<>(), type = new TreeMap<>(), shift = new TreeMap<>(), status = new TreeMap<>();
        int expired = 0, soon = 0;
        LocalDate today = today();
        for (Vehicle v : vs) {
            fuel.merge(v.getFuel(), 1, Integer::sum);
            type.merge(v.getType(), 1, Integer::sum);
            shift.merge(v.getShift(), 1, Integer::sum);
            status.merge(v.getStatus().label(), 1, Integer::sum);
            for (LocalDate d : List.of(v.getInsuranceExpiry(), v.getFitnessExpiry(), v.getPucExpiry())) {
                long days = Vehicle.daysUntil(d, today);
                if (days < 0) expired++; else if (days <= 30) soon++;
            }
        }
        List<Object> load = new ArrayList<>();
        List<Map<String, Object>> sorted = new ArrayList<>(routeRows());
        sorted.sort((a, b) -> Integer.compare((Integer) b.get("students"), (Integer) a.get("students")));
        for (Map<String, Object> r : sorted)
            load.add(obj("id", r.get("id"), "name", r.get("name"), "buses", r.get("busCount"),
                    "seats", r.get("seats"), "students", r.get("students")));
        return obj("fleetByFuel", fuel, "fleetByType", type, "fleetByShift", shift, "fleetByStatus", status,
                "documentsExpired", expired, "documentsDueSoon", soon, "fees", feeTotals(dao.fees()),
                "routeLoad", load, "totalVehicles", vs.size(),
                "totalSeats", vs.stream().mapToInt(Vehicle::getCapacity).sum());
    }

    public Map<String, Object> events() {
        List<Object> rows = new ArrayList<>();
        for (String[] e : dao.events(100)) rows.add(obj("id", e[0], "time", e[1], "type", e[2], "details", e[3]));
        return obj("events", rows);
    }

    // =====================================================================================
    //  system info (shown on the Settings page; also explains the OOP / data structures)
    // =====================================================================================
    public boolean isMySql() { return dao.isMySql(); }

    public Map<String, Object> system() {
        List<Object> ds = List.of(
            obj("structure", "HashMap", "where", "TransportService.vehicle(): bus id -> Vehicle index; stops/fees/students lookups", "why", "O(1) lookup by key"),
            obj("structure", "Graph (adjacency list: Map<String,List<Edge>>)", "where", "Graph.java - campus + stops + road_links table", "why", "roads between stops with travel minutes"),
            obj("structure", "PriorityQueue (min-heap)", "where", "Graph.shortestPaths() (Dijkstra) and TransportService.alerts()", "why", "always pop the nearest node / most urgent alert first"),
            obj("structure", "ArrayDeque as FIFO queue", "where", "TripPlanner.waiting: one queue of waiting students per stop", "why", "students board in arrival order"),
            obj("structure", "LinkedList", "where", "Route.stops and Graph.ShortestPaths.pathTo()", "why", "ordered chain of stops, cheap insert at the front/end"),
            obj("structure", "TreeMap / sorted list + binary search", "where", "routes grouped by id; vehicle lookup by registration number", "why", "sorted order and O(log n) search"),
            obj("structure", "EnumMap / HashSet", "where", "fleet status counters; present-student ids", "why", "fast counters and membership tests"),
            obj("structure", "ArrayDeque as stack", "where", "dashboard 'Recent Trips' (push newest)", "why", "LIFO order"));
        List<Object> oop = List.of(
            obj("concept", "Abstraction + inheritance", "example", "CollegeBus -> StandardBus / MiniBus; Person -> Driver / Conductor"),
            obj("concept", "Polymorphism", "example", "CollegeBus.canUseRoad() differs per bus type; Person.role(); PickupPlanningPolicy"),
            obj("concept", "Encapsulation", "example", "private fields + getters in Vehicle, Route, Student, FeeRecord"),
            obj("concept", "Interfaces / dependency inversion", "example", "TransportDao (JdbcTransportDao for MySQL, DemoTransportDao for files); PickupPlanningPolicy"),
            obj("concept", "Composition", "example", "Vehicle has-a Driver and Conductor; Route has-many Stop; DailyTrip has-many Student"),
            obj("concept", "Layered architecture", "example", "web (HTTP) -> service (logic) -> dao (JDBC/SQL) -> MySQL"));
        return obj("dataSource", dao.mode(), "mysql", dao.isMySql(),
                "java", System.getProperty("java.version"), "uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000,
                "counts", obj("stops", dao.stops().size() - 1, "routes", dao.routes().size(), "vehicles", dao.vehicles().size(),
                        "students", dao.students().size()),
                "dataStructures", ds, "oop", oop,
                "endpoints", List.of("GET /api/dashboard", "GET /api/vehicles", "GET /api/vehicles/{id}", "POST /api/vehicles/{id}/status",
                        "GET /api/routes", "GET /api/map", "GET /api/crew?role=Driver|Conductor", "GET /api/students",
                        "GET /api/fees", "POST /api/fees/pay", "GET /api/tracking", "GET /api/alerts", "GET /api/reports",
                        "GET /api/plan", "POST /api/plan", "GET /api/events", "GET /api/system"));
    }
}
