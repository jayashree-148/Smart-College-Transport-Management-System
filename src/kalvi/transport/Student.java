package kalvi.transport;

/** A student who may use the college transport service. */
public class Student {
    private final String id;
    private final String name;
    private final String stopId;
    private boolean authorized;

    public Student(String id, String name, String stopId, boolean authorized) {
        this.id = id;
        this.name = name;
        this.stopId = stopId;
        this.authorized = authorized;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStopId() { return stopId; }
    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean authorized) { this.authorized = authorized; }

    @Override
    public String toString() { return id + " " + name; }
}
