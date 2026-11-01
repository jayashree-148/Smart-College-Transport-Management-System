package kalvi.transport;

/** Abstract bus. Subclasses decide which roads they may use (polymorphism). */
public abstract class CollegeBus {
    private final String id;
    private final int capacity;
    private boolean available = true;

    protected CollegeBus(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public abstract boolean canUseRoad(Graph.Edge road);
    public abstract String type();

    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
