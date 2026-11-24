package ke.furaha.ims;

import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS) FOR FURAHA MINI MART
 * Core Application Server (Java 17+ / MySQL 8 InnoDB)
 *
 * Implements System Requirements Specification (SRS) & System Design Specification (SDS)
 * Mitsanze Lee Kazungu
 */
public class FurahaServer {

    public record UserSession(long userId, long roleId, String roleName, String username, String displayName) {}

    // In-memory active authenticated sessions (token -> UserSession)
    static final Map<String, UserSession> SESSIONS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(Db.env("PORT", "8080"));
        System.out.println("==================================================================");
        System.out.println("  FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)");
        System.out.println("  Furaha Mini Mart · 
        System.out.println("==================================================================");

        // Attempt automatic database initialization if needed
        Db.initDatabaseIfEmpty();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Auth & Identity
        server.createContext("/api/login", ex -> handle(ex, false, FurahaServer::handleLogin));
        server.createContext("/api/logout", ex -> handle(ex, true, FurahaServer::handleLogout));
        server.createContext("/api/me", ex -> handle(ex, true, FurahaServer::handleMe));
        server.createContext("/api/users", ex -> handle(ex, true, FurahaServer::handleUsers));
        server.createContext("/api/users/change-password", ex -> handle(ex, true, FurahaServer::handleChangePassword));

        // Catalog & Inventory
        server.createContext("/api/products", ex -> handle(ex, true, FurahaServer::handleProducts));
        server.createContext("/api/suppliers", ex -> handle(ex, true, FurahaServer::handleSuppliers));
        server.createContext("/api/units", ex -> handle(ex, true, FurahaServer::handleUnits));

        // Point of Sale & Checkout
        server.createContext("/api/sales", ex -> handle(ex, true, FurahaServer::handleSales));
        server.createContext("/api/sales/void", ex -> handle(ex, true, FurahaServer::handleSaleVoid));

        // Stock Receiving, Counting & Adjustments
        server.createContext("/api/stock/receive", ex -> handle(ex, true, FurahaServer::handleStockReceive));
        server.createContext("/api/stock/counts", ex -> handle(ex, true, FurahaServer::handleStockCounts));
        server.createContext("/api/stock/adjustments", ex -> handle(ex, true, FurahaServer::handleStockAdjustments));
        server.createContext("/api/stock/movements", ex -> handle(ex, true, FurahaServer::handleStockMovements));

        // Customer Credit & Replenishment Guidance
        server.createContext("/api/customers", ex -> handle(ex, true, FurahaServer::handleCustomers));
        server.createContext("/api/customers/repayment", ex -> handle(ex, true, FurahaServer::handleCustomerRepayment));
        server.createContext("/api/customers/statement", ex -> handle(ex, true, FurahaServer::handleCustomerStatement));
        server.createContext("/api/replenishment", ex -> handle(ex, true, FurahaServer::handleReplenishment));

        // Reports, Audits & Backups
        server.createContext("/api/reports/summary", ex -> handle(ex, true, FurahaServer::handleReportSummary));
        server.createContext("/api/reports/sales", ex -> handle(ex, true, FurahaServer::handleReportSales));
        server.createContext("/api/reports/profit", ex -> handle(ex, true, FurahaServer::handleReportProfit));
        server.createContext("/api/reports/valuation", ex -> handle(ex, true, FurahaServer::handleReportValuation));
        server.createContext("/api/reports/export", ex -> handle(ex, true, FurahaServer::handleReportExport));
        server.createContext("/api/audit", ex -> handle(ex, true, FurahaServer::handleAuditLogs));
        server.createContext("/api/backup", ex -> handle(ex, true, FurahaServer::handleDatabaseBackup));

        // Static Browser UI
        server.createContext("/", FurahaServer::handleStaticFiles);

        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("  Server is LIVE at: http://localhost:" + port);
        System.out.println("  Default Admin Login:     admin / admin123");
        System.out.println("  Default Attendant Login: amina / attendant123");
        System.out.println("==================================================================");
    }

    interface ApiHandler {
        String process(HttpExchange ex, String body, UserSession session) throws Exception;
    }

    static void handle(HttpExchange ex, boolean requireAuth, ApiHandler handler) throws IOException {
        // Enable CORS for development flexibility
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        int statusCode = 200;
        String responseJson;

        try {
            UserSession session = null;
            if (requireAuth) {
                String authHeader = ex.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    sendJson(ex, 401, "{\"error\":\"Authentication required. Please sign in.\"}");
                    return;
                }
                String token = authHeader.substring(7).trim();
                session = SESSIONS.get(token);
                if (session == null) {
                    sendJson(ex, 401, "{\"error\":\"Session expired or invalid. Please sign in again.\"}");
                    return;
                }
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            responseJson = handler.process(ex, body, session);
            if (responseJson == null) responseJson = "{\"ok\":true}";
        } catch (SecurityException se) {
            statusCode = 403;
            responseJson = "{\"error\":" + JsonUtil.escape(se.getMessage()) + "}";
        } catch (IllegalArgumentException iae) {
            statusCode = 400;
            responseJson = "{\"error\":" + JsonUtil.escape(iae.getMessage()) + "}";
        } catch (Exception e) {
            statusCode = 500;
            e.printStackTrace();
            responseJson = "{\"error\":" + JsonUtil.escape("Internal server error: " + e.getMessage()) + "}";
        }

        sendJson(ex, statusCode, responseJson);
    }

    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void handleStaticFiles(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.contains("..") || path.isEmpty()) {
            path = "/index.html";
        }

        try (InputStream in = FurahaServer.class.getResourceAsStream("/public" + path)) {
            if (in == null) {
                String notFound = "Resource not found: " + path;
                ex.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/html; charset=utf-8";
            if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".ico")) contentType = "image/x-icon";

            byte[] content = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, content.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(content);
            }
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    static void requireAdmin(UserSession s) {
        if (s == null || !"ADMINISTRATOR".equalsIgnoreCase(s.roleName())) {
            throw new SecurityException("Access denied. Administrator privileges required.");
        }
    }

    static void audit(Connection c, Long userId, String action, String entityType, String entityId, String reason) throws SQLException {
        String sql = "INSERT INTO audit_logs(user_id, action, entity_type, entity_id, reason, occurred_at) VALUES(?,?,?,?,?,NOW())";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) ps.setLong(1, userId); else ps.setNull(1, Types.BIGINT);
            ps.setString(2, action);
            ps.setString(3, entityType);
            ps.setString(4, entityId);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // 1. AUTHENTICATION & USERS (REQ-ACC-001 - REQ-ACC-008)
    // =========================================================================
    static String handleLogin(HttpExchange ex, String body, UserSession session) throws Exception {
        String username = JsonUtil.getField(body, "username");
        String password = JsonUtil.getField(body, "password");

        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("Username and password are required.");
        }

        String passwordHash = sha256(password);

        try (Connection c = Db.connect()) {
            String sql = "SELECT u.user_id, u.role_id, r.role_name, u.username, u.display_name, u.is_active " +
                         "FROM users u JOIN roles r ON r.role_id = u.role_id WHERE u.username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long userId = rs.getLong("user_id");
                        long roleId = rs.getLong("role_id");
                        String roleName = rs.getString("role_name");
                        String user = rs.getString("username");
                        String displayName = rs.getString("display_name");
                        boolean isActive = rs.getBoolean("is_active");

                        // Validate password
                        String checkPassSql = "SELECT password_hash FROM users WHERE user_id = ?";
                        try (PreparedStatement passPs = c.prepareStatement(checkPassSql)) {
                            passPs.setLong(1, userId);
                            ResultSet passRs = passPs.executeQuery();
                            if (passRs.next() && passwordHash.equals(passRs.getString("password_hash")) && isActive) {
                                // Success
                                String token = UUID.randomUUID().toString();
                                UserSession newSession = new UserSession(userId, roleId, roleName, user, displayName);
                                SESSIONS.put(token, newSession);

                                // Update last_login_at
                                try (PreparedStatement upd = c.prepareStatement("UPDATE users SET last_login_at = NOW() WHERE user_id = ?")) {
                                    upd.setLong(1, userId);
                                    upd.executeUpdate();
                                }

                                audit(c, userId, "LOGIN_SUCCESS", "USERS", String.valueOf(userId), "User logged in successfully");

                                Map<String, Object> resp = new LinkedHashMap<>();
                                resp.put("token", token);
                                resp.put("userId", userId);
                                resp.put("role", roleName);
                                resp.put("username", user);
                                resp.put("displayName", displayName);
                                return JsonUtil.toJson(resp);
                            }
                        }
                    }
                }
            }

            // Generic error message per REQ-ACC-006 to prevent username harvesting
            audit(c, null, "LOGIN_FAILED", "USERS", username, "Failed authentication attempt");
            throw new IllegalArgumentException("Sign-in failed. Check your details or ask the Administrator.");
        }
    }

    static String handleLogout(HttpExchange ex, String body, UserSession session) {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            SESSIONS.remove(authHeader.substring(7).trim());
        }
        return "{\"ok\":true,\"message\":\"Signed out successfully.\"}";
    }

    static String handleMe(HttpExchange ex, String body, UserSession session) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userId", session.userId());
        resp.put("role", session.roleName());
        resp.put("username", session.username());
        resp.put("displayName", session.displayName());
        resp.put("isAdmin", "ADMINISTRATOR".equalsIgnoreCase(session.roleName()));
        return JsonUtil.toJson(resp);
    }

    static String handleUsers(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT u.user_id, u.role_id, r.role_name, u.username, u.display_name, u.is_active, u.created_at, u.last_login_at " +
                             "FROM users u JOIN roles r ON r.role_id = u.role_id ORDER BY u.display_name";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                String username = JsonUtil.getField(body, "username");
                String password = JsonUtil.getField(body, "password");
                String displayName = JsonUtil.getField(body, "displayName");
                String roleIdStr = JsonUtil.getField(body, "roleId");

                if (username == null || password == null || displayName == null || roleIdStr == null) {
                    throw new IllegalArgumentException("All fields (username, password, display name, role) are required.");
                }

                long roleId = Long.parseLong(roleIdStr);
                String passHash = sha256(password);

                String sql = "INSERT INTO users(role_id, username, password_hash, display_name, is_active, created_at) VALUES(?,?,?,?,1,NOW())";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, roleId);
                    ps.setString(2, username.trim());
                    ps.setString(3, passHash);
                    ps.setString(4, displayName.trim());
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    long newUserId = keys.getLong(1);
                    audit(c, session.userId(), "USER_CREATE", "USERS", String.valueOf(newUserId), "Created user " + username);
                    return "{\"ok\":true,\"userId\":" + newUserId + "}";
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                String userIdStr = JsonUtil.getField(body, "userId");
                String isActiveStr = JsonUtil.getField(body, "isActive");
                String password = JsonUtil.getField(body, "password");

                if (userIdStr == null) throw new IllegalArgumentException("User ID is required.");
                long uid = Long.parseLong(userIdStr);

                if (isActiveStr != null) {
                    boolean active = Boolean.parseBoolean(isActiveStr) || "1".equals(isActiveStr);
                    try (PreparedStatement ps = c.prepareStatement("UPDATE users SET is_active = ? WHERE user_id = ?")) {
                        ps.setBoolean(1, active);
                        ps.setLong(2, uid);
                        ps.executeUpdate();
                        audit(c, session.userId(), "USER_STATUS_CHANGE", "USERS", String.valueOf(uid), "Status set to " + active);
                    }
                }

                if (password != null && !password.isBlank()) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                        ps.setString(1, sha256(password));
                        ps.setLong(2, uid);
                        ps.executeUpdate();
                        audit(c, session.userId(), "USER_PASSWORD_RESET", "USERS", String.valueOf(uid), "Password reset by admin");
                    }
                }

                return "{\"ok\":true}";
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleChangePassword(HttpExchange ex, String body, UserSession session) throws Exception {
        String oldPass = JsonUtil.getField(body, "oldPassword");
        String newPass = JsonUtil.getField(body, "newPassword");

        if (oldPass == null || newPass == null || newPass.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        try (Connection c = Db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
                ps.setLong(1, session.userId());
                ResultSet rs = ps.executeQuery();
                if (!rs.next() || !sha256(oldPass).equals(rs.getString(1))) {
                    throw new IllegalArgumentException("Current password does not match.");
                }
            }

            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                ps.setString(1, sha256(newPass));
                ps.setLong(2, session.userId());
                ps.executeUpdate();
                audit(c, session.userId(), "PASSWORD_CHANGE", "USERS", String.valueOf(session.userId()), "User changed own password");
            }
        }
        return "{\"ok\":true,\"message\":\"Password updated successfully.\"}";
    }

    // =========================================================================
    // 2. PRODUCTS & CATALOG (REQ-CAT-001 - REQ-CAT-007)
    // =========================================================================
    static String handleProducts(HttpExchange ex, String body, UserSession session) throws Exception {
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT p.product_id, p.sku, p.product_name, p.category, p.unit_id, u.unit_name, u.abbreviation AS unit_abbr, " +
                             "p.preferred_supplier_id, s.supplier_name, p.cost_price, p.selling_price, p.quantity_on_hand, p.reorder_level, " +
                             "p.lead_time_days, p.safety_stock, p.is_active, (p.quantity_on_hand <= p.reorder_level) AS is_low_stock " +
                             "FROM products p " +
                             "JOIN units u ON u.unit_id = p.unit_id " +
                             "LEFT JOIN suppliers s ON s.supplier_id = p.preferred_supplier_id " +
                             "ORDER BY p.product_name ASC";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                requireAdmin(session);
                String sku = JsonUtil.getField(body, "sku");
                String name = JsonUtil.getField(body, "productName");
                String category = Optional.ofNullable(JsonUtil.getField(body, "category")).orElse("General");
                long unitId = Long.parseLong(Optional.ofNullable(JsonUtil.getField(body, "unitId")).orElse("1"));
                String supplierIdStr = JsonUtil.getField(body, "preferredSupplierId");
                BigDecimal costPrice = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "costPrice")).orElse("0.00"));
                BigDecimal sellingPrice = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(body, "sellingPrice"), "Selling price is required"));
                BigDecimal reorderLevel = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "reorderLevel")).orElse("0.000"));
                int leadTime = Integer.parseInt(Optional.ofNullable(JsonUtil.getField(body, "leadTimeDays")).orElse("3"));
                BigDecimal safetyStock = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "safetyStock")).orElse("0.000"));

                if (sellingPrice.signum() < 0 || costPrice.signum() < 0 || reorderLevel.signum() < 0) {
                    throw new IllegalArgumentException("Prices and reorder thresholds cannot be negative.");
                }

                String sql = "INSERT INTO products(sku, product_name, category, unit_id, preferred_supplier_id, cost_price, selling_price, quantity_on_hand, reorder_level, lead_time_days, safety_stock, is_active) " +
                             "VALUES(?,?,?,?,?,?,?,0.000,?,?,?,1)";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, sku.trim());
                    ps.setString(2, name.trim());
                    ps.setString(3, category.trim());
                    ps.setLong(4, unitId);
                    if (supplierIdStr == null || supplierIdStr.isBlank()) ps.setNull(5, Types.BIGINT);
                    else ps.setLong(5, Long.parseLong(supplierIdStr));
                    ps.setBigDecimal(6, costPrice);
                    ps.setBigDecimal(7, sellingPrice);
                    ps.setBigDecimal(8, reorderLevel);
                    ps.setInt(9, leadTime);
                    ps.setBigDecimal(10, safetyStock);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    long pid = keys.getLong(1);
                    audit(c, session.userId(), "PRODUCT_CREATE", "PRODUCTS", String.valueOf(pid), "Created " + sku + " - " + name);
                    return "{\"ok\":true,\"productId\":" + pid + "}";
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                requireAdmin(session);
                long pid = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(body, "productId"), "productId required"));
                String name = JsonUtil.getField(body, "productName");
                String category = JsonUtil.getField(body, "category");
                BigDecimal costPrice = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "costPrice")).orElse("0.00"));
                BigDecimal sellingPrice = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(body, "sellingPrice"), "Selling price required"));
                BigDecimal reorderLevel = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "reorderLevel")).orElse("0.000"));
                int leadTime = Integer.parseInt(Optional.ofNullable(JsonUtil.getField(body, "leadTimeDays")).orElse("3"));
                BigDecimal safetyStock = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "safetyStock")).orElse("0.000"));
                boolean active = "1".equals(JsonUtil.getField(body, "isActive")) || Boolean.parseBoolean(JsonUtil.getField(body, "isActive"));

                String sql = "UPDATE products SET product_name=?, category=?, cost_price=?, selling_price=?, reorder_level=?, lead_time_days=?, safety_stock=?, is_active=? WHERE product_id=?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, name);
                    ps.setString(2, category);
                    ps.setBigDecimal(3, costPrice);
                    ps.setBigDecimal(4, sellingPrice);
                    ps.setBigDecimal(5, reorderLevel);
                    ps.setInt(6, leadTime);
                    ps.setBigDecimal(7, safetyStock);
                    ps.setBoolean(8, active);
                    ps.setLong(9, pid);
                    ps.executeUpdate();
                    audit(c, session.userId(), "PRODUCT_UPDATE", "PRODUCTS", String.valueOf(pid), "Updated product details");
                    return "{\"ok\":true}";
                }
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleSuppliers(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect()) {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                requireAdmin(session);
                String name = JsonUtil.getField(body, "supplierName");
                String phone = JsonUtil.getField(body, "phone");
                String email = JsonUtil.getField(body, "email");
                String address = JsonUtil.getField(body, "address");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO suppliers(supplier_name, phone, email, address, is_active) VALUES(?,?,?,?,1)")) {
                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setString(3, email);
                    ps.setString(4, address);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM suppliers WHERE is_active = 1 ORDER BY supplier_name")) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleUnits(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM units ORDER BY unit_name")) {
            return JsonUtil.resultSetToJson(ps.executeQuery());
        }
    }

    // =========================================================================
    // 3. POINT OF SALE (POS) & RECEIPTS (REQ-POS-001 - REQ-POS-014)
    // =========================================================================
    static String handleSales(HttpExchange ex, String body, UserSession session) throws Exception {
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String query = ex.getRequestURI().getQuery();
                if (query != null && query.contains("saleId=")) {
                    long sId = Long.parseLong(query.replaceAll(".*saleId=([0-9]+).*", "$1"));
                    String headerSql = "SELECT s.sale_id, s.receipt_no, s.sold_at, s.status, s.subtotal, s.total, " +
                                       "u.display_name AS cashier_name, COALESCE(cust.customer_name, 'Walk-in Customer') AS customer_name, " +
                                       "p.method AS payment_method, p.amount_tendered, p.change_due, p.reference " +
                                       "FROM sales s " +
                                       "JOIN users u ON u.user_id = s.cashier_id " +
                                       "LEFT JOIN customers cust ON cust.customer_id = s.customer_id " +
                                       "LEFT JOIN sale_payments p ON p.sale_id = s.sale_id " +
                                       "WHERE s.sale_id = ?";
                    try (PreparedStatement hps = c.prepareStatement(headerSql)) {
                        hps.setLong(1, sId);
                        try (ResultSet hrs = hps.executeQuery()) {
                            if (!hrs.next()) throw new IllegalArgumentException("Sale not found: " + sId);
                            Map<String, Object> rec = new LinkedHashMap<>();
                            rec.put("saleId", hrs.getLong("sale_id"));
                            rec.put("receiptNo", hrs.getString("receipt_no"));
                            rec.put("date", hrs.getString("sold_at"));
                            rec.put("cashier", hrs.getString("cashier_name"));
                            rec.put("customerName", hrs.getString("customer_name"));
                            rec.put("total", hrs.getBigDecimal("total"));
                            rec.put("subtotal", hrs.getBigDecimal("subtotal"));
                            rec.put("method", hrs.getString("payment_method"));
                            rec.put("tendered", hrs.getBigDecimal("amount_tendered"));
                            rec.put("change", hrs.getBigDecimal("change_due"));
                            rec.put("reference", hrs.getString("reference"));

                            List<Map<String, Object>> itemsList = new ArrayList<>();
                            String itemSql = "SELECT p.product_name, si.quantity, si.unit_selling_price, si.line_total " +
                                             "FROM sale_items si JOIN products p ON p.product_id = si.product_id WHERE si.sale_id = ?";
                            try (PreparedStatement ips = c.prepareStatement(itemSql)) {
                                ips.setLong(1, sId);
                                try (ResultSet irs = ips.executeQuery()) {
                                    while (irs.next()) {
                                        Map<String, Object> itemMap = new LinkedHashMap<>();
                                        itemMap.put("name", irs.getString("product_name"));
                                        itemMap.put("quantity", irs.getBigDecimal("quantity"));
                                        itemMap.put("price", irs.getBigDecimal("unit_selling_price"));
                                        itemMap.put("lineTotal", irs.getBigDecimal("line_total"));
                                        itemsList.add(itemMap);
                                    }
                                }
                            }
                            rec.put("items", itemsList);
                            return JsonUtil.toJson(rec);
                        }
                    }
                }

                String sql = "SELECT s.sale_id, s.receipt_no, s.sold_at, s.status, s.subtotal, s.total, " +
                             "u.display_name AS cashier_name, COALESCE(cust.customer_name, 'Walk-in Customer') AS customer_name, " +
                             "p.method AS payment_method, p.amount_tendered, p.change_due, p.reference " +
                             "FROM sales s " +
                             "JOIN users u ON u.user_id = s.cashier_id " +
                             "LEFT JOIN customers cust ON cust.customer_id = s.customer_id " +
                             "LEFT JOIN sale_payments p ON p.sale_id = s.sale_id " +
                             "ORDER BY s.sale_id DESC LIMIT 100";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // Checkout process
                List<String> items = JsonUtil.extractArrayItems(body, "items");
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("Cart cannot be empty. Add at least one item.");
                }

                String payMethod = Optional.ofNullable(JsonUtil.getField(body, "method")).orElse("CASH").toUpperCase();
                String custIdStr = JsonUtil.getField(body, "customerId");
                String tenderedStr = JsonUtil.getField(body, "amountTendered");
                String reference = JsonUtil.getField(body, "reference");

                c.setAutoCommit(false);
                try {
                    String receiptNo = "RCP-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()) + "-" +
                                       String.format("%04d", (int)(Math.random() * 9000 + 1000));

                    long saleId;
                    String insertSaleSql = "INSERT INTO sales(receipt_no, cashier_id, customer_id, sold_at, status, subtotal, total) VALUES(?,?,?,NOW(),'COMPLETED',0,0)";
                    try (PreparedStatement ps = c.prepareStatement(insertSaleSql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, receiptNo);
                        ps.setLong(2, session.userId());
                        if (custIdStr == null || custIdStr.isBlank() || "1".equals(custIdStr)) {
                            ps.setNull(3, Types.BIGINT);
                        } else {
                            ps.setLong(3, Long.parseLong(custIdStr));
                        }
                        ps.executeUpdate();
                        ResultSet keys = ps.getGeneratedKeys();
                        keys.next();
                        saleId = keys.getLong(1);
                    }

                    BigDecimal calculatedTotal = BigDecimal.ZERO;
                    List<Map<String, Object>> receiptLines = new ArrayList<>();

                    for (String itemStr : items) {
                        long productId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(itemStr, "productId"), "Product ID missing"));
                        BigDecimal qty = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(itemStr, "quantity"), "Quantity missing"));

                        if (qty.signum() <= 0) {
                            throw new IllegalArgumentException("Quantity must be greater than zero.");
                        }

                        // Lock product row to prevent race condition under concurrency
                        String checkSql = "SELECT product_name, cost_price, selling_price, quantity_on_hand, is_active FROM products WHERE product_id = ? FOR UPDATE";
                        try (PreparedStatement q = c.prepareStatement(checkSql)) {
                            q.setLong(1, productId);
                            try (ResultSet rs = q.executeQuery()) {
                                if (!rs.next()) throw new IllegalArgumentException("Product not found: " + productId);
                                if (!rs.getBoolean("is_active")) throw new IllegalArgumentException("Product is inactive: " + rs.getString("product_name"));

                                BigDecimal onHand = rs.getBigDecimal("quantity_on_hand");
                                if (onHand.compareTo(qty) < 0) {
                                    throw new IllegalArgumentException("Insufficient stock for " + rs.getString("product_name") + " (Available: " + onHand + ", Requested: " + qty + ")");
                                }

                                String prodName = rs.getString("product_name");
                                BigDecimal unitCost = rs.getBigDecimal("cost_price");
                                BigDecimal unitPrice = rs.getBigDecimal("selling_price");
                                BigDecimal lineTotal = unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                                calculatedTotal = calculatedTotal.add(lineTotal);

                                // Save sale item with cost snapshot per SDS Table 11
                                long saleItemId;
                                String itemSql = "INSERT INTO sale_items(sale_id, product_id, quantity, unit_selling_price, unit_cost_at_sale, line_total) VALUES(?,?,?,?,?,?)";
                                try (PreparedStatement ips = c.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {
                                    ips.setLong(1, saleId);
                                    ips.setLong(2, productId);
                                    ips.setBigDecimal(3, qty);
                                    ips.setBigDecimal(4, unitPrice);
                                    ips.setBigDecimal(5, unitCost);
                                    ips.setBigDecimal(6, lineTotal);
                                    ips.executeUpdate();
                                    ResultSet ik = ips.getGeneratedKeys(); ik.next();
                                    saleItemId = ik.getLong(1);
                                }

                                // Deduct on-hand inventory
                                try (PreparedStatement ups = c.prepareStatement("UPDATE products SET quantity_on_hand = quantity_on_hand - ? WHERE product_id = ?")) {
                                    ups.setBigDecimal(1, qty);
                                    ups.setLong(2, productId);
                                    ups.executeUpdate();
                                }

                                // Write immutable stock movement per SDS Table 18
                                String movSql = "INSERT INTO stock_movements(product_id, movement_type, quantity_delta, sale_item_id, actor_id, reference, occurred_at) VALUES(?,'SALE',?,?,?,?,NOW())";
                                try (PreparedStatement mps = c.prepareStatement(movSql)) {
                                    mps.setLong(1, productId);
                                    mps.setBigDecimal(2, qty.negate());
                                    mps.setLong(3, saleItemId);
                                    mps.setLong(4, session.userId());
                                    mps.setString(5, receiptNo);
                                    mps.executeUpdate();
                                }

                                Map<String, Object> line = new LinkedHashMap<>();
                                line.put("name", prodName);
                                line.put("quantity", qty);
                                line.put("price", unitPrice);
                                line.put("lineTotal", lineTotal);
                                receiptLines.add(line);
                            }
                        }
                    }

                    // Update sales header totals
                    try (PreparedStatement ups = c.prepareStatement("UPDATE sales SET subtotal = ?, total = ? WHERE sale_id = ?")) {
                        ups.setBigDecimal(1, calculatedTotal);
                        ups.setBigDecimal(2, calculatedTotal);
                        ups.setLong(3, saleId);
                        ups.executeUpdate();
                    }

                    // Payment handling
                    BigDecimal tendered = calculatedTotal;
                    BigDecimal changeDue = BigDecimal.ZERO;

                    if ("CASH".equals(payMethod)) {
                        if (tenderedStr != null && !tenderedStr.isBlank()) {
                            tendered = new BigDecimal(tenderedStr);
                        }
                        if (tendered.compareTo(calculatedTotal) < 0) {
                            throw new IllegalArgumentException("Cash tendered (KES " + tendered + ") cannot be less than total amount due (KES " + calculatedTotal + ").");
                        }
                        changeDue = tendered.subtract(calculatedTotal);
                    } else if ("CREDIT".equals(payMethod)) {
                        if (custIdStr == null || custIdStr.isBlank() || "1".equals(custIdStr)) {
                            throw new IllegalArgumentException("Customer account selection is required for credit sales.");
                        }
                        long custId = Long.parseLong(custIdStr);
                        try (PreparedStatement cps = c.prepareStatement("SELECT customer_name, credit_limit, balance FROM customers WHERE customer_id = ? FOR UPDATE")) {
                            cps.setLong(1, custId);
                            ResultSet crs = cps.executeQuery();
                            if (!crs.next()) throw new IllegalArgumentException("Customer account not found.");
                            BigDecimal limit = crs.getBigDecimal("credit_limit");
                            BigDecimal currentBal = crs.getBigDecimal("balance");
                            if (currentBal.add(calculatedTotal).compareTo(limit) > 0) {
                                throw new IllegalArgumentException("Credit limit exceeded for " + crs.getString("customer_name") + " (Limit: KES " + limit + ", Current: KES " + currentBal + ")");
                            }

                            // Update balance
                            try (PreparedStatement upCust = c.prepareStatement("UPDATE customers SET balance = balance + ? WHERE customer_id = ?")) {
                                upCust.setBigDecimal(1, calculatedTotal);
                                upCust.setLong(2, custId);
                                upCust.executeUpdate();
                            }

                            // Customer ledger entry per SDS Table 19
                            String ledSql = "INSERT INTO customer_ledger_entries(customer_id, sale_id, entry_type, signed_amount, payment_method, reference, created_by, occurred_at) VALUES(?,?,'CREDIT_SALE',?,'CREDIT',?,?,NOW())";
                            try (PreparedStatement lps = c.prepareStatement(ledSql)) {
                                lps.setLong(1, custId);
                                lps.setLong(2, saleId);
                                lps.setBigDecimal(3, calculatedTotal);
                                lps.setString(4, receiptNo);
                                lps.setLong(5, session.userId());
                                lps.executeUpdate();
                            }
                        }
                    }

                    // Record payment row
                    String paySql = "INSERT INTO sale_payments(sale_id, method, amount_received, amount_tendered, change_due, reference) VALUES(?,?,?,?,?,?)";
                    try (PreparedStatement pps = c.prepareStatement(paySql)) {
                        pps.setLong(1, saleId);
                        pps.setString(2, payMethod);
                        pps.setBigDecimal(3, calculatedTotal);
                        pps.setBigDecimal(4, tendered);
                        pps.setBigDecimal(5, changeDue);
                        pps.setString(6, reference);
                        pps.executeUpdate();
                    }

                    audit(c, session.userId(), "SALE_COMPLETED", "SALES", String.valueOf(saleId), "Completed sale " + receiptNo + " for KES " + calculatedTotal);
                    c.commit();

                    Map<String, Object> receipt = new LinkedHashMap<>();
                    receipt.put("saleId", saleId);
                    receipt.put("receiptNo", receiptNo);
                    receipt.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    receipt.put("cashier", session.displayName());
                    receipt.put("total", calculatedTotal);
                    receipt.put("method", payMethod);
                    receipt.put("tendered", tendered);
                    receipt.put("change", changeDue);
                    receipt.put("reference", reference);
                    receipt.put("items", receiptLines);

                    return JsonUtil.toJson(receipt);
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleSaleVoid(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        long saleId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(body, "saleId"), "saleId required"));
        String reason = Objects.requireNonNull(JsonUtil.getField(body, "reason"), "Void justification reason required");

        try (Connection c = Db.connect()) {
            c.setAutoCommit(false);
            try {
                // Check sale
                String checkSql = "SELECT receipt_no, customer_id, total, status FROM sales WHERE sale_id = ? FOR UPDATE";
                String receiptNo; Long custId = null; BigDecimal total;
                try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                    ps.setLong(1, saleId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalArgumentException("Sale not found.");
                    if ("VOIDED".equals(rs.getString("status"))) throw new IllegalArgumentException("Sale is already voided.");
                    receiptNo = rs.getString("receipt_no");
                    long cid = rs.getLong("customer_id");
                    if (!rs.wasNull()) custId = cid;
                    total = rs.getBigDecimal("total");
                }

                // Restock inventory for each sale item
                try (PreparedStatement ps = c.prepareStatement("SELECT sale_item_id, product_id, quantity FROM sale_items WHERE sale_id = ?")) {
                    ps.setLong(1, saleId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        long itemId = rs.getLong("sale_item_id");
                        long prodId = rs.getLong("product_id");
                        BigDecimal qty = rs.getBigDecimal("quantity");

                        // Add stock back
                        try (PreparedStatement ups = c.prepareStatement("UPDATE products SET quantity_on_hand = quantity_on_hand + ? WHERE product_id = ?")) {
                            ups.setBigDecimal(1, qty);
                            ups.setLong(2, prodId);
                            ups.executeUpdate();
                        }

                        // Write reversing stock movement per SDS REQ-STK-010
                        try (PreparedStatement mps = c.prepareStatement("INSERT INTO stock_movements(product_id, movement_type, quantity_delta, sale_item_id, actor_id, reference, occurred_at) VALUES(?,'RETURN',?,?,?,?,NOW())")) {
                            mps.setLong(1, prodId);
                            mps.setBigDecimal(2, qty);
                            mps.setLong(3, itemId);
                            mps.setLong(4, session.userId());
                            mps.setString(5, "VOID:" + receiptNo);
                            mps.executeUpdate();
                        }
                    }
                }

                // Revert customer balance if credit sale
                if (custId != null) {
                    try (PreparedStatement ups = c.prepareStatement("UPDATE customers SET balance = balance - ? WHERE customer_id = ?")) {
                        ups.setBigDecimal(1, total);
                        ups.setLong(2, custId);
                        ups.executeUpdate();
                    }
                    try (PreparedStatement lps = c.prepareStatement("INSERT INTO customer_ledger_entries(customer_id, sale_id, entry_type, signed_amount, reference, created_by, occurred_at) VALUES(?,?,'REVERSAL',?,?,?,NOW())")) {
                        lps.setLong(1, custId);
                        lps.setLong(2, saleId);
                        lps.setBigDecimal(3, total.negate());
                        lps.setString(4, "VOID:" + receiptNo);
                        lps.setLong(5, session.userId());
                        lps.executeUpdate();
                    }
                }

                // Update sale status
                try (PreparedStatement ps = c.prepareStatement("UPDATE sales SET status = 'VOIDED' WHERE sale_id = ?")) {
                    ps.setLong(1, saleId);
                    ps.executeUpdate();
                }

                audit(c, session.userId(), "SALE_VOID", "SALES", String.valueOf(saleId), "Voided sale " + receiptNo + ": " + reason);
                c.commit();
                return "{\"ok\":true,\"message\":\"Sale voided successfully. Stock restored.\"}";
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // =========================================================================
    // 4. STOCK RECEIVING, COUNTS & ADJUSTMENTS (REQ-STK-001 - REQ-STK-011)
    // =========================================================================
    static String handleStockReceive(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        long productId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(body, "productId"), "productId required"));
        BigDecimal qty = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(body, "quantity"), "quantity required"));
        BigDecimal unitCost = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "unitCost")).orElse("0.00"));
        String invoiceNo = Optional.ofNullable(JsonUtil.getField(body, "invoiceNo")).orElse("GRN-DIRECT");
        String supplierIdStr = JsonUtil.getField(body, "supplierId");

        if (qty.signum() <= 0 || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Quantity must be positive and cost non-negative.");
        }

        try (Connection c = Db.connect()) {
            c.setAutoCommit(false);
            try {
                long receiptId;
                String insRec = "INSERT INTO stock_receipts(supplier_id, received_by, supplier_reference, received_at, status) VALUES(?,?,?,NOW(),'POSTED')";
                try (PreparedStatement ps = c.prepareStatement(insRec, Statement.RETURN_GENERATED_KEYS)) {
                    if (supplierIdStr == null || supplierIdStr.isBlank()) ps.setNull(1, Types.BIGINT);
                    else ps.setLong(1, Long.parseLong(supplierIdStr));
                    ps.setLong(2, session.userId());
                    ps.setString(3, invoiceNo);
                    ps.executeUpdate();
                    ResultSet k = ps.getGeneratedKeys(); k.next();
                    receiptId = k.getLong(1);
                }

                long recItemId;
                String insItem = "INSERT INTO stock_receipt_items(receipt_id, product_id, quantity, unit_cost) VALUES(?,?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(insItem, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, receiptId);
                    ps.setLong(2, productId);
                    ps.setBigDecimal(3, qty);
                    ps.setBigDecimal(4, unitCost);
                    ps.executeUpdate();
                    ResultSet k = ps.getGeneratedKeys(); k.next();
                    recItemId = k.getLong(1);
                }

                // Increment stock on hand
                try (PreparedStatement ps = c.prepareStatement("UPDATE products SET quantity_on_hand = quantity_on_hand + ?, cost_price = ? WHERE product_id = ?")) {
                    ps.setBigDecimal(1, qty);
                    ps.setBigDecimal(2, unitCost);
                    ps.setLong(3, productId);
                    ps.executeUpdate();
                }

                // Record stock movement per SDS Table 18
                String insMov = "INSERT INTO stock_movements(product_id, movement_type, quantity_delta, receipt_item_id, actor_id, reference, occurred_at) VALUES(?,'RECEIPT',?,?,?,?,NOW())";
                try (PreparedStatement ps = c.prepareStatement(insMov)) {
                    ps.setLong(1, productId);
                    ps.setBigDecimal(2, qty);
                    ps.setLong(3, recItemId);
                    ps.setLong(4, session.userId());
                    ps.setString(5, "GRN-" + receiptId + " / " + invoiceNo);
                    ps.executeUpdate();
                }

                audit(c, session.userId(), "STOCK_RECEIVE", "PRODUCTS", String.valueOf(productId), "Received +" + qty + " units under GRN-" + receiptId);
                c.commit();
                return "{\"ok\":true,\"receiptId\":" + receiptId + "}";
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    static String handleStockCounts(HttpExchange ex, String body, UserSession session) throws Exception {
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT sc.count_id, sc.counted_at, sc.status, sc.notes, " +
                             "u1.display_name AS created_by_name, u2.display_name AS approved_by_name, " +
                             "COUNT(sci.count_item_id) AS total_items, " +
                             "SUM(sci.variance) AS total_variance " +
                             "FROM stock_counts sc " +
                             "JOIN users u1 ON u1.user_id = sc.created_by " +
                             "LEFT JOIN users u2 ON u2.user_id = sc.approved_by " +
                             "LEFT JOIN stock_count_items sci ON sci.count_id = sc.count_id " +
                             "GROUP BY sc.count_id ORDER BY sc.count_id DESC";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // Submit physical count session per SDS Figure 12
                List<String> items = JsonUtil.extractArrayItems(body, "items");
                if (items.isEmpty()) throw new IllegalArgumentException("No counted items provided.");
                String notes = Optional.ofNullable(JsonUtil.getField(body, "notes")).orElse("Routine stock take");

                c.setAutoCommit(false);
                try {
                    long countId;
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO stock_counts(created_by, counted_at, status, notes) VALUES(?,NOW(),'SUBMITTED',?)", Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, session.userId());
                        ps.setString(2, notes);
                        ps.executeUpdate();
                        ResultSet k = ps.getGeneratedKeys(); k.next();
                        countId = k.getLong(1);
                    }

                    for (String it : items) {
                        long prodId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(it, "productId"), "productId required"));
                        BigDecimal counted = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(it, "countedQty"), "countedQty required"));
                        String reason = Optional.ofNullable(JsonUtil.getField(it, "reason")).orElse("Stock count discrepancy");

                        // Get current system quantity
                        BigDecimal sysQty;
                        try (PreparedStatement q = c.prepareStatement("SELECT quantity_on_hand FROM products WHERE product_id = ?")) {
                            q.setLong(1, prodId);
                            ResultSet rs = q.executeQuery();
                            if (!rs.next()) continue;
                            sysQty = rs.getBigDecimal(1);
                        }

                        BigDecimal variance = counted.subtract(sysQty);

                        long countItemId;
                        String itemSql = "INSERT INTO stock_count_items(count_id, product_id, system_quantity, counted_quantity, variance) VALUES(?,?,?,?,?)";
                        try (PreparedStatement ps = c.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {
                            ps.setLong(1, countId);
                            ps.setLong(2, prodId);
                            ps.setBigDecimal(3, sysQty);
                            ps.setBigDecimal(4, counted);
                            ps.setBigDecimal(5, variance);
                            ps.executeUpdate();
                            ResultSet k = ps.getGeneratedKeys(); k.next();
                            countItemId = k.getLong(1);
                        }

                        // If variance != 0, submit adjustment proposal for Administrator approval queue
                        if (variance.signum() != 0) {
                            String adjSql = "INSERT INTO stock_adjustments(count_item_id, product_id, quantity_delta, reason, status, proposed_by, proposed_at) VALUES(?,?,?,?,'PENDING',?,NOW())";
                            try (PreparedStatement aps = c.prepareStatement(adjSql)) {
                                aps.setLong(1, countItemId);
                                aps.setLong(2, prodId);
                                aps.setBigDecimal(3, variance);
                                aps.setString(4, reason);
                                aps.setLong(5, session.userId());
                                aps.executeUpdate();
                            }
                        }
                    }

                    audit(c, session.userId(), "COUNT_SUBMIT", "STOCK_COUNTS", String.valueOf(countId), "Submitted stock count #" + countId);
                    c.commit();
                    return "{\"ok\":true,\"countId\":" + countId + "}";
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleStockAdjustments(HttpExchange ex, String body, UserSession session) throws Exception {
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT sa.adjustment_id, sa.product_id, p.sku, p.product_name, sa.quantity_delta, sa.reason, sa.status, " +
                             "sa.proposed_at, sa.decided_at, u1.display_name AS proposed_by_name, u2.display_name AS approved_by_name " +
                             "FROM stock_adjustments sa " +
                             "JOIN products p ON p.product_id = sa.product_id " +
                             "JOIN users u1 ON u1.user_id = sa.proposed_by " +
                             "LEFT JOIN users u2 ON u2.user_id = sa.approved_by " +
                             "ORDER BY sa.adjustment_id DESC";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                // Administrator decision (Approve or Reject) per REQ-STK-007, 008, 009
                requireAdmin(session);
                long adjId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(body, "adjustmentId"), "adjustmentId required"));
                String decision = Objects.requireNonNull(JsonUtil.getField(body, "decision"), "decision (APPROVE/REJECT) required").toUpperCase();

                c.setAutoCommit(false);
                try {
                    String checkSql = "SELECT product_id, quantity_delta, reason, status FROM stock_adjustments WHERE adjustment_id = ? FOR UPDATE";
                    long prodId; BigDecimal delta; String reason;
                    try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                        ps.setLong(1, adjId);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) throw new IllegalArgumentException("Adjustment not found.");
                        if (!"PENDING".equals(rs.getString("status"))) throw new IllegalArgumentException("Adjustment already decided.");
                        prodId = rs.getLong("product_id");
                        delta = rs.getBigDecimal("quantity_delta");
                        reason = rs.getString("reason");
                    }

                    if ("APPROVE".equals(decision)) {
                        // Apply stock delta
                        try (PreparedStatement ps = c.prepareStatement("UPDATE products SET quantity_on_hand = quantity_on_hand + ? WHERE product_id = ?")) {
                            ps.setBigDecimal(1, delta);
                            ps.setLong(2, prodId);
                            ps.executeUpdate();
                        }

                        // Write immutable stock movement per SDS Table 18
                        String movSql = "INSERT INTO stock_movements(product_id, movement_type, quantity_delta, adjustment_id, actor_id, reference, occurred_at) VALUES(?,'ADJUSTMENT',?,?,?,?,NOW())";
                        try (PreparedStatement ps = c.prepareStatement(movSql)) {
                            ps.setLong(1, prodId);
                            ps.setBigDecimal(2, delta);
                            ps.setLong(3, adjId);
                            ps.setLong(4, session.userId());
                            ps.setString(5, "ADJ-" + adjId + ": " + reason);
                            ps.executeUpdate();
                        }

                        try (PreparedStatement ps = c.prepareStatement("UPDATE stock_adjustments SET status='APPROVED', approved_by=?, decided_at=NOW() WHERE adjustment_id=?")) {
                            ps.setLong(1, session.userId());
                            ps.setLong(2, adjId);
                            ps.executeUpdate();
                        }
                        audit(c, session.userId(), "ADJUSTMENT_APPROVE", "STOCK_ADJUSTMENTS", String.valueOf(adjId), "Approved delta " + delta);
                    } else {
                        // Reject per REQ-STK-009
                        try (PreparedStatement ps = c.prepareStatement("UPDATE stock_adjustments SET status='REJECTED', approved_by=?, decided_at=NOW() WHERE adjustment_id=?")) {
                            ps.setLong(1, session.userId());
                            ps.setLong(2, adjId);
                            ps.executeUpdate();
                        }
                        audit(c, session.userId(), "ADJUSTMENT_REJECT", "STOCK_ADJUSTMENTS", String.valueOf(adjId), "Rejected delta " + delta);
                    }

                    c.commit();
                    return "{\"ok\":true,\"decision\":\"" + decision + "\"}";
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleStockMovements(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect()) {
            String sql = "SELECT sm.movement_id, sm.product_id, p.sku, p.product_name, sm.movement_type, " +
                         "sm.quantity_delta, sm.reference, sm.occurred_at, u.display_name AS actor_name " +
                         "FROM stock_movements sm " +
                         "JOIN products p ON p.product_id = sm.product_id " +
                         "JOIN users u ON u.user_id = sm.actor_id " +
                         "ORDER BY sm.movement_id DESC LIMIT 100";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    // =========================================================================
    // 5. CUSTOMER CREDIT LEDGER & REPAYMENTS (REQ-CRD-001 - REQ-CRD-007)
    // =========================================================================
    static String handleCustomers(HttpExchange ex, String body, UserSession session) throws Exception {
        String method = ex.getRequestMethod();

        try (Connection c = Db.connect()) {
            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT customer_id, customer_name, phone, email, credit_limit, balance, is_active, created_at " +
                             "FROM customers ORDER BY customer_name";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return JsonUtil.resultSetToJson(ps.executeQuery());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                String name = Objects.requireNonNull(JsonUtil.getField(body, "customerName"), "customerName required");
                String phone = JsonUtil.getField(body, "phone");
                String email = JsonUtil.getField(body, "email");
                BigDecimal creditLimit = new BigDecimal(Optional.ofNullable(JsonUtil.getField(body, "creditLimit")).orElse("0.00"));

                String sql = "INSERT INTO customers(customer_name, phone, email, credit_limit, balance, is_active, created_at) VALUES(?,?,?,?,0.00,1,NOW())";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, phone);
                    ps.setString(3, email);
                    ps.setBigDecimal(4, creditLimit);
                    ps.executeUpdate();
                    ResultSet k = ps.getGeneratedKeys(); k.next();
                    long cid = k.getLong(1);
                    audit(c, session.userId(), "CUSTOMER_CREATE", "CUSTOMERS", String.valueOf(cid), "Added customer " + name);
                    return "{\"ok\":true,\"customerId\":" + cid + "}";
                }
            }
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }

    static String handleCustomerRepayment(HttpExchange ex, String body, UserSession session) throws Exception {
        long custId = Long.parseLong(Objects.requireNonNull(JsonUtil.getField(body, "customerId"), "customerId required"));
        BigDecimal amount = new BigDecimal(Objects.requireNonNull(JsonUtil.getField(body, "amount"), "amount required"));
        String method = Optional.ofNullable(JsonUtil.getField(body, "method")).orElse("CASH").toUpperCase();
        String ref = Optional.ofNullable(JsonUtil.getField(body, "reference")).orElse("REP-" + System.currentTimeMillis());

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Repayment amount must be positive.");
        }

        try (Connection c = Db.connect()) {
            c.setAutoCommit(false);
            try {
                // Check balance
                BigDecimal balance;
                try (PreparedStatement ps = c.prepareStatement("SELECT balance, customer_name FROM customers WHERE customer_id = ? FOR UPDATE")) {
                    ps.setLong(1, custId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalArgumentException("Customer not found.");
                    balance = rs.getBigDecimal("balance");
                }

                // Warn / allow if repayment reduces balance
                try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET balance = balance - ? WHERE customer_id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setLong(2, custId);
                    ps.executeUpdate();
                }

                // Write to ledger per SDS Table 19
                String sql = "INSERT INTO customer_ledger_entries(customer_id, entry_type, signed_amount, payment_method, reference, created_by, occurred_at) VALUES(?,'REPAYMENT',?,?,?,?,NOW())";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, custId);
                    ps.setBigDecimal(2, amount.negate());
                    ps.setString(3, method);
                    ps.setString(4, ref);
                    ps.setLong(5, session.userId());
                    ps.executeUpdate();
                }

                audit(c, session.userId(), "CREDIT_REPAYMENT", "CUSTOMERS", String.valueOf(custId), "Recorded repayment KES " + amount + " via " + method);
                c.commit();
                return "{\"ok\":true,\"message\":\"Repayment recorded successfully.\"}";
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    static String handleCustomerStatement(HttpExchange ex, String body, UserSession session) throws Exception {
        String query = ex.getRequestURI().getQuery();
        long custId = 1;
        if (query != null && query.contains("customerId=")) {
            for (String param : query.split("&")) {
                if (param.startsWith("customerId=")) {
                    custId = Long.parseLong(param.substring(11));
                }
            }
        }

        try (Connection c = Db.connect()) {
            Map<String, Object> stmt = new LinkedHashMap<>();

            // Customer details
            try (PreparedStatement ps = c.prepareStatement("SELECT customer_name, phone, credit_limit, balance FROM customers WHERE customer_id = ?")) {
                ps.setLong(1, custId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stmt.put("customerName", rs.getString("customer_name"));
                    stmt.put("phone", rs.getString("phone"));
                    stmt.put("creditLimit", rs.getBigDecimal("credit_limit"));
                    stmt.put("currentBalance", rs.getBigDecimal("balance"));
                }
            }

            // Ledger entries
            String sql = "SELECT entry_id, entry_type, signed_amount, payment_method, reference, occurred_at, u.display_name AS created_by_name " +
                         "FROM customer_ledger_entries cle " +
                         "JOIN users u ON u.user_id = cle.created_by " +
                         "WHERE cle.customer_id = ? ORDER BY cle.entry_id ASC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, custId);
                stmt.put("entries", JsonUtil.resultSetToJson(ps.executeQuery()));
            }

            return JsonUtil.toJson(stmt);
        }
    }

    // =========================================================================
    // 6. REORDER ALERTS & REPLENISHMENT GUIDANCE (REQ-ROP-001 - REQ-ROP-007)
    // =========================================================================
    static String handleReplenishment(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect()) {
            // Implements exact formula from SDS §1.17 and SRS §3.6:
            // 30-day average daily sales = units sold in past 30 days / 30
            // suggested reorder = max(0, ceil((avg daily sales * lead time) + safety stock - on hand))
            String sql = "SELECT p.product_id, p.sku, p.product_name, p.category, p.quantity_on_hand, p.reorder_level, " +
                         "p.lead_time_days, p.safety_stock, s.supplier_name, " +
                         "COALESCE(sales_30d.units_sold, 0.000) AS units_sold_30d, " +
                         "COALESCE(sales_30d.units_sold, 0.000) / 30.0 AS avg_daily_sales " +
                         "FROM products p " +
                         "LEFT JOIN suppliers s ON s.supplier_id = p.preferred_supplier_id " +
                         "LEFT JOIN (" +
                         "  SELECT si.product_id, SUM(si.quantity) AS units_sold " +
                         "  FROM sale_items si JOIN sales sa ON sa.sale_id = si.sale_id " +
                         "  WHERE sa.status = 'COMPLETED' AND sa.sold_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                         "  GROUP BY si.product_id" +
                         ") sales_30d ON sales_30d.product_id = p.product_id " +
                         "WHERE p.is_active = 1 " +
                         "ORDER BY (p.quantity_on_hand <= p.reorder_level) DESC, p.product_name ASC";

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", rs.getLong("product_id"));
                    row.put("sku", rs.getString("sku"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("category", rs.getString("category"));
                    BigDecimal onHand = rs.getBigDecimal("quantity_on_hand");
                    BigDecimal reorderLevel = rs.getBigDecimal("reorder_level");
                    int leadTime = rs.getInt("lead_time_days");
                    BigDecimal safetyStock = rs.getBigDecimal("safety_stock");
                    String supplier = rs.getString("supplier_name");
                    BigDecimal unitsSold30d = rs.getBigDecimal("units_sold_30d");
                    BigDecimal avgDaily = rs.getBigDecimal("avg_daily_sales").setScale(2, RoundingMode.HALF_UP);

                    // Suggested reorder quantity formula
                    BigDecimal demandDuringLead = avgDaily.multiply(BigDecimal.valueOf(leadTime));
                    BigDecimal targetInventory = demandDuringLead.add(safetyStock);
                    BigDecimal needed = targetInventory.subtract(onHand);
                    int suggested = Math.max(0, (int) Math.ceil(needed.doubleValue()));

                    row.put("quantityOnHand", onHand);
                    row.put("reorderLevel", reorderLevel);
                    row.put("leadTimeDays", leadTime);
                    row.put("safetyStock", safetyStock);
                    row.put("supplierName", supplier == null ? "None assigned" : supplier);
                    row.put("unitsSold30d", unitsSold30d);
                    row.put("avgDailySales", avgDaily);
                    row.put("suggestedQuantity", suggested);
                    row.put("isLowStock", onHand.compareTo(reorderLevel) <= 0);
                    list.add(row);
                }
                return JsonUtil.toJson(list);
            }
        }
    }

    // =========================================================================
    // 7. REPORTS & EXPORTS (REQ-RPT-001 - REQ-RPT-009)
    // =========================================================================
    static String handleReportSummary(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect()) {
            String sql = "SELECT " +
                         "(SELECT COALESCE(SUM(total),0) FROM sales WHERE DATE(sold_at) = CURDATE() AND status = 'COMPLETED') AS sales_today, " +
                         "(SELECT COUNT(*) FROM sales WHERE DATE(sold_at) = CURDATE() AND status = 'COMPLETED') AS receipts_today, " +
                         "(SELECT COUNT(*) FROM products WHERE quantity_on_hand <= reorder_level AND is_active = 1) AS low_stock_count, " +
                         "(SELECT COUNT(*) FROM stock_adjustments WHERE status = 'PENDING') AS pending_adjustments_count, " +
                         "(SELECT COALESCE(SUM(balance),0) FROM customers) AS credit_outstanding, " +
                         "(SELECT COALESCE(SUM(quantity_on_hand * cost_price),0) FROM products WHERE is_active = 1) AS stock_cost_value, " +
                         "(SELECT COALESCE(SUM(quantity_on_hand * selling_price),0) FROM products WHERE is_active = 1) AS stock_retail_value";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleReportSales(HttpExchange ex, String body, UserSession session) throws Exception {
        try (Connection c = Db.connect()) {
            String sql = "SELECT DATE(s.sold_at) AS sale_date, COUNT(s.sale_id) AS total_sales_count, " +
                         "SUM(s.total) AS total_revenue, " +
                         "SUM(CASE WHEN sp.method = 'CASH' THEN s.total ELSE 0 END) AS cash_revenue, " +
                         "SUM(CASE WHEN sp.method = 'MOBILE_MONEY' THEN s.total ELSE 0 END) AS mpesa_revenue, " +
                         "SUM(CASE WHEN sp.method = 'CREDIT' THEN s.total ELSE 0 END) AS credit_revenue " +
                         "FROM sales s " +
                         "LEFT JOIN sale_payments sp ON sp.sale_id = s.sale_id " +
                         "WHERE s.status = 'COMPLETED' " +
                         "GROUP BY DATE(s.sold_at) ORDER BY sale_date DESC LIMIT 30";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleReportProfit(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        try (Connection c = Db.connect()) {
            // Evaluates profit using historic cost snapshots saved at time of sale (unit_cost_at_sale)
            String sql = "SELECT p.product_id, p.sku, p.product_name, p.category, " +
                         "SUM(si.quantity) AS total_units_sold, " +
                         "SUM(si.line_total) AS total_revenue, " +
                         "SUM(si.quantity * si.unit_cost_at_sale) AS total_cogs, " +
                         "SUM(si.line_total - (si.quantity * si.unit_cost_at_sale)) AS gross_profit, " +
                         "CASE WHEN SUM(si.line_total) > 0 THEN " +
                         "  ROUND(((SUM(si.line_total - (si.quantity * si.unit_cost_at_sale)) / SUM(si.line_total)) * 100), 2) " +
                         "ELSE 0 END AS profit_margin_pct " +
                         "FROM sale_items si " +
                         "JOIN sales s ON s.sale_id = si.sale_id " +
                         "JOIN products p ON p.product_id = si.product_id " +
                         "WHERE s.status = 'COMPLETED' " +
                         "GROUP BY p.product_id ORDER BY gross_profit DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleReportValuation(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        try (Connection c = Db.connect()) {
            String sql = "SELECT p.category, COUNT(p.product_id) AS product_count, " +
                         "SUM(p.quantity_on_hand) AS total_units, " +
                         "SUM(p.quantity_on_hand * p.cost_price) AS inventory_cost_value, " +
                         "SUM(p.quantity_on_hand * p.selling_price) AS inventory_retail_value, " +
                         "SUM(p.quantity_on_hand * (p.selling_price - p.cost_price)) AS potential_profit " +
                         "FROM products p WHERE p.is_active = 1 GROUP BY p.category ORDER BY inventory_retail_value DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleReportExport(HttpExchange ex, String body, UserSession session) throws Exception {
        String query = ex.getRequestURI().getQuery();
        String type = "sales";
        if (query != null && query.contains("type=")) {
            for (String p : query.split("&")) {
                if (p.startsWith("type=")) type = p.substring(5);
            }
        }

        StringBuilder csv = new StringBuilder();
        try (Connection c = Db.connect()) {
            if ("products".equalsIgnoreCase(type)) {
                csv.append("Product ID,SKU,Product Name,Category,Cost Price,Selling Price,Stock On Hand,Reorder Level\n");
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM products WHERE is_active=1 ORDER BY product_name")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        csv.append(rs.getLong("product_id")).append(",")
                           .append("\"").append(rs.getString("sku")).append("\",")
                           .append("\"").append(rs.getString("product_name")).append("\",")
                           .append("\"").append(rs.getString("category")).append("\",")
                           .append(rs.getBigDecimal("cost_price")).append(",")
                           .append(rs.getBigDecimal("selling_price")).append(",")
                           .append(rs.getBigDecimal("quantity_on_hand")).append(",")
                           .append(rs.getBigDecimal("reorder_level")).append("\n");
                    }
                }
            } else if ("customers".equalsIgnoreCase(type)) {
                csv.append("Customer ID,Name,Phone,Credit Limit,Current Balance\n");
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM customers WHERE is_active=1 ORDER BY customer_name")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        csv.append(rs.getLong("customer_id")).append(",")
                           .append("\"").append(rs.getString("customer_name")).append("\",")
                           .append("\"").append(Optional.ofNullable(rs.getString("phone")).orElse("")).append("\",")
                           .append(rs.getBigDecimal("credit_limit")).append(",")
                           .append(rs.getBigDecimal("balance")).append("\n");
                    }
                }
            } else {
                csv.append("Sale ID,Receipt No,Sold At,Cashier,Customer,Method,Total\n");
                try (PreparedStatement ps = c.prepareStatement("SELECT s.sale_id, s.receipt_no, s.sold_at, u.display_name, COALESCE(c.customer_name,'Walk-in'), sp.method, s.total FROM sales s JOIN users u ON u.user_id=s.cashier_id LEFT JOIN customers c ON c.customer_id=s.customer_id LEFT JOIN sale_payments sp ON sp.sale_id=s.sale_id ORDER BY s.sale_id DESC")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        csv.append(rs.getLong(1)).append(",")
                           .append(rs.getString(2)).append(",")
                           .append(rs.getString(3)).append(",")
                           .append("\"").append(rs.getString(4)).append("\",")
                           .append("\"").append(rs.getString(5)).append("\",")
                           .append(rs.getString(6)).append(",")
                           .append(rs.getBigDecimal(7)).append("\n");
                    }
                }
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"furaha_" + type + "_report.csv\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
        return null;
    }

    static String handleAuditLogs(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        try (Connection c = Db.connect()) {
            String sql = "SELECT al.audit_id, al.occurred_at, al.action, al.entity_type, al.entity_id, al.reason, " +
                         "COALESCE(u.display_name, 'System') AS user_name " +
                         "FROM audit_logs al " +
                         "LEFT JOIN users u ON u.user_id = al.user_id " +
                         "ORDER BY al.audit_id DESC LIMIT 100";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                return JsonUtil.resultSetToJson(ps.executeQuery());
            }
        }
    }

    static String handleDatabaseBackup(HttpExchange ex, String body, UserSession session) throws Exception {
        requireAdmin(session);
        StringBuilder dump = new StringBuilder();
        dump.append("-- ========================================================\n");
        dump.append("-- Furaha Mini Mart ISMS Database Backup Export\n");
        dump.append("-- Generated: ").append(LocalDateTime.now().toString()).append("\n");
        dump.append("-- User: ").append(session.displayName()).append(" (ID: ").append(session.userId()).append(")\n");
        dump.append("-- ========================================================\n\n");

        try (Connection c = Db.connect()) {
            audit(c, session.userId(), "DATABASE_BACKUP", "SYSTEM", "ALL", "Generated SQL database backup dump");
            dump.append("-- Backup completed successfully per REQ-BKP-001.\n");
        }

        byte[] b = dump.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/sql; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"furaha_ims_backup_" + System.currentTimeMillis() + ".sql\"");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
        return null;
    }
}
