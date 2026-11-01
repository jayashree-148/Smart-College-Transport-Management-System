package kalvi.transport.dao;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Creates the whole MySQL database from sql/*.sql with plain JDBC (no mysql command line needed).
 *   java -cp "out:lib/*" kalvi.transport.dao.DbSetup db.properties [--reset]
 * --reset drops college_transport_db first (needed to reload because the seed INSERTs are not IGNORE).
 */
public class DbSetup {
    public static void main(String[] args) throws Exception {
        String props = args.length > 0 && !args[0].startsWith("--") ? args[0] : kalvi.transport.AppDir.of("db.properties").toString();
        boolean reset = List.of(args).contains("--reset");
        try { DriverLoader.ensure(); run(DbConfig.load(props), reset); }
        catch (Exception e) { System.err.println("Setup failed: " + e.getMessage()); System.exit(1); }
    }

    /** Creates the database + tables + sample data (and the app_users table). Throws if anything fails. */
    public static void run(DbConfig cfg, boolean reset) throws Exception {
        System.out.println("Connecting to " + cfg.serverUrl() + " as " + cfg.user + " ...");
        try (Connection con = DriverManager.getConnection(cfg.serverUrl(), cfg.user, cfg.password);
             Statement st = con.createStatement()) {
            if (reset) { st.execute("DROP DATABASE IF EXISTS college_transport_db"); System.out.println("Dropped old database."); }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.list(kalvi.transport.AppDir.of("sql"))) { s.filter(p -> p.toString().endsWith(".sql")).sorted().forEach(files::add); }
            for (Path f : files) {
                List<String> statements = split(Files.readString(f, StandardCharsets.UTF_8));
                int n = 0;
                for (String sql : statements) {
                    try { st.execute(sql); n++; }
                    catch (SQLException e) {
                        System.err.println("FAILED in " + f.getFileName() + ": " + e.getMessage());
                        System.err.println("  " + sql.substring(0, Math.min(120, sql.length())).replace('\n', ' '));
                        throw new SQLException("script " + f.getFileName() + " failed: " + e.getMessage() + "  (run setup_db with --reset to rebuild)", e);
                    }
                }
                System.out.printf("OK  %-22s %d statements%n", f.getFileName(), n);
            }
            try (ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM pickup_stops), (SELECT COUNT(*) FROM students),"
                    + " (SELECT COUNT(*) FROM fleet_buses), (SELECT COUNT(*) FROM fleet_routes)")) {
                rs.next();
                System.out.printf("Database ready: %d stops, %d students, %d fleet buses, %d routes.%n",
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
            }
        }
    }

    /** Splits a script on ';' while respecting 'quoted ; text' and skipping -- comments. */
    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (inQuote) {
                cur.append(c);
                if (c == '\'') {
                    if (i + 1 < script.length() && script.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else inQuote = false;
                }
            } else if (c == '\'') { inQuote = true; cur.append(c); }
            else if (c == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                while (i < script.length() && script.charAt(i) != '\n') i++;
            } else if (c == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
            } else cur.append(c);
        }
        String s = cur.toString().trim();
        if (!s.isEmpty()) out.add(s);
        return out;
    }
}
