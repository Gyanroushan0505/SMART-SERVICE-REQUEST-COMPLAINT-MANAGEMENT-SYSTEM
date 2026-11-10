
package com.smartcomplaint.servlet;

// JSON data ko read aur send karne ke liye
import com.google.gson.*;

// Database connection ke liye
import com.smartcomplaint.config.DB;

// Password ko hash aur verify karne ke liye
import com.smartcomplaint.service.Passwords;

// Complaint ki prediction ke liye
import com.smartcomplaint.service.PredictionService;

// Servlet aur HTTP classes
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.sql.*;
import java.util.*;

// Is servlet ke saare URLs /api/ se start honge
@WebServlet(urlPatterns = "/api/*")
public class ApiServlet extends HttpServlet {

    // JSON convert karne ke liye Gson object
    private final Gson gson = new Gson();

    // PATCH request ko handle karna
    @Override
    protected void service(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws ServletException, IOException {

        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    // Request body se JSON read karna
    private JsonObject body(HttpServletRequest req)
            throws IOException {

        try (Reader reader = req.getReader()) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }

            return new JsonObject();
        }
    }

    // JSON response send karna
    private void send(
            HttpServletResponse resp,
            int status,
            Object data
    ) throws IOException {

        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(gson.toJson(data));
    }

    // Key-value pairs se response map banana
    private Map<String, Object> map(Object... values) {

        Map<String, Object> result = new LinkedHashMap<>();

        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }

        return result;
    }

    // JSON field ko safely string mein read karna
    private String val(JsonObject body, String key) {

        if (body.has(key) && !body.get(key).isJsonNull()) {
            return body.get(key).getAsString().trim();
        }

        return "";
    }

    // Session se logged-in user ki ID lena
    private long uid(HttpServletRequest req) {

        HttpSession session = req.getSession(false);

        if (session == null) {
            return -1;
        }

        Object value = session.getAttribute("uid");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return -1;
    }

    // Session se logged-in user ka role lena
    private String role(HttpServletRequest req) {

        HttpSession session = req.getSession(false);

        if (session == null) {
            return "";
        }

        Object value = session.getAttribute("role");

        return value == null ? "" : value.toString();
    }

    // Login check karna
    private boolean auth(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        if (uid(req) < 0) {
            send(resp, 401, map(
                    "error",
                    "Please login first."
            ));

            return false;
        }

        return true;
    }

    // User ke role ki permission check karna
    private boolean allow(
            HttpServletRequest req,
            HttpServletResponse resp,
            String... allowedRoles
    ) throws IOException {

        if (!auth(req, resp)) {
            return false;
        }

        String currentRole = role(req);

        for (String allowedRole : allowedRoles) {
            if (allowedRole.equals(currentRole)) {
                return true;
            }
        }

        send(resp, 403, map(
                "error",
                "You do not have permission for this action."
        ));

        return false;
    }

    // =====================================================
    // GET REQUESTS
    // =====================================================

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        String path = req.getPathInfo();

        if (path == null) {
            path = "/";
        }

        try {

            // Logged-in user ki details
            if (path.equals("/me")) {

                if (!auth(req, resp)) {
                    return;
                }

                String sql =
                        "SELECT id, student_id, full_name, email, " +
                        "role, floor_name, room_number, created_at " +
                        "FROM users WHERE id = ?";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setLong(1, uid(req));

                    try (ResultSet rs = ps.executeQuery()) {

                        if (rs.next()) {
                            send(resp, 200, user(rs));
                        } else {
                            send(resp, 404, map(
                                    "error", "User not found."
                            ));
                        }
                    }
                }

                return;
            }

            // Complaints list: user, admin ya staff
            if (path.equals("/complaints")
                    || path.equals("/admin/complaints")
                    || path.equals("/staff/complaints")) {

                if (path.equals("/admin/complaints")) {

                    if (!allow(req, resp, "ADMIN")) {
                        return;
                    }

                } else if (path.equals("/staff/complaints")) {

                    if (!allow(req, resp, "STAFF")) {
                        return;
                    }

                } else if (!auth(req, resp)) {
                    return;
                }

                String sql =
                        "SELECT c.*, u.full_name, " +
                        "st.full_name AS assigned_staff " +
                        "FROM complaints c " +
                        "JOIN users u ON c.user_id = u.id " +
                        "LEFT JOIN users st " +
                        "ON c.assigned_staff_id = st.id";

                if ("USER".equals(role(req))) {
                    sql += " WHERE c.user_id = ?";
                } else if ("STAFF".equals(role(req))) {
                    sql += " WHERE c.assigned_staff_id = ?";
                }

                sql += " ORDER BY c.created_at DESC";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    if ("USER".equals(role(req))
                            || "STAFF".equals(role(req))) {
                        ps.setLong(1, uid(req));
                    }

                    try (ResultSet rs = ps.executeQuery()) {

                        List<Map<String, Object>> complaints =
                                new ArrayList<>();

                        while (rs.next()) {
                            complaints.add(complaint(rs));
                        }

                        send(resp, 200, complaints);
                    }
                }

                return;
            }

            // Admin dashboard par user feedback ki list
            if (path.equals("/admin/feedback")) {
                if (!allow(req, resp, "ADMIN")) {
                    return;
                }

                String sql =
                        "SELECT f.id, c.public_id, u.full_name, u.student_id, " +
                        "st.full_name AS assigned_staff, " +
                        "f.rating, f.comments, f.created_at " +
                        "FROM feedback f " +
                        "LEFT JOIN complaints c ON f.complaint_id = c.id " +
                        "LEFT JOIN users u ON f.user_id = u.id " +
                        "LEFT JOIN users st ON c.assigned_staff_id = st.id " +
                        "ORDER BY f.created_at DESC";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> feedbackList = new ArrayList<>();
                    while (rs.next()) {
                        feedbackList.add(map(
                                "id", rs.getLong("id"),
                                "complaintId", rs.getString("public_id"),
                                "studentName", rs.getString("full_name"),
                                "studentId", rs.getString("student_id"),
                                "assignedStaff", rs.getString("assigned_staff"),
                                "rating", rs.getInt("rating"),
                                "comments", rs.getString("comments"),
                                "createdAt", rs.getTimestamp("created_at")
                        ));
                    }
                    send(resp, 200, feedbackList);
                }
                return;
            }

            // Admin dashboard par staff ki list
            if (path.equals("/staff")) {

                if (!allow(req, resp, "ADMIN")) {
                    return;
                }

                String sql =
                        "SELECT id, student_id, full_name, email, role " +
                        "FROM users WHERE role = 'STAFF' " +
                        "ORDER BY full_name";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {

                    List<Map<String, Object>> staffList =
                            new ArrayList<>();

                    while (rs.next()) {

                        staffList.add(map(
                                "id", rs.getLong("id"),
                                "studentId", rs.getString("student_id"),
                                "name", rs.getString("full_name"),
                                "email", rs.getString("email"),
                                "role", rs.getString("role")
                        ));
                    }

                    send(resp, 200, staffList);
                }

                return;
            }

            // Complaint ki updates/history
            if (path.matches("/complaints/[^/]+/updates")) {

                if (!auth(req, resp)) {
                    return;
                }

                String publicId = path.split("/")[2];

                String sql =
                        "SELECT cu.*, u.full_name AS updated_by_name " +
                        "FROM complaint_updates cu " +
                        "JOIN users u ON cu.updated_by = u.id " +
                        "JOIN complaints c ON c.id = cu.complaint_id " +
                        "WHERE c.public_id = ? " +
                        "AND (c.user_id = ? OR ? IN ('ADMIN','STAFF')) " +
                        "ORDER BY cu.created_at";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, publicId);
                    ps.setLong(2, uid(req));
                    ps.setString(3, role(req));

                    try (ResultSet rs = ps.executeQuery()) {

                        List<Map<String, Object>> updates =
                                new ArrayList<>();

                        while (rs.next()) {

                            updates.add(map(
                                    "oldStatus",
                                    rs.getString("old_status"),
                                    "newStatus",
                                    rs.getString("new_status"),
                                    "note",
                                    rs.getString("note"),
                                    "updatedBy",
                                    rs.getString("updated_by_name"),
                                    "createdAt",
                                    rs.getTimestamp("created_at")
                            ));
                        }

                        send(resp, 200, updates);
                    }
                }

                return;
            }

            send(resp, 404, map(
                    "error", "Endpoint not found."
            ));

        } catch (Exception e) {
            e.printStackTrace();

            send(resp, 500, map(
                    "error",
                    "Server error. Check database configuration and server log."
            ));
        }
    }

    // User details ko JSON-compatible map mein convert karna
    private Map<String, Object> user(ResultSet rs)
            throws SQLException {

        return map(
                "id", rs.getLong("id"),
                "studentId", rs.getString("student_id"),
                "name", rs.getString("full_name"),
                "email", rs.getString("email"),
                "role", rs.getString("role"),
                "floor", rs.getString("floor_name"),
                "roomNumber", rs.getString("room_number"),
                "createdAt", rs.getTimestamp("created_at")
        );
    }

    // Complaint details ko map mein convert karna
    private Map<String, Object> complaint(ResultSet rs)
            throws SQLException {

        return map(
                "id", rs.getLong("id"),
                "publicId", rs.getString("public_id"),
                "studentId", rs.getString("student_id"),
                "name", rs.getString("full_name"),
                "floor", rs.getString("floor_name"),
                "roomNumber", rs.getString("room_number"),
                "category", rs.getString("category"),
                "priority", rs.getString("priority"),
                "description", rs.getString("description"),
                "predictedCategory", rs.getString("predicted_category"),
                "predictedPriority", rs.getString("predicted_priority"),
                "expectedDays", rs.getInt("expected_days"),
                "status", rs.getString("status"),
                "assignedStaffId", rs.getObject("assigned_staff_id"),
                "assignedStaff", rs.getString("assigned_staff"),
                "resolutionNote", rs.getString("resolution_note"),
                "createdAt", rs.getTimestamp("created_at"),
                "updatedAt", rs.getTimestamp("updated_at")
        );
    }

    // =====================================================
    // POST REQUESTS
    // =====================================================

    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        String path = req.getPathInfo();

        if (path == null) {
            path = "/";
        }

        try {

            JsonObject b = body(req);

            // -------------------------------------------------
            // 1. STUDENT REGISTRATION
            // -------------------------------------------------

            if (path.equals("/register")) {

                String sid = val(b, "studentId");
                String name = val(b, "name");
                String email = val(b, "email");
                String password = val(b, "password");
                String floor = val(b, "floor");
                String room = val(b, "roomNumber");

                if (sid.isBlank()
                        || name.isBlank()
                        || email.isBlank()
                        || password.length() < 8) {

                    send(resp, 400, map(
                            "error",
                            "Student ID, name, email and password " +
                            "(at least 8 characters) are required."
                    ));

                    return;
                }

                String sql =
                        "INSERT INTO users " +
                        "(student_id, full_name, email, password_hash, " +
                        "floor_name, room_number) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

                    ps.setString(1, sid);
                    ps.setString(2, name);
                    ps.setString(3, email);
                    ps.setString(4, Passwords.hash(password));
                    ps.setString(5, floor);
                    ps.setString(6, room);

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {

                        if (!keys.next()) {
                            throw new SQLException(
                                    "User ID was not generated."
                            );
                        }

                        HttpSession session = req.getSession(true);
                        session.setAttribute("uid", keys.getLong(1));
                        session.setAttribute("role", "USER");
                    }

                    send(resp, 201, map(
                            "message", "Registration successful.",
                            "redirect", "home.html"
                    ));

                } catch (SQLIntegrityConstraintViolationException e) {

                    send(resp, 409, map(
                            "error",
                            "Student ID or email already registered."
                    ));
                }

                return;
            }

            // -------------------------------------------------
            // 2. LOGIN: USER, ADMIN OR STAFF
            // -------------------------------------------------

            if (path.equals("/login")) {

                String loginId = val(b, "studentId");
                String password = val(b, "password");
                String requestedRole = val(b, "requestedRole");

                String sql =
                        "SELECT id, password_hash, role " +
                        "FROM users WHERE student_id = ? OR email = ?";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, loginId);
                    ps.setString(2, loginId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()
                                || !Passwords.verify(
                                        password,
                                        rs.getString("password_hash")
                                )) {

                            send(resp, 401, map(
                                    "error",
                                    "Invalid ID/email or password."
                            ));

                            return;
                        }

                        String actualRole = rs.getString("role");

                        // Requested login page aur account role match hone chahiye
                        if (!requestedRole.isBlank()
                                && !requestedRole.equals(actualRole)) {

                            send(resp, 403, map(
                                    "error",
                                    "This account cannot use this login page."
                            ));

                            return;
                        }

                        HttpSession session = req.getSession(true);

                        session.setAttribute("uid", rs.getLong("id"));
                        session.setAttribute("role", actualRole);

                        String destination;

                        if ("ADMIN".equals(actualRole)) {
                            destination = "admin.html";
                        } else if ("STAFF".equals(actualRole)) {
                            destination = "staff.html";
                        } else {
                            destination = "home.html";
                        }

                        send(resp, 200, map(
                                "message", "Login successful.",
                                "role", actualRole,
                                "redirect", destination
                        ));
                    }
                }

                return;
            }

            // -------------------------------------------------
            // 3. ADMIN ADDS STAFF
            // -------------------------------------------------

            if (path.equals("/admin/staff")) {

                // Sirf logged-in ADMIN staff create kar sakta hai
                if (!allow(req, resp, "ADMIN")) {
                    return;
                }

                String sid = val(b, "studentId");
                String name = val(b, "name");
                String email = val(b, "email");
                String password = val(b, "password");
                String floor = val(b, "floor");

                // Staff ke liye room number required nahi hai
                if (sid.isBlank()
                        || name.isBlank()
                        || email.isBlank()
                        || password.length() < 8) {

                    send(resp, 400, map(
                            "error",
                            "Staff ID, name, email and password " +
                            "(at least 8 characters) are required."
                    ));

                    return;
                }

                /*
                 * Room number ko blank string set kiya gaya hai.
                 * Isliye staff form se roomNumber nahi bhejna padega.
                 *
                 * Role fixed 'STAFF' hai.
                 * Browser se bheja gaya role accept nahi kiya jayega.
                 */

                String sql =
                        "INSERT INTO users " +
                        "(student_id, full_name, email, password_hash, " +
                        "role, floor_name, room_number) " +
                        "VALUES (?, ?, ?, ?, 'STAFF', ?, ?)";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, sid);
                    ps.setString(2, name);
                    ps.setString(3, email);
                    ps.setString(4, Passwords.hash(password));
                    ps.setString(5, floor);
                    ps.setString(6, "");

                    ps.executeUpdate();

                    send(resp, 201, map(
                            "message",
                            "Staff account created successfully."
                    ));

                } catch (SQLIntegrityConstraintViolationException e) {

                    send(resp, 409, map(
                            "error",
                            "Staff ID or email already exists."
                    ));
                }

                return;
            }

            // -------------------------------------------------
            // 4. LOGOUT
            // -------------------------------------------------

            if (path.equals("/logout")) {

                HttpSession session = req.getSession(false);

                if (session != null) {
                    session.invalidate();
                }

                send(resp, 200, map(
                        "message", "Logged out.",
                        "redirect", "html.html"
                ));

                return;
            }

            // -------------------------------------------------
            // 5. SUBMIT COMPLAINT
            // -------------------------------------------------

            if (path.equals("/complaints")) {

                if (!allow(req, resp, "USER")) {
                    return;
                }

                String floor = val(b, "floor");
                String room = val(b, "roomNumber");
                String category = val(b, "category");
                String description = val(b, "description");

                if (floor.isBlank()
                        || room.isBlank()
                        || category.isBlank()
                        || description.length() < 10) {

                    send(resp, 400, map(
                            "error",
                            "Please complete all fields. Description " +
                            "must contain at least 10 characters."
                    ));

                    return;
                }

                Map<String, Object> prediction =
                        PredictionService.get().predict(description);

                String publicId =
                        "CMP" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();

                try (Connection con = DB.get()) {

                    con.setAutoCommit(false);

                    try {

                        // Logged-in student ka ID lena
                        String studentId;

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "SELECT student_id FROM users WHERE id = ?"
                                     )) {

                            ps.setLong(1, uid(req));

                            try (ResultSet rs = ps.executeQuery()) {

                                if (!rs.next()) {
                                    throw new SQLException(
                                            "User not found."
                                    );
                                }

                                studentId = rs.getString("student_id");
                            }
                        }

                        // Complaint database mein insert karna
                        String insertSql =
                                "INSERT INTO complaints " +
                                "(public_id, user_id, student_id, floor_name, " +
                                "room_number, category, priority, description, " +
                                "predicted_category, predicted_priority, expected_days) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                        long complaintId;

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             insertSql,
                                             Statement.RETURN_GENERATED_KEYS
                                     )) {

                            ps.setString(1, publicId);
                            ps.setLong(2, uid(req));
                            ps.setString(3, studentId);
                            ps.setString(4, floor);
                            ps.setString(5, room);
                            ps.setString(6, category);

                            // Priority is decided by the trained ML model.
                            ps.setString(
                                    7,
                                    String.valueOf(prediction.get("priority"))
                            );

                            ps.setString(8, description);

                            ps.setString(
                                    9,
                                    String.valueOf(
                                            prediction.get("category")
                                    )
                            );

                            ps.setString(
                                    10,
                                    String.valueOf(
                                            prediction.get("priority")
                                    )
                            );

                            ps.setInt(
                                    11,
                                    ((Number) prediction.get("expectedDays"))
                                            .intValue()
                            );

                            ps.executeUpdate();

                            try (ResultSet keys = ps.getGeneratedKeys()) {

                                if (!keys.next()) {
                                    throw new SQLException(
                                            "Complaint ID was not generated."
                                    );
                                }

                                complaintId = keys.getLong(1);
                            }
                        }

                        // Complaint ki initial history add karna
                        String historySql =
                                "INSERT INTO complaint_updates " +
                                "(complaint_id, updated_by, old_status, " +
                                "new_status, note) " +
                                "VALUES (?, ?, NULL, 'Pending', ?)";

                        try (PreparedStatement ps =
                                     con.prepareStatement(historySql)) {

                            ps.setLong(1, complaintId);
                            ps.setLong(2, uid(req));
                            ps.setString(3, "Complaint submitted");

                            ps.executeUpdate();
                        }

                        con.commit();

                        send(resp, 201, map(
                                "message",
                                "Complaint submitted successfully.",
                                "publicId",
                                publicId,
                                "prediction",
                                prediction
                        ));

                    } catch (Exception e) {

                        try {
                            con.rollback();
                        } catch (SQLException ignored) {
                            // Rollback failure ko ignore karna
                        }

                        throw e;
                    }
                }

                return;
            }

            // -------------------------------------------------
            // 6. FEEDBACK FOR RESOLVED COMPLAINT
            // -------------------------------------------------

            if (path.matches("/complaints/[^/]+/feedback")) {

                if (!allow(req, resp, "USER")) {
                    return;
                }

                String publicId = path.split("/")[2];

                int rating = b.has("rating")
                        ? b.get("rating").getAsInt()
                        : 0;

                String comments = val(b, "comments");
                if (rating < 1 || rating > 5) {
                    send(resp, 400, map("error", "Rating must be from 1 to 5."));
                    return;
                }
                if (comments.isBlank()) {
                    send(resp, 400, map("error", "Please write your feedback comment."));
                    return;
                }

                String sql =
                        "INSERT INTO feedback " +
                        "(complaint_id, user_id, rating, comments) " +
                        "SELECT id, ?, ?, ? FROM complaints " +
                        "WHERE public_id = ? AND user_id = ? " +
                        "AND status = 'Resolved' " +
                        "ON DUPLICATE KEY UPDATE " +
                        "rating = VALUES(rating), comments = VALUES(comments)";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setLong(1, uid(req));
                    ps.setInt(2, rating);
                    ps.setString(3, comments);
                    ps.setString(4, publicId);
                    ps.setLong(5, uid(req));

                    int count = ps.executeUpdate();

                    if (count == 0) {

                        send(resp, 400, map(
                                "error",
                                "Feedback is available for your " +
                                "resolved complaints only."
                        ));

                        return;
                    }

                    send(resp, 200, map(
                            "message", "Feedback saved."
                    ));
                }

                return;
            }

            // Unknown POST endpoint
            send(resp, 404, map(
                    "error", "Endpoint not found."
            ));

        } catch (Exception e) {

            e.printStackTrace();

            send(resp, 500, map(
                    "error",
                    "Server error. Check database configuration and server log."
            ));
        }
    }

    // =====================================================
    // PATCH REQUESTS
    // =====================================================

    protected void doPatch(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        String path = req.getPathInfo();

        try {

            JsonObject b = body(req);

            // -------------------------------------------------
            // 1. ADMIN UPDATES COMPLAINT STATUS
            // -------------------------------------------------

            if (path != null
                    && path.matches("/admin/complaints/[^/]+")) {

                if (!allow(req, resp, "ADMIN")) {
                    return;
                }

                String publicId =
                        path.substring(path.lastIndexOf('/') + 1);

                String status = val(b, "status");
                String note = val(b, "note");

                Set<String> validStatuses = Set.of(
                        "Pending",
                        "Assigned",
                        "In Progress",
                        "Resolved",
                        "Rejected"
                );

                if (!validStatuses.contains(status)) {

                    send(resp, 400, map(
                            "error", "Invalid status."
                    ));

                    return;
                }

                try (Connection con = DB.get()) {

                    con.setAutoCommit(false);

                    try {

                        long complaintId;
                        String oldStatus;

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "SELECT id, status FROM complaints " +
                                             "WHERE public_id = ? FOR UPDATE"
                                     )) {

                            ps.setString(1, publicId);

                            try (ResultSet rs = ps.executeQuery()) {

                                if (!rs.next()) {

                                    send(resp, 404, map(
                                            "error", "Complaint not found."
                                    ));

                                    con.rollback();
                                    return;
                                }

                                complaintId = rs.getLong("id");
                                oldStatus = rs.getString("status");
                            }
                        }

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "UPDATE complaints SET status = ?, " +
                                             "resolution_note = ? WHERE id = ?"
                                     )) {

                            ps.setString(1, status);
                            ps.setString(2, note);
                            ps.setLong(3, complaintId);

                            ps.executeUpdate();
                        }

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "INSERT INTO complaint_updates " +
                                             "(complaint_id, updated_by, old_status, " +
                                             "new_status, note) VALUES (?, ?, ?, ?, ?)"
                                     )) {

                            ps.setLong(1, complaintId);
                            ps.setLong(2, uid(req));
                            ps.setString(3, oldStatus);
                            ps.setString(4, status);
                            ps.setString(5, note);

                            ps.executeUpdate();
                        }

                        con.commit();

                        send(resp, 200, map(
                                "message", "Complaint status updated."
                        ));

                    } catch (Exception e) {

                        try {
                            con.rollback();
                        } catch (SQLException ignored) {
                            // Rollback failure ko ignore karna
                        }

                        throw e;
                    }
                }

                return;
            }

            // -------------------------------------------------
            // 2. ADMIN ASSIGNS COMPLAINT TO STAFF
            // -------------------------------------------------

            if (path != null
                    && path.matches("/admin/complaints/[^/]+/assign")) {

                if (!allow(req, resp, "ADMIN")) {
                    return;
                }

                String publicId = path.split("/")[3];

                if (!b.has("staffId")
                        || b.get("staffId").isJsonNull()) {

                    send(resp, 400, map(
                            "error", "Please select a staff member."
                    ));

                    return;
                }

                long staffId;

                try {
                    staffId = b.get("staffId").getAsLong();
                } catch (Exception e) {

                    send(resp, 400, map(
                            "error", "Invalid staff ID."
                    ));

                    return;
                }

                if (staffId <= 0) {

                    send(resp, 400, map(
                            "error", "Invalid staff ID."
                    ));

                    return;
                }

                String sql =
                        "UPDATE complaints SET assigned_staff_id = ?, " +
                        "status = 'Assigned' " +
                        "WHERE public_id = ? " +
                        "AND EXISTS (SELECT 1 FROM users " +
                        "WHERE id = ? AND role = 'STAFF')";

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setLong(1, staffId);
                    ps.setString(2, publicId);
                    ps.setLong(3, staffId);

                    if (ps.executeUpdate() == 0) {

                        send(resp, 400, map(
                                "error",
                                "Complaint or staff member not found."
                        ));

                        return;
                    }

                    send(resp, 200, map(
                            "message", "Staff assigned."
                    ));
                }

                return;
            }

            // -------------------------------------------------
            // 3. STAFF UPDATES ASSIGNED COMPLAINT
            // -------------------------------------------------

            if (path != null
                    && path.matches("/staff/complaints/[^/]+")) {

                if (!allow(req, resp, "STAFF")) {
                    return;
                }

                String publicId =
                        path.substring(path.lastIndexOf('/') + 1);

                String status = val(b, "status");
                String note = val(b, "note");

                if (!Set.of("In Progress", "Resolved").contains(status)) {

                    send(resp, 400, map(
                            "error",
                            "Staff can set In Progress or Resolved."
                    ));

                    return;
                }

                try (Connection con = DB.get()) {

                    con.setAutoCommit(false);

                    try {

                        long complaintId;
                        String oldStatus;

                        String selectSql =
                                "SELECT id, status FROM complaints " +
                                "WHERE public_id = ? AND assigned_staff_id = ? " +
                                "FOR UPDATE";

                        try (PreparedStatement ps =
                                     con.prepareStatement(selectSql)) {

                            ps.setString(1, publicId);
                            ps.setLong(2, uid(req));

                            try (ResultSet rs = ps.executeQuery()) {

                                if (!rs.next()) {

                                    send(resp, 404, map(
                                            "error",
                                            "Assigned complaint not found."
                                    ));

                                    con.rollback();
                                    return;
                                }

                                complaintId = rs.getLong("id");
                                oldStatus = rs.getString("status");
                            }
                        }

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "UPDATE complaints SET status = ?, " +
                                             "resolution_note = ? WHERE id = ?"
                                     )) {

                            ps.setString(1, status);
                            ps.setString(2, note);
                            ps.setLong(3, complaintId);

                            ps.executeUpdate();
                        }

                        try (PreparedStatement ps =
                                     con.prepareStatement(
                                             "INSERT INTO complaint_updates " +
                                             "(complaint_id, updated_by, old_status, " +
                                             "new_status, note) VALUES (?, ?, ?, ?, ?)"
                                     )) {

                            ps.setLong(1, complaintId);
                            ps.setLong(2, uid(req));
                            ps.setString(3, oldStatus);
                            ps.setString(4, status);
                            ps.setString(5, note);

                            ps.executeUpdate();
                        }

                        con.commit();

                        send(resp, 200, map(
                                "message", "Progress updated."
                        ));

                    } catch (Exception e) {

                        try {
                            con.rollback();
                        } catch (SQLException ignored) {
                            // Rollback failure ko ignore karna
                        }

                        throw e;
                    }
                }

                return;
            }

            send(resp, 404, map(
                    "error", "Endpoint not found."
            ));

        } catch (Exception e) {

            e.printStackTrace();

            send(resp, 500, map(
                    "error",
                    "Server error. Check database configuration and server log."
            ));
        }
    }
}
