package kalvi.transport.dao;

import kalvi.transport.*;
import kalvi.transport.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-access contract. The service layer only talks to this interface
 * (dependency inversion): JdbcTransportDao = MySQL, DemoTransportDao = local files.
 */
public interface TransportDao extends AutoCloseable {
    /** Human readable data source, e.g. "MySQL college_transport_db". */
    String mode();
    boolean isMySql();

    // ---------- reads ----------
    Map<String, Stop> stops();                       // stop_id -> Stop (includes CAMPUS depot), insertion ordered
    List<Route> routes();
    List<Vehicle> vehicles();
    List<Student> students();
    Map<String, FeeRecord> fees();
    Set<String> presentOn(LocalDate d);
    LocalDate latestAttendanceDate();
    List<CollegeBus> plannerBuses();                 // BUS-01..03 used by the Java route planner
    Graph graph();                                   // road_links -> weighted graph
    List<String[]> events(int limit);                // {id, time, type, details} newest first
    List<TripRow> savedTrips(LocalDate d);

    // ---------- writes ----------
    void setVehicleStatus(String busId, VehicleStatus status);
    void addStudent(String id, String name, String stopId, int feeDue);
    void setStudentAuthorized(String studentId, boolean authorized);
    void addFeePayment(String studentId, int amount);
    void markAttendance(String studentId, LocalDate d, boolean present);
    void saveTrips(LocalDate d, List<DailyTrip> trips);
    void log(String type, String details);

    // ---------- login accounts (table app_users) ----------
    UserRecord findUser(String userId);              // null if unknown
    List<UserRecord> users();
    void saveUser(UserRecord u);                     // insert or update
    void deleteUser(String userId);

    @Override void close();
}
