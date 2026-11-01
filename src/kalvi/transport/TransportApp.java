package kalvi.transport;

import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import kalvi.transport.dao.*;

/** Swing front end. Every action reads/writes MySQL through JdbcDataStore. */
public class TransportApp extends JFrame {
    private final JdbcDataStore db;
    private final JTextField dateField = new JTextField(10);
    private final DefaultTableModel studentModel = model("ID", "Name", "Stop", "Authorized", "Present", "Fee");
    private final DefaultTableModel busModel = model("Bus", "Type", "Seats", "Status");
    private final DefaultTableModel stopModel = model("Stop ID", "Stop name");
    private final DefaultTableModel feeModel = model("ID", "Name", "Due", "Paid", "Status");
    private final DefaultTableModel eventModel = model("#", "Time", "Type", "Details");
    private final JTable studentTable = new JTable(studentModel);
    private final JTable busTable = new JTable(busModel);
    private final JTextArea output = new JTextArea();
    private TripPlanner planner;

    public TransportApp(JdbcDataStore db) throws Exception {
        super("Smart College Transportation Management - Kalvi College");
        this.db = db;
        dateField.setText(db.latestAttendanceDate().toString());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Date (yyyy-mm-dd):"));
        top.add(dateField);
        top.add(button("Reload", e -> safe(this::refresh)));

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Students", studentsTab());
        tabs.add("Buses", busesTab());
        tabs.add("Plan Pickups", planTab());
        tabs.add("Fees", new JScrollPane(new JTable(feeModel)));
        tabs.add("Stops", new JScrollPane(new JTable(stopModel)));
        tabs.add("Event Log", new JScrollPane(new JTable(eventModel)));

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setSize(1000, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        refresh();
    }

    // ---------- tabs ----------
    private JPanel studentsTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(studentTable), BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT));
        b.add(button("Mark Present", e -> attendance(true)));
        b.add(button("Mark Absent", e -> attendance(false)));
        b.add(button("Toggle Authorization", e -> safe(this::toggleAuth)));
        b.add(button("Add Student", e -> safe(this::addStudent)));
        b.add(button("Record Fee Payment", e -> safe(this::payFee)));
        p.add(b, BorderLayout.SOUTH);
        return p;
    }

    private JPanel busesTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(busTable), BorderLayout.CENTER);
        p.add(button("Toggle Available / Broken Down", e -> safe(this::toggleBus)), BorderLayout.SOUTH);
        return p;
    }

    private JPanel planTab() {
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(output), BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT));
        b.add(button("Plan Pickups", e -> safe(this::plan)));
        b.add(button("Simulate Breakdown", e -> safe(this::breakdown)));
        b.add(button("Save Plan to Database", e -> safe(this::savePlan)));
        p.add(b, BorderLayout.SOUTH);
        return p;
    }

    // ---------- actions ----------
    private LocalDate date() { return LocalDate.parse(dateField.getText().trim()); }

    private void refresh() throws Exception {
        LocalDate d = date();
        Set<String> present = db.presentOn(d);
        Map<String, FeeRecord> fees = db.fees();
        studentModel.setRowCount(0);
        feeModel.setRowCount(0);
        for (Student s : db.students()) {
            FeeRecord f = fees.get(s.getId());
            studentModel.addRow(new Object[]{s.getId(), s.getName(), s.getStopId(),
                    s.isAuthorized() ? "Yes" : "No", present.contains(s.getId()) ? "Present" : "Absent/NA",
                    f == null ? "-" : f.status()});
            if (f != null) feeModel.addRow(new Object[]{s.getId(), s.getName(), f.getAmountDue(), f.getAmountPaid(), f.status()});
        }
        busModel.setRowCount(0);
        for (CollegeBus b : db.buses())
            busModel.addRow(new Object[]{b.getId(), b.type(), b.getCapacity(), b.isAvailable() ? "AVAILABLE" : "BROKEN_DOWN"});
        stopModel.setRowCount(0);
        for (Map.Entry<String, String> e : db.stopNames().entrySet()) stopModel.addRow(new Object[]{e.getKey(), e.getValue()});
        eventModel.setRowCount(0);
        for (String[] r : db.events()) eventModel.addRow(r);
    }

    private void attendance(boolean present) {
        safe(() -> {
            int[] rows = studentTable.getSelectedRows();
            if (rows.length == 0) { info("Select one or more students first."); return; }
            for (int r : rows) db.markAttendance((String) studentModel.getValueAt(r, 0), date(), present);
            refresh();
        });
    }

    private void toggleAuth() throws Exception {
        int r = studentTable.getSelectedRow();
        if (r < 0) { info("Select a student first."); return; }
        db.setAuthorized((String) studentModel.getValueAt(r, 0), studentModel.getValueAt(r, 3).equals("No"));
        refresh();
    }

    private void addStudent() throws Exception {
        JTextField name = new JTextField();
        List<String> stops = new ArrayList<>(db.stopNames().keySet());
        stops.remove("CAMPUS");
        JComboBox<String> stop = new JComboBox<>(stops.toArray(String[]::new));
        JTextField fee = new JTextField("12000");
        Object[] form = {"Name:", name, "Pickup stop:", stop, "Fee due:", fee};
        if (JOptionPane.showConfirmDialog(this, form, "Add Student", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().isBlank()) { info("Name is required."); return; }
        int max = 0;
        for (Student s : db.students()) max = Math.max(max, Integer.parseInt(s.getId().substring(1)));
        db.addStudent(String.format("S%03d", max + 1), name.getText().trim(), (String) stop.getSelectedItem(),
                Integer.parseInt(fee.getText().trim()));
        refresh();
    }

    private void payFee() throws Exception {
        int r = studentTable.getSelectedRow();
        if (r < 0) { info("Select a student first."); return; }
        String amt = JOptionPane.showInputDialog(this, "Amount paid now:");
        if (amt == null || amt.isBlank()) return;
        db.addFeePayment((String) studentModel.getValueAt(r, 0), Integer.parseInt(amt.trim()));
        refresh();
    }

    private void toggleBus() throws Exception {
        int r = busTable.getSelectedRow();
        if (r < 0) { info("Select a bus first."); return; }
        db.setBusAvailable((String) busModel.getValueAt(r, 0), busModel.getValueAt(r, 3).equals("BROKEN_DOWN"));
        refresh();
    }

    private void plan() throws Exception {
        planner = new TripPlanner(db.graph(), new NearestNextStopPolicy(), db.stopNames());
        planner.loadWaiting(db.students(), db.presentOn(date()));
        planner.plan(db.buses());
        output.setText("PLAN FOR " + date() + "\n\n" + planner.report());
        output.setCaretPosition(0);
    }

    private void breakdown() throws Exception {
        if (planner == null) { info("Run 'Plan Pickups' first."); return; }
        List<String> ids = new ArrayList<>();
        for (DailyTrip t : planner.getTrips()) if (t.getBus().isAvailable()) ids.add(t.getBus().getId());
        if (ids.isEmpty()) { info("No active trips."); return; }
        String bus = (String) JOptionPane.showInputDialog(this, "Which bus breaks down?", "Breakdown",
                JOptionPane.QUESTION_MESSAGE, null, ids.toArray(), ids.get(0));
        if (bus == null) return;
        String n = JOptionPane.showInputDialog(this, "Breakdown after how many pickups?", "1");
        if (n == null) return;
        String msg = planner.breakdown(bus, Integer.parseInt(n.trim()));
        output.setText("BREAKDOWN: " + msg + "\n\n" + planner.report());
        output.setCaretPosition(0);
    }

    private void savePlan() throws Exception {
        if (planner == null) { info("Run 'Plan Pickups' first."); return; }
        db.saveTrips(date(), planner.getTrips());
        for (DailyTrip t : planner.getTrips())
            if (t.getStatus().equals("BROKEN_DOWN")) db.setBusAvailable(t.getBus().getId(), false);
        refresh();
        info("Plan saved to daily_trips / trip_assignments.");
    }

    // ---------- helpers ----------
    private interface Action { void run() throws Exception; }

    private void safe(Action a) {
        try { a.run(); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }
    private void info(String m) { JOptionPane.showMessageDialog(this, m); }

    private static JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    private static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String properties = args.length > 0 && !args[0].equals("--demo") ? args[0] : AppDir.of("db.properties").toString();
                TransportDao dao;
                if (args.length > 0 && args[0].equals("--demo")) {
                    dao = new DemoTransportDao(AppDir.of("data", "demo"));
                } else {
                    DriverLoader.ensure();
                    dao = new JdbcTransportDao(DbConfig.load(properties));
                }
                JdbcDataStore store = new JdbcDataStore(dao);
                new TransportApp(store).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Cannot connect to MySQL:\n" + e.getMessage()
                        + "\n\nCheck db.properties, that MySQL is running, and that the Connector/J jar is in lib/.",
                        "Database error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
