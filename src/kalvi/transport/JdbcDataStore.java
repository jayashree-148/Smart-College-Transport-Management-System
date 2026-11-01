package kalvi.transport;

import java.time.LocalDate;
import java.util.*;
import kalvi.transport.dao.TransportDao;
import kalvi.transport.model.Stop;
import kalvi.transport.model.VehicleStatus;

/**
 * Compatibility facade for the Swing client.
 *
 * <p>The desktop UI predates the service/DAO layer and uses this domain-specific
 * API. Keeping the facade lets the UI share exactly the same MySQL and demo DAO
 * implementations as the web application.</p>
 */
public final class JdbcDataStore implements AutoCloseable {
    private final TransportDao dao;

    public JdbcDataStore(TransportDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public Map<String, String> stopNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Stop stop : dao.stops().values()) names.put(stop.id(), stop.name());
        return names;
    }

    public List<Student> students() { return dao.students(); }
    public Set<String> presentOn(LocalDate date) { return dao.presentOn(date); }
    public LocalDate latestAttendanceDate() { return dao.latestAttendanceDate(); }
    public List<CollegeBus> buses() { return dao.plannerBuses(); }
    public Graph graph() { return dao.graph(); }
    public Map<String, FeeRecord> fees() { return dao.fees(); }
    public List<String[]> events() { return dao.events(200); }

    public void markAttendance(String studentId, LocalDate date, boolean present) {
        dao.markAttendance(studentId, date, present);
    }

    public void addStudent(String id, String name, String stopId, int feeDue) {
        dao.addStudent(id, name, stopId, feeDue);
    }

    public void setAuthorized(String studentId, boolean authorized) {
        dao.setStudentAuthorized(studentId, authorized);
    }

    public void setBusAvailable(String busId, boolean available) {
        dao.setVehicleStatus(busId, available ? VehicleStatus.ACTIVE : VehicleStatus.MAINTENANCE);
    }

    public void addFeePayment(String studentId, int amount) {
        dao.addFeePayment(studentId, amount);
    }

    public void saveTrips(LocalDate date, List<DailyTrip> trips) {
        dao.saveTrips(date, trips);
    }

    @Override public void close() { dao.close(); }
}
