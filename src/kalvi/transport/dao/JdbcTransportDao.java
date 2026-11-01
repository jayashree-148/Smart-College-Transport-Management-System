package kalvi.transport.dao;

import kalvi.transport.*;
import kalvi.transport.model.*;
import kalvi.transport.model.Driver;   // not java.sql.Driver

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * MySQL implementation (JDBC + PreparedStatement). ALL SQL for the web app lives in this class.
 * One shared connection; methods are synchronized and the connection is re-opened if MySQL dropped it.
 */
public class JdbcTransportDao implements TransportDao {
    private final DbConfig cfg;
    private Connection con;

    public JdbcTransportDao(DbConfig cfg) throws SQLException {
        this.cfg = cfg;
        con = DriverManager.getConnection(cfg.url, cfg.user, cfg.password);
        ensureUserTable();
    }

    /** Also in sql/06_users.sql; created here too so an existing database works without re-running setup. */
    private void ensureUserTable() throws SQLException {
        try (Statement st = con().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_users (user_id VARCHAR(20) PRIMARY KEY, full_name VARCHAR(60) NOT NULL, "
                     + "role ENUM('ADMIN','STAFF','VIEWER') NOT NULL DEFAULT 'VIEWER', password_hash VARCHAR(200) NOT NULL, "
                     + "failed_attempts INT NOT NULL DEFAULT 0, locked_until BIGINT NOT NULL DEFAULT 0, "
                     + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private synchronized Connection con() throws SQLException {
        if (con == null || !con.isValid(2)) con = DriverManager.getConnection(cfg.url, cfg.user, cfg.password);
        return con;
    }

    private static RuntimeException wrap(SQLException e) { return new IllegalStateException("MySQL error: " + e.getMessage(), e); }
    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    @Override public String mode() { return "MySQL (" + cfg.url.replaceAll("\\?.*", "") + ")"; }
    @Override public boolean isMySql() { return true; }

    @Override
    public synchronized Map<String, Stop> stops() {
        Map<String, Stop> m = new LinkedHashMap<>();
        String sql = "SELECT p.stop_id, p.stop_name, c.latitude, c.longitude FROM pickup_stops p "
                   + "LEFT JOIN stop_coordinates c ON c.stop_id = p.stop_id ORDER BY p.stop_id";
        try (Statement st = con().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                BigDecimalPair ll = new BigDecimalPair(rs.getBigDecimal(3), rs.getBigDecimal(4));
                m.put(rs.getString(1), new Stop(rs.getString(1), rs.getString(2), ll.lat(), ll.lng()));
            }
        } catch (SQLException e) { throw wrap(e); }
        return m;
    }

    private record BigDecimalPair(java.math.BigDecimal a, java.math.BigDecimal b) {
        double lat() { return a == null ? Double.NaN : a.doubleValue(); }
        double lng() { return b == null ? Double.NaN : b.doubleValue(); }
    }

    @Override
    public synchronized List<Route> routes() {
        Map<String, Stop> stops = stops();
        Map<String, Route> routes = new LinkedHashMap<>();
        try (Statement st = con().createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT route_id, route_name, zone, distance_km, est_minutes FROM fleet_routes ORDER BY route_id")) {
                while (rs.next())
                    routes.put(rs.getString(1), new Route(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getDouble(4), rs.getInt(5)));
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT route_id, stop_id FROM fleet_route_stops ORDER BY route_id, seq")) {
                while (rs.next()) {
                    Route r = routes.get(rs.getString(1));
                    Stop s = stops.get(rs.getString(2));
                    if (r != null && s != null) r.addStop(s);
                }
            }
        } catch (SQLException e) { throw wrap(e); }
        return new ArrayList<>(routes.values());
    }

    @Override
    public synchronized List<Vehicle> vehicles() {
        List<Vehicle> l = new ArrayList<>();
        try (Statement st = con().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fleet_buses ORDER BY bus_id")) {
            while (rs.next()) {
                java.math.BigDecimal mil = rs.getBigDecimal("mileage_kmpl");
                l.add(new Vehicle(rs.getString("bus_id"), rs.getString("reg_no"), rs.getString("bus_type"),
                        rs.getInt("capacity"), rs.getString("make_model"), rs.getInt("year_mfg"), rs.getString("fuel"),
                        rs.getString("route_id"), rs.getString("shift_name"),
                        new Driver(rs.getString("driver_name"), rs.getString("driver_phone"), rs.getInt("driver_exp_years")),
                        new Conductor(rs.getString("conductor_name"), rs.getString("conductor_phone")),
                        VehicleStatus.valueOf(rs.getString("status")), rs.getString("remarks"),
                        rs.getInt("odometer_km"), mil == null ? null : mil.doubleValue(),
                        date(rs, "last_service"), date(rs, "next_service"), date(rs, "insurance_expiry"),
                        date(rs, "fitness_expiry"), date(rs, "puc_expiry"),
                        rs.getString("gps_device"), rs.getInt("fill_pct")));
            }
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    @Override
    public synchronized List<Student> students() {
        List<Student> l = new ArrayList<>();
        try (Statement st = con().createStatement();
             ResultSet rs = st.executeQuery("SELECT student_id, name, stop_id, authorized FROM students ORDER BY student_id")) {
            while (rs.next()) l.add(new Student(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    @Override
    public synchronized Map<String, FeeRecord> fees() {
        Map<String, FeeRecord> m = new LinkedHashMap<>();
        try (Statement st = con().createStatement();
             ResultSet rs = st.executeQuery("SELECT student_id, amount_due, amount_paid FROM fee_records ORDER BY student_id")) {
            while (rs.next()) m.put(rs.getString(1), new FeeRecord(rs.getString(1), rs.getInt(2), rs.getInt(3)));
        } catch (SQLException e) { throw wrap(e); }
        return m;
    }

    @Override
    public synchronized Set<String> presentOn(LocalDate d) {
        Set<String> s = new HashSet<>();
        try (PreparedStatement ps = con().prepareStatement(
                "SELECT student_id FROM attendance WHERE att_date = ? AND status = 'PRESENT'")) {
            ps.setDate(1, java.sql.Date.valueOf(d));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) s.add(rs.getString(1)); }
        } catch (SQLException e) { throw wrap(e); }
        return s;
    }

    @Override
    public synchronized LocalDate latestAttendanceDate() {
        try (Statement st = con().createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(att_date) FROM attendance")) {
            if (rs.next() && rs.getDate(1) != null) return rs.getDate(1).toLocalDate();
        } catch (SQLException e) { throw wrap(e); }
        return LocalDate.now();
    }

    @Override
    public synchronized List<CollegeBus> plannerBuses() {
        List<CollegeBus> l = new ArrayList<>();
        try (Statement st = con().createStatement();
             ResultSet rs = st.executeQuery("SELECT bus_id, bus_type, capacity, status FROM buses ORDER BY bus_id")) {
            while (rs.next()) {
                CollegeBus b = rs.getString(2).equals("MINI")
                        ? new MiniBus(rs.getString(1), rs.getInt(3)) : new StandardBus(rs.getString(1), rs.getInt(3));
                b.setAvailable(rs.getString(4).equals("AVAILABLE"));
                l.add(b);
            }
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    @Override
    public synchronized Graph graph() {
        Graph g = new Graph();
        try (Statement st = con().createStatement();
             ResultSet rs = st.executeQuery("SELECT from_stop, to_stop, minutes, narrow FROM road_links")) {
            while (rs.next()) g.addRoad(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4));
        } catch (SQLException e) { throw wrap(e); }
        return g;
    }

    @Override
    public synchronized List<String[]> events(int limit) {
        List<String[]> l = new ArrayList<>();
        try (PreparedStatement ps = con().prepareStatement(
                "SELECT event_id, event_time, event_type, details FROM event_log ORDER BY event_id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) l.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
            }
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    @Override
    public synchronized List<TripRow> savedTrips(LocalDate d) {
        List<TripRow> l = new ArrayList<>();
        try (PreparedStatement ps = con().prepareStatement(
                "SELECT trip_id, trip_date, bus_id, route_text, total_minutes, trip_status "
              + "FROM daily_trips WHERE trip_date = ? ORDER BY trip_id")) {
            ps.setDate(1, java.sql.Date.valueOf(d));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) l.add(new TripRow(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6)));
            }
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    // ---------------- writes ----------------
    @Override
    public synchronized void setVehicleStatus(String busId, VehicleStatus status) {
        try {
            Connection c = con();
            c.setAutoCommit(false);
            try (PreparedStatement a = c.prepareStatement("UPDATE fleet_buses SET status = ? WHERE bus_id = ?");
                 PreparedStatement b = c.prepareStatement("UPDATE buses SET status = ? WHERE bus_id = ?")) {
                a.setString(1, status.name()); a.setString(2, busId); a.executeUpdate();
                // BUS-01..03 are also planner buses: keep both tables in step so the planner re-plans
                b.setString(1, status == VehicleStatus.MAINTENANCE ? "BROKEN_DOWN" : "AVAILABLE");
                b.setString(2, busId); b.executeUpdate();
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw wrap(e); }
        log(status == VehicleStatus.MAINTENANCE ? "BREAKDOWN" : "BUS", busId + " status set to " + status);
    }

    @Override
    public synchronized void addStudent(String id, String name, String stopId, int feeDue) {
        try {
            con().setAutoCommit(false);
            try (PreparedStatement student = con().prepareStatement(
                    "INSERT INTO students (student_id, name, stop_id, authorized) VALUES (?,?,?,1)");
                 PreparedStatement fee = con().prepareStatement(
                    "INSERT INTO fee_records (student_id, amount_due, amount_paid) VALUES (?,?,0)")) {
                student.setString(1, id); student.setString(2, name); student.setString(3, stopId);
                student.executeUpdate();
                fee.setString(1, id); fee.setInt(2, feeDue);
                fee.executeUpdate();
                con().commit();
            } catch (SQLException e) {
                con().rollback();
                throw e;
            } finally {
                con().setAutoCommit(true);
            }
            log("STUDENT", "Added " + id + " " + name + " at " + stopId);
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public synchronized void setStudentAuthorized(String studentId, boolean authorized) {
        try (PreparedStatement ps = con().prepareStatement(
                "UPDATE students SET authorized = ? WHERE student_id = ?")) {
            ps.setBoolean(1, authorized); ps.setString(2, studentId); ps.executeUpdate();
            log("AUTHORIZATION", studentId + (authorized ? " authorized" : " not authorized") + " for transport");
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public synchronized void addFeePayment(String studentId, int amount) {
        try (PreparedStatement ps = con().prepareStatement(
                "UPDATE fee_records SET amount_paid = LEAST(amount_due, amount_paid + ?) WHERE student_id = ?")) {
            ps.setInt(1, amount); ps.setString(2, studentId); ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
        log("FEE", studentId + " paid " + amount);
    }

    @Override
    public synchronized void markAttendance(String studentId, LocalDate d, boolean present) {
        try (PreparedStatement ps = con().prepareStatement(
                "INSERT INTO attendance (student_id, att_date, status) VALUES (?,?,?) "
              + "ON DUPLICATE KEY UPDATE status = VALUES(status)")) {
            ps.setString(1, studentId); ps.setDate(2, java.sql.Date.valueOf(d));
            ps.setString(3, present ? "PRESENT" : "ABSENT"); ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
        log("ATTENDANCE", studentId + " marked " + (present ? "PRESENT" : "ABSENT") + " on " + d);
    }

    @Override
    public synchronized void saveTrips(LocalDate d, List<DailyTrip> trips) {
        try {
            Connection c = con();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM daily_trips WHERE trip_date = ?")) {
                    del.setDate(1, java.sql.Date.valueOf(d)); del.executeUpdate();     // cascades to trip_assignments
                }
                for (DailyTrip t : trips) {
                    int tripId;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO daily_trips (trip_date, bus_id, route_text, total_minutes, trip_status) VALUES (?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setDate(1, java.sql.Date.valueOf(d));
                        ps.setString(2, t.getBus().getId());
                        ps.setString(3, String.join(" > ", t.fullRoute()));
                        ps.setInt(4, t.getTotalMinutes());
                        ps.setString(5, t.getStatus());
                        ps.executeUpdate();
                        try (ResultSet k = ps.getGeneratedKeys()) { k.next(); tripId = k.getInt(1); }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO trip_assignments (trip_id, student_id, stop_id, board_order) VALUES (?,?,?,?)")) {
                        int order = 1;
                        for (DailyTrip.Pickup p : t.getPickups())
                            for (Student s : p.getBoarded()) {
                                ps.setInt(1, tripId); ps.setString(2, s.getId());
                                ps.setString(3, p.getStopId()); ps.setInt(4, order++);
                                ps.addBatch();
                            }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw wrap(e); }
        log("PICKUP", "Plan saved for " + d + " (" + trips.size() + " trips)");
    }

    @Override
    public synchronized void log(String type, String details) {
        try (PreparedStatement ps = con().prepareStatement("INSERT INTO event_log (event_type, details) VALUES (?,?)")) {
            ps.setString(1, type); ps.setString(2, details); ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public synchronized void close() {
        try { if (con != null) con.close(); } catch (SQLException ignored) { }
    }

    // ---------------------------------------------------------------- login accounts
    private static UserRecord user(ResultSet rs) throws SQLException {
        return new UserRecord(rs.getString("user_id"), rs.getString("full_name"), rs.getString("role"), rs.getString("password_hash"),
                rs.getInt("failed_attempts"), rs.getLong("locked_until"), String.valueOf(rs.getTimestamp("created_at")).replaceAll("\\.0$", ""));
    }

    @Override
    public synchronized UserRecord findUser(String userId) {
        try (PreparedStatement ps = con().prepareStatement("SELECT * FROM app_users WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? user(rs) : null; }
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public synchronized List<UserRecord> users() {
        List<UserRecord> l = new ArrayList<>();
        try (Statement st = con().createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM app_users ORDER BY user_id")) {
            while (rs.next()) l.add(user(rs));
        } catch (SQLException e) { throw wrap(e); }
        return l;
    }

    @Override
    public synchronized void saveUser(UserRecord u) {
        String sql = "INSERT INTO app_users (user_id, full_name, role, password_hash, failed_attempts, locked_until) VALUES (?,?,?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE full_name=VALUES(full_name), role=VALUES(role), password_hash=VALUES(password_hash), "
                   + "failed_attempts=VALUES(failed_attempts), locked_until=VALUES(locked_until)";
        try (PreparedStatement ps = con().prepareStatement(sql)) {
            ps.setString(1, u.userId()); ps.setString(2, u.fullName()); ps.setString(3, u.role());
            ps.setString(4, u.passwordHash()); ps.setInt(5, u.failedAttempts()); ps.setLong(6, u.lockedUntil());
            ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public synchronized void deleteUser(String userId) {
        try (PreparedStatement ps = con().prepareStatement("DELETE FROM app_users WHERE user_id = ?")) {
            ps.setString(1, userId); ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }
}
