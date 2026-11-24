package ke.furaha.ims;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * Database connection and schema manager for Furaha Inventory & Sales Management System.
 * Connects to MySQL 8 (or compatible JDBC URL) configured via environment variables:
 *   DB_URL      (default: jdbc:mysql://localhost:3306/furaha_ims?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Nairobi)
 *   DB_USER     (default: furaha_ims)
 *   DB_PASSWORD (default: furaha_ims)
 */
public final class Db {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/furaha_ims?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Nairobi&characterEncoding=utf8";
    private static final String DEFAULT_USER = "furaha_ims";
    private static final String DEFAULT_PASS = "furaha_ims";

    public static String getUrl() {
        return env("DB_URL", DEFAULT_URL);
    }

    public static String getUser() {
        return env("DB_USER", DEFAULT_USER);
    }

    public static String getPassword() {
        return env("DB_PASSWORD", DEFAULT_PASS);
    }

    public static String env(String key, String fallback) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            val = System.getProperty(key);
        }
        return (val == null || val.isBlank()) ? fallback : val.trim();
    }

    public static Connection connect() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Driver may already be loaded via ServiceLoader
        }
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    /**
     * Attempts to verify database connectivity. If tables are missing and schema/seed
     * are found in the classpath, automatically runs initialization.
     */
    public static boolean initDatabaseIfEmpty() {
        try (Connection c = connect()) {
            DatabaseMetaData meta = c.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "users", null)) {
                if (rs.next()) {
                    return true; // Tables already exist
                }
            }
            // Execute schema and seed if present in classpath
            System.out.println("[DB] Initializing fresh Furaha database schema...");
            runSqlScript(c, "/db/schema.sql");
            runSqlScript(c, "/db/seed.sql");
            System.out.println("[DB] Fresh Furaha database schema and seed data loaded successfully.");
            return true;
        } catch (Exception e) {
            System.err.println("[DB] Notice: " + e.getMessage());
            return false;
        }
    }

    private static void runSqlScript(Connection c, String resourcePath) {
        try (InputStream in = Db.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String[] statements = content.split(";\\s*(\\r?\\n)+");
            try (Statement st = c.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            st.execute(trimmed);
                        } catch (SQLException sqle) {
                            // Continue on minor script warnings (e.g. table exists)
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DB] Error running script " + resourcePath + ": " + e.getMessage());
        }
    }
}
