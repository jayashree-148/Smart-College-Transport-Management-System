package kalvi.transport;

import java.util.List;

/** Console demo with built-in data (no database). Useful as a backup during the review. */
public class OfflineDemo {
    public static void main(String[] args) {
        System.out.println("=== NORMAL OPERATION (12-10-2026) ===");
        TripPlanner p = new TripPlanner(SampleData.graph(), new NearestNextStopPolicy(), SampleData.stopNames());
        List<CollegeBus> buses = SampleData.buses();
        p.loadWaiting(SampleData.students(), SampleData.presentOn1012());
        p.plan(buses);
        System.out.println(p.report());

        System.out.println("=== BREAKDOWN: BUS-02 after 1 pickup ===");
        System.out.println(p.breakdown("BUS-02", 1));
        System.out.println(p.report());
    }
}
