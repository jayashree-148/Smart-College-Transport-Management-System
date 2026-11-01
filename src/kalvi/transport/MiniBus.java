package kalvi.transport;

/** Small bus: may use narrow roads as well as normal roads. */
public class MiniBus extends CollegeBus {
    public MiniBus(String id, int capacity) { super(id, capacity); }

    @Override public boolean canUseRoad(Graph.Edge road) { return true; }
    @Override public String type() { return "MINI"; }
}
