package kalvi.transport;

import java.util.Collection;

/** Abstraction: how the next stop is chosen. */
public interface PickupPlanningPolicy {
    /** @return the chosen stop id, or null if none of the candidates is reachable. */
    String nextStop(Graph.ShortestPaths fromHere, Collection<String> candidateStops);
}
