package kalvi.transport;

import java.util.*;

/** Offline copy of the seed data (same values as sql/03_seed_data.sql) used by OfflineDemo. */
final class SampleData {
    private SampleData() {}

    static Map<String, String> stopNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CAMPUS", "Kalvi College Campus (Depot)");
        m.put("STOP-01", "Central Railway Station");
        m.put("STOP-02", "Egmore Bus Terminus");
        m.put("STOP-03", "Anna Nagar Roundtana");
        m.put("STOP-04", "Anna Nagar West Depot");
        m.put("STOP-05", "Koyambedu Omni Bus Stand");
        m.put("STOP-06", "Vadapalani Murugan Temple");
        m.put("STOP-07", "Guindy Kathipara Junction");
        m.put("STOP-08", "Alandur Metro Station");
        m.put("STOP-09", "St. Thomas Mount Railway Station");
        m.put("STOP-10", "Tambaram Sanatorium");
        return m;
    }

    static Graph graph() {
        Graph g = new Graph();
        g.addRoad("CAMPUS", "STOP-05", 12, false);
        g.addRoad("CAMPUS", "STOP-06", 15, false);
        g.addRoad("CAMPUS", "STOP-04", 14, false);
        g.addRoad("CAMPUS", "STOP-10", 30, false);
        g.addRoad("STOP-05", "STOP-04", 6, false);
        g.addRoad("STOP-04", "STOP-03", 5, true);
        g.addRoad("STOP-03", "STOP-05", 11, false);
        g.addRoad("STOP-03", "STOP-02", 9, false);
        g.addRoad("STOP-02", "STOP-01", 4, false);
        g.addRoad("STOP-05", "STOP-06", 7, false);
        g.addRoad("STOP-06", "STOP-07", 10, false);
        g.addRoad("STOP-07", "STOP-08", 6, false);
        g.addRoad("STOP-08", "STOP-09", 5, true);
        g.addRoad("STOP-09", "STOP-10", 14, false);
        g.addRoad("STOP-07", "STOP-02", 16, false);
        return g;
    }

    static List<Student> students() {
        List<Student> l = new ArrayList<>();
        l.add(new Student("S001", "Arun Kumar", "STOP-01", true));
        l.add(new Student("S002", "Divya Lakshmi", "STOP-01", true));
        l.add(new Student("S003", "Karthik Raja", "STOP-01", true));
        l.add(new Student("S004", "Priya Dharshini", "STOP-01", true));
        l.add(new Student("S005", "Vignesh S", "STOP-01", true));
        l.add(new Student("S006", "Meena Kumari", "STOP-01", true));
        l.add(new Student("S007", "Rahul Dev", "STOP-02", false));
        l.add(new Student("S008", "Sneha Rao", "STOP-02", true));
        l.add(new Student("S009", "Suresh Babu", "STOP-02", true));
        l.add(new Student("S010", "Anitha M", "STOP-02", true));
        l.add(new Student("S011", "Harish Kumar", "STOP-02", true));
        l.add(new Student("S012", "Keerthana R", "STOP-03", true));
        l.add(new Student("S013", "Naveen Raj", "STOP-03", true));
        l.add(new Student("S014", "Pavithra S", "STOP-03", true));
        l.add(new Student("S015", "Gokul Nath", "STOP-03", true));
        l.add(new Student("S016", "Swetha K", "STOP-03", true));
        l.add(new Student("S017", "Bharath V", "STOP-03", true));
        l.add(new Student("S018", "Nandhini P", "STOP-04", true));
        l.add(new Student("S019", "Dinesh Kumar", "STOP-04", false));
        l.add(new Student("S020", "Shalini G", "STOP-04", true));
        l.add(new Student("S021", "Manoj Kumar", "STOP-04", true));
        l.add(new Student("S022", "Janani S", "STOP-05", true));
        l.add(new Student("S023", "Praveen R", "STOP-05", true));
        l.add(new Student("S024", "Lavanya T", "STOP-05", true));
        l.add(new Student("S025", "Ashwin K", "STOP-05", true));
        l.add(new Student("S026", "Revathi N", "STOP-05", true));
        l.add(new Student("S027", "Santhosh M", "STOP-05", true));
        l.add(new Student("S028", "Deepika R", "STOP-05", true));
        l.add(new Student("S029", "Yuvaraj S", "STOP-06", true));
        l.add(new Student("S030", "Kavya Shree", "STOP-06", true));
        l.add(new Student("S031", "Mohan Raj", "STOP-06", true));
        l.add(new Student("S032", "Harini B", "STOP-06", true));
        l.add(new Student("S033", "Siva Kumar", "STOP-06", true));
        l.add(new Student("S034", "Preethi A", "STOP-07", true));
        l.add(new Student("S035", "Balaji N", "STOP-07", true));
        l.add(new Student("S036", "Aishwarya V", "STOP-07", true));
        l.add(new Student("S037", "Ganesh P", "STOP-07", true));
        l.add(new Student("S038", "Monisha S", "STOP-07", true));
        l.add(new Student("S039", "Tamilselvan K", "STOP-08", true));
        l.add(new Student("S040", "Abirami D", "STOP-08", true));
        l.add(new Student("S041", "Vijay Anand", "STOP-08", true));
        l.add(new Student("S042", "Sowmiya R", "STOP-08", true));
        l.add(new Student("S043", "Lokesh B", "STOP-09", true));
        l.add(new Student("S044", "Nisha K", "STOP-09", true));
        l.add(new Student("S045", "Prakash J", "STOP-09", true));
        return l;
    }

    static Set<String> presentOn1012() {
        Set<String> absent = new HashSet<>(List.of("S003", "S011", "S024", "S038", "S043", "S044", "S045"));
        Set<String> p = new HashSet<>();
        for (Student s : students()) if (!absent.contains(s.getId())) p.add(s.getId());
        return p;
    }

    static List<CollegeBus> buses() {
        List<CollegeBus> l = new ArrayList<>();
        l.add(new StandardBus("BUS-01", 12));
        l.add(new StandardBus("BUS-02", 12));
        l.add(new MiniBus("BUS-03", 8));
        return l;
    }
}
