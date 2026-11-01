package kalvi.transport;

/** Full-size bus: cannot enter narrow roads. */
public class StandardBus extends CollegeBus {
    public StandardBus(String id, int capacity) { super(id, capacity); }

    @Override public boolean canUseRoad(Graph.Edge road) { return !road.isNarrow(); }
    @Override public String type() { return "STANDARD"; }
}
