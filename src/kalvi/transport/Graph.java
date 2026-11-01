package kalvi.transport;

import java.util.*;

/** Weighted undirected graph (adjacency lists) of campus + pickup stops, with own Dijkstra. */
public class Graph {

    public static final class Edge {
        private final String from, to;
        private final int minutes;
        private final boolean narrow;

        Edge(String from, String to, int minutes, boolean narrow) {
            this.from = from; this.to = to; this.minutes = minutes; this.narrow = narrow;
        }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public int getMinutes() { return minutes; }
        public boolean isNarrow() { return narrow; }
    }

    /** Result of one Dijkstra run from a source, for one bus. */
    public static final class ShortestPaths {
        private final String source;
        private final Map<String, Integer> dist;
        private final Map<String, String> prev;

        ShortestPaths(String source, Map<String, Integer> dist, Map<String, String> prev) {
            this.source = source; this.dist = dist; this.prev = prev;
        }
        public boolean reachable(String node) { return dist.containsKey(node); }
        public int distance(String node) { return dist.get(node); }

        /** Nodes to travel through, excluding the source, ending at 'node'. */
        public List<String> pathTo(String node) {
            LinkedList<String> path = new LinkedList<>();
            for (String n = node; !n.equals(source); n = prev.get(n)) path.addFirst(n);
            return path;
        }
    }

    private record Entry(String node, int dist) {}

    private final Map<String, List<Edge>> adj = new HashMap<>();

    public void addRoad(String a, String b, int minutes, boolean narrow) {
        if (minutes <= 0) throw new IllegalArgumentException("Travel time must be positive");
        adj.computeIfAbsent(a, k -> new ArrayList<>()).add(new Edge(a, b, minutes, narrow));
        adj.computeIfAbsent(b, k -> new ArrayList<>()).add(new Edge(b, a, minutes, narrow));
    }

    public Set<String> nodes() { return adj.keySet(); }

    /** Dijkstra + min-heap; only roads the bus may use are relaxed. Ties broken by stop id. */
    public ShortestPaths shortestPaths(String source, CollegeBus bus) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<Entry> heap = new PriorityQueue<>(
                Comparator.comparingInt(Entry::dist).thenComparing(Entry::node));
        dist.put(source, 0);
        heap.add(new Entry(source, 0));
        while (!heap.isEmpty()) {
            Entry cur = heap.poll();
            if (cur.dist() > dist.get(cur.node())) continue;   // stale record, already improved
            for (Edge e : adj.getOrDefault(cur.node(), List.of())) {
                if (!bus.canUseRoad(e)) continue;
                int nd = cur.dist() + e.getMinutes();
                Integer old = dist.get(e.getTo());
                if (old == null || nd < old) {
                    dist.put(e.getTo(), nd);
                    prev.put(e.getTo(), cur.node());
                    heap.add(new Entry(e.getTo(), nd));         // re-insert with new ranking
                }
            }
        }
        return new ShortestPaths(source, dist, prev);
    }
}
