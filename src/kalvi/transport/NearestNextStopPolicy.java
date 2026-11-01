package kalvi.transport;

import java.util.Collection;

/** Greedy heuristic: go to the nearest stop that still has waiting students (ties: smaller stop id). */
public class NearestNextStopPolicy implements PickupPlanningPolicy {
    @Override
    public String nextStop(Graph.ShortestPaths fromHere, Collection<String> candidateStops) {
        String best = null;
        for (String s : candidateStops) {
            if (!fromHere.reachable(s)) continue;
            if (best == null
                    || fromHere.distance(s) < fromHere.distance(best)
                    || (fromHere.distance(s) == fromHere.distance(best) && s.compareTo(best) < 0)) {
                best = s;
            }
        }
        return best;
    }
}
