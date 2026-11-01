package kalvi.transport.dao;

import kalvi.transport.AppDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Gets the MySQL driver ready no matter how the program was started. If the jar in lib/ is not on the classpath
 * (IDE "Run" button, java -cp out ... without lib/*), it is loaded from lib/ here and registered with JDBC.
 */
public final class DriverLoader {
    private DriverLoader() { }

    public static void ensure() {
        Path lib = AppDir.of("lib");
        try { DriverManager.getDriver("jdbc:mysql://localhost/"); return; }       // already on the classpath
        catch (SQLException notYet) { /* load it below */ }
        DriverFetcher.ensure(lib);
        Path jar = DriverFetcher.find(lib);
        if (jar == null) return;                                                  // WebApp will explain the problem
        try {
            URLClassLoader cl = new URLClassLoader(new URL[]{jar.toUri().toURL()}, DriverLoader.class.getClassLoader());
            Driver d = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, cl).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new Shim(d));
        } catch (java.io.IOException | ReflectiveOperationException | SQLException e) {
            System.err.println("[driver] Could not load " + jar.getFileName() + ": " + e);
        }
    }

    /** DriverManager only trusts drivers its caller can see, so the real driver is wrapped. */
    private record Shim(Driver d) implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException { return d.connect(url, info); }
        @Override
        public boolean acceptsURL(String url) throws SQLException { return d.acceptsURL(url); }
        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return d.getPropertyInfo(url, info); }
        @Override
        public int getMajorVersion() { return d.getMajorVersion(); }
        @Override
        public int getMinorVersion() { return d.getMinorVersion(); }
        @Override
        public boolean jdbcCompliant() { return d.jdbcCompliant(); }
        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException { return d.getParentLogger(); }
    }
}
