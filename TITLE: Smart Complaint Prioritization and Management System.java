==== src\main\java\com\complaint\controller\ComplaintServlet.java ====
package com.complaint.controller;

import com.complaint.dao.ComplaintDAO;
import com.complaint.model.Admin;
import com.complaint.model.Citizen;
import com.complaint.model.Complaint;
import com.complaint.model.User;
import com.complaint.util.PriorityCalculator;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Controller servlet handling Complaint actions with object composition mapping.
 */
@WebServlet(name = "ComplaintServlet", urlPatterns = {"/dashboard", "/submit-complaint", "/update-status"})
public class ComplaintServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ComplaintDAO complaintDAO;

    @Override
    public void init() throws ServletException {
        complaintDAO = new ComplaintDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String path = request.getServletPath();

        if ("/dashboard".equals(path)) {
            showDashboard(request, response);
        } else if ("/submit-complaint".equals(path)) {
            request.getRequestDispatcher("/WEB-INF/views/submit-complaint.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String path = request.getServletPath();

        if ("/submit-complaint".equals(path)) {
            handleComplaintSubmission(request, response);
        } else if ("/update-status".equals(path)) {
            handleStatusUpdate(request, response);
        }
    }

    private void showDashboard(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("currentUser");

        try {
            List<Complaint> complaints;
            if ("ADMIN".equals(currentUser.getRole())) {
                complaints = complaintDAO.getAllComplaints();
            } else {
                complaints = complaintDAO.getComplaintsByCitizen(currentUser.getId());
            }

            request.setAttribute("complaints", complaints);
            request.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Database error loading dashboard details", e);
        }
    }

    private void handleComplaintSubmission(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("currentUser");

        if (!"CITIZEN".equals(currentUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only Citizens can submit complaints.");
            return;
        }

        Citizen currentCitizen = (Citizen) currentUser;
        String title = request.getParameter("title");
        String description = request.getParameter("description");
        String category = request.getParameter("category");

        // Dynamic priority calculation
        String priority = PriorityCalculator.calculate(category, description);

        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setCategory(category);
        complaint.setStatus("PENDING");
        complaint.setPriority(priority);
        complaint.setCitizen(currentCitizen); // Link the complete Citizen object

        try {
            boolean success = complaintDAO.insertComplaint(complaint);
            if (success) {
                response.sendRedirect(request.getContextPath() + "/dashboard");
            } else {
                request.setAttribute("errorMessage", "Failed to submit complaint. Try again.");
                request.getRequestDispatcher("/WEB-INF/views/submit-complaint.jsp").forward(request, response);
            }
        } catch (SQLException e) {
            throw new ServletException("Database error during complaint submission", e);
        }
    }

    private void handleStatusUpdate(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("currentUser");

        if (!"ADMIN".equals(currentUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only admins can perform this action");
            return;
        }

        Admin currentAdmin = (Admin) currentUser;
        int complaintId = Integer.parseInt(request.getParameter("complaintId"));
        String status = request.getParameter("status");

        try {
            // Assign the updating admin as resolver to the complaint
            complaintDAO.updateComplaintStatus(complaintId, status, currentAdmin.getId());
            response.sendRedirect(request.getContextPath() + "/dashboard");
        } catch (SQLException e) {
            throw new ServletException("Database error updating complaint status", e);
        }
    }
}


==== src\main\java\com\complaint\controller\UserServlet.java ====
package com.complaint.controller;

import com.complaint.dao.UserDAO;
import com.complaint.model.Citizen;
import com.complaint.model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Controller servlet for handling Citizen and Admin account actions.
 */
@WebServlet(name = "UserServlet", urlPatterns = {"/login", "/register", "/logout"})
public class UserServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        userDAO = new UserDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String path = request.getServletPath();
        
        if ("/logout".equals(path)) {
            handleLogout(request, response);
        } else if ("/login".equals(path)) {
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
        } else if ("/register".equals(path)) {
            request.getRequestDispatcher("/WEB-INF/views/login.jsp?register=true").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String path = request.getServletPath();
        
        if ("/login".equals(path)) {
            handleLogin(request, response);
        } else if ("/register".equals(path)) {
            handleRegistration(request, response);
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try {
            // Polymorphic login check (returns a Citizen or Admin as User base type)
            User user = userDAO.authenticate(username, password);
            if (user != null) {
                HttpSession session = request.getSession();
                session.setAttribute("currentUser", user);
                response.sendRedirect(request.getContextPath() + "/dashboard");
            } else {
                request.setAttribute("errorMessage", "Invalid Username or Password.");
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            }
        } catch (SQLException e) {
            throw new ServletException("Database error during login flow", e);
        }
    }

    private void handleRegistration(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String email = request.getParameter("email");
        String fullName = request.getParameter("fullName");
        String phone = request.getParameter("phone");
        String address = request.getParameter("address");

        // Construct concrete Citizen object
        Citizen citizen = new Citizen();
        citizen.setUsername(username);
        citizen.setPassword(password); // Raw for seed; hash in production
        citizen.setEmail(email);
        citizen.setFullName(fullName);
        citizen.setPhone(phone);
        citizen.setAddress(address);

        try {
            boolean success = userDAO.registerCitizen(citizen);
            if (success) {
                request.setAttribute("successMessage", "Registration successful! Please login.");
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            } else {
                request.setAttribute("errorMessage", "Registration failed. Try again.");
                request.getRequestDispatcher("/WEB-INF/views/login.jsp?register=true").forward(request, response);
            }
        } catch (SQLException e) {
            throw new ServletException("Database error during citizen registration", e);
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }
}


==== src\main\java\com\complaint\dao\ComplaintDAO.java ====
package com.complaint.dao;

import com.complaint.model.Admin;
import com.complaint.model.Citizen;
import com.complaint.model.Complaint;
import com.complaint.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for managing Complaints with relational JOIN operations 
 * across 'users' (citizens) and 'admins' tables.
 */
public class ComplaintDAO {

    /**
     * Submits a new complaint.
     */
    public boolean insertComplaint(Complaint complaint) throws SQLException {
        String query = "INSERT INTO complaints (title, description, category, status, priority, citizen_id, created_at, updated_at) " +
                       "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            ps.setString(1, complaint.getTitle());
            ps.setString(2, complaint.getDescription());
            ps.setString(3, complaint.getCategory());
            ps.setString(4, complaint.getStatus());
            ps.setString(5, complaint.getPriority());
            ps.setInt(6, complaint.getCitizen().getId());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves all complaints submitted by a specific citizen.
     * Uses INNER JOIN with 'users' to construct the reporting Citizen object.
     */
    public List<Complaint> getComplaintsByCitizen(int citizenId) throws SQLException {
        List<Complaint> list = new ArrayList<>();
        String query = "SELECT c.*, u.username, u.email, u.full_name, u.phone, u.address " +
                       "FROM complaints c " +
                       "INNER JOIN users u ON c.citizen_id = u.id " +
                       "WHERE c.citizen_id = ? " +
                       "ORDER BY c.created_at DESC";
                       
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            ps.setInt(1, citizenId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all complaints in the database (For Admins).
     * Uses LEFT JOINs with both 'users' and 'admins' to load complete object graphs.
     * Sorts complaints dynamically by priority weight and registration time.
     */
    public List<Complaint> getAllComplaints() throws SQLException {
        List<Complaint> list = new ArrayList<>();
        String query = "SELECT c.*, " +
                       "u.username AS citizen_username, u.email AS citizen_email, u.full_name AS citizen_name, u.phone, u.address, " +
                       "a.username AS admin_username, a.email AS admin_email, a.full_name AS admin_name, a.department, a.employee_id " +
                       "FROM complaints c " +
                       "INNER JOIN users u ON c.citizen_id = u.id " +
                       "LEFT JOIN admins a ON c.admin_id = a.id " +
                       "ORDER BY " +
                       "CASE c.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END, " +
                       "c.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                list.add(mapResultSetRowForAdmin(rs));
            }
        }
        return list;
    }

    /**
     * Updates status and optionally assigns the resolving Admin.
     */
    public boolean updateComplaintStatus(int complaintId, String status, int adminId) throws SQLException {
        String query = "UPDATE complaints SET status = ?, admin_id = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            ps.setString(1, status);
            ps.setInt(2, adminId);
            ps.setInt(3, complaintId);

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Helper to map a basic result set row (with citizen join details).
     */
    private Complaint mapResultSetRow(ResultSet rs) throws SQLException {
        Complaint c = new Complaint();
        c.setId(rs.getInt("id"));
        c.setTitle(rs.getString("title"));
        c.setDescription(rs.getString("description"));
        c.setCategory(rs.getString("category"));
        c.setStatus(rs.getString("status"));
        c.setPriority(rs.getString("priority"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Construct Citizen
        Citizen citizen = new Citizen();
        citizen.setId(rs.getInt("citizen_id"));
        citizen.setUsername(rs.getString("username"));
        citizen.setEmail(rs.getString("email"));
        citizen.setFullName(rs.getString("full_name"));
        citizen.setPhone(rs.getString("phone"));
        citizen.setAddress(rs.getString("address"));
        c.setCitizen(citizen);

        return c;
    }

    /**
     * Helper to map admin-centric queries containing citizen and admin columns.
     */
    private Complaint mapResultSetRowForAdmin(ResultSet rs) throws SQLException {
        Complaint c = new Complaint();
        c.setId(rs.getInt("id"));
        c.setTitle(rs.getString("title"));
        c.setDescription(rs.getString("description"));
        c.setCategory(rs.getString("category"));
        c.setStatus(rs.getString("status"));
        c.setPriority(rs.getString("priority"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Construct Citizen
        Citizen citizen = new Citizen();
        citizen.setId(rs.getInt("citizen_id"));
        citizen.setUsername(rs.getString("citizen_username"));
        citizen.setEmail(rs.getString("citizen_email"));
        citizen.setFullName(rs.getString("citizen_name"));
        citizen.setPhone(rs.getString("phone"));
        citizen.setAddress(rs.getString("address"));
        c.setCitizen(citizen);

        // Construct Admin (if assigned)
        int adminId = rs.getInt("admin_id");
        if (!rs.wasNull()) {
            Admin admin = new Admin();
            admin.setId(adminId);
            admin.setUsername(rs.getString("admin_username"));
            admin.setEmail(rs.getString("admin_email"));
            admin.setFullName(rs.getString("admin_name"));
            admin.setDepartment(rs.getString("department"));
            admin.setEmployeeId(rs.getString("employee_id"));
            c.setAssignedAdmin(admin);
        }

        return c;
    }
}


==== src\main\java\com\complaint\dao\UserDAO.java ====
package com.complaint.dao;

import com.complaint.model.Admin;
import com.complaint.model.Citizen;
import com.complaint.model.User;
import com.complaint.util.DBConnection;
import com.complaint.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object (DAO) for managing User authentication and persistence across
 * separate 'users' (citizens) and 'admins' database tables.
 */
public class UserDAO {

    public UserDAO() {
        // Default constructor
    }

    /**
     * Authenticates a user (either a Citizen or an Admin) based on credentials.
     * Demonstrates polymorphism: returns a subclass instance as a User reference.
     * 
     * @return User subclass instance if authenticated, null otherwise.
     */
    public User authenticate(String username, String password) throws SQLException {
        // Hash the incoming password to match database records
        String hashedPassword = PasswordUtil.hash(password);

        // 1. Check users (citizens) table
        String userQuery = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(userQuery)) {
            
            ps.setString(1, username);
            ps.setString(2, hashedPassword);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Citizen citizen = new Citizen();
                    citizen.setId(rs.getInt("id"));
                    citizen.setUsername(rs.getString("username"));
                    citizen.setPassword(rs.getString("password"));
                    citizen.setEmail(rs.getString("email"));
                    citizen.setFullName(rs.getString("full_name"));
                    citizen.setPhone(rs.getString("phone"));
                    citizen.setAddress(rs.getString("address"));
                    return citizen;
                }
            }
        }

        // 2. Check admins table
        String adminQuery = "SELECT * FROM admins WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(adminQuery)) {
            
            ps.setString(1, username);
            ps.setString(2, hashedPassword);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Admin admin = new Admin();
                    admin.setId(rs.getInt("id"));
                    admin.setUsername(rs.getString("username"));
                    admin.setPassword(rs.getString("password"));
                    admin.setEmail(rs.getString("email"));
                    admin.setFullName(rs.getString("full_name"));
                    admin.setDepartment(rs.getString("department"));
                    admin.setEmployeeId(rs.getString("employee_id"));
                    return admin;
                }
            }
        }

        return null;
    }

    /**
     * Registers a new Citizen into the 'users' table.
     */
    public boolean registerCitizen(Citizen citizen) throws SQLException {
        String query = "INSERT INTO users (username, password, email, full_name, phone, address) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            ps.setString(1, citizen.getUsername());
            ps.setString(2, PasswordUtil.hash(citizen.getPassword())); // Secure password storage
            ps.setString(3, citizen.getEmail());
            ps.setString(4, citizen.getFullName());
            ps.setString(5, citizen.getPhone());
            ps.setString(6, citizen.getAddress());

            return ps.executeUpdate() > 0;
        }
    }
}


==== src\main\java\com\complaint\model\Admin.java ====
package com.complaint.model;

/**
 * Concrete class representing an Administrator/Staff, extending the abstract User class.
 */
public class Admin extends User {
    private String department;
    private String employeeId;

    // Default constructor
    public Admin() {
        super();
        setRole("ADMIN");
    }

    // Parameterized constructor
    public Admin(int id, String username, String password, String email, String fullName, 
                 String department, String employeeId) {
        super(id, username, password, email, fullName, "ADMIN");
        this.department = department;
        this.employeeId = employeeId;
    }

    // Getters and Setters
    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    @Override
    public String toString() {
        return "Admin{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", department='" + department + '\'' +
                ", employeeId='" + employeeId + '\'' +
                '}';
    }
}


==== src\main\java\com\complaint\model\Citizen.java ====
package com.complaint.model;

/**
 * Concrete class representing a Citizen, extending the abstract User class.
 */
public class Citizen extends User {
    private String phone;
    private String address;

    // Default constructor
    public Citizen() {
        super();
        setRole("CITIZEN");
    }

    // Parameterized constructor
    public Citizen(int id, String username, String password, String email, String fullName, 
                   String phone, String address) {
        super(id, username, password, email, fullName, "CITIZEN");
        this.phone = phone;
        this.address = address;
    }

    // Getters and Setters
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "Citizen{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}


==== src\main\java\com\complaint\model\Complaint.java ====
package com.complaint.model;

import java.sql.Timestamp;

/**
 * Model class representing a Complaint, linking to Citizen and Admin OOP structures.
 */
public class Complaint {
    private int id;
    private String title;
    private String description;
    private String category;
    private String status;   // "PENDING", "IN_PROGRESS", "RESOLVED"
    private String priority; // "HIGH", "MEDIUM", "LOW"
    
    // OOP Associations (Composition/Aggregation)
    private Citizen citizen; // The reporting citizen
    private Admin assignedAdmin; // The assigned admin/staff
    
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Default constructor
    public Complaint() {}

    // Parameterized constructor
    public Complaint(int id, String title, String description, String category, 
                     String status, String priority, Citizen citizen, Admin assignedAdmin,
                     Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.status = status;
        this.priority = priority;
        this.citizen = citizen;
        this.assignedAdmin = assignedAdmin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Citizen getCitizen() {
        return citizen;
    }

    public void setCitizen(Citizen citizen) {
        this.citizen = citizen;
    }

    public Admin getAssignedAdmin() {
        return assignedAdmin;
    }

    public void setAssignedAdmin(Admin assignedAdmin) {
        this.assignedAdmin = assignedAdmin;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Complaint{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", priority='" + priority + '\'' +
                ", citizen=" + (citizen != null ? citizen.getFullName() : "null") +
                ", assignedAdmin=" + (assignedAdmin != null ? assignedAdmin.getFullName() : "none") +
                '}';
    }
}


==== src\main\java\com\complaint\model\User.java ====
package com.complaint.model;

/**
 * Abstract base class representing a generic User in the system.
 * Part of the OOP inheritance hierarchy (User -> Citizen / Admin).
 */
public abstract class User {
    private int id;
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String role; // "CITIZEN" or "ADMIN"

    // Default constructor
    public User() {}

    // Parameterized constructor
    public User(int id, String username, String password, String email, String fullName, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}


==== src\main\java\com\complaint\util\DBConnection.java ====
package com.complaint.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class to handle MySQL database connection using JDBC.
 */
public class DBConnection {
    // Database connection parameters
    private static final String URL = "jdbc:h2:file:./complaint_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "password";

    static {
        try {
            // Load the H2 JDBC driver
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("H2 JDBC Driver not found. Ensure the dependency is added.");
            e.printStackTrace();
        }
    }

    /**
     * Obtains a Connection to the MySQL database.
     * @return Connection object
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Utility method to close connection resources safely.
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}


==== src\main\java\com\complaint\util\PasswordUtil.java ====
package com.complaint.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for hashing and verifying passwords using SHA-256.
 */
public class PasswordUtil {

    /**
     * Hashes a plain text password using SHA-256.
     * 
     * @param password Plain text password
     * @return Hexadecimal representation of the SHA-256 hash
     */
    public static String hash(String password) {
        if (password == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            
            // Convert byte array into signum representation
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Checks if a plain password matches a hashed password.
     * 
     * @param plainPassword Plain text password
     * @param hashedPassword Hashed password to compare against
     * @return true if they match
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return hash(plainPassword).equals(hashedPassword);
    }
}


==== src\main\java\com\complaint\util\PriorityCalculator.java ====
package com.complaint.util;

/**
 * Utility to calculate the priority of a complaint automatically based on 
 * keywords, severity, and category.
 */
public class PriorityCalculator {

    /**
     * Calculates priority: "HIGH", "MEDIUM", or "LOW"
     * 
     * @param category    Complaint category
     * @param description Complaint description
     * @return Calculated priority level
     */
    public static String calculate(String category, String description) {
        if (category == null || description == null) {
            return "LOW";
        }

        String descLower = description.toLowerCase();

        // Critical safety keywords
        boolean isCritical = descLower.contains("fire") 
                || descLower.contains("danger") 
                || descLower.contains("leak") 
                || descLower.contains("hazard") 
                || descLower.contains("injury") 
                || descLower.contains("blast");

        // High priority categories or key indicators
        if (isCritical) {
            return "HIGH";
        }

        if ("Water Supply".equalsIgnoreCase(category) && descLower.contains("no water")) {
            return "HIGH";
        }

        if ("Electricity".equalsIgnoreCase(category) && (descLower.contains("spark") || descLower.contains("blackout"))) {
            return "HIGH";
        }

        // Medium logic
        if (descLower.contains("broken") 
                || descLower.contains("not working") 
                || descLower.contains("overflow")) {
            return "MEDIUM";
        }

        // Default priority
        return "LOW";
    }
}


==== src\main\webapp\assets\css\style.css ====
/* Modern, Sleek, and Professional Design System for Complaint System */

@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

:root {
    --bg-primary: #0f172a;
    --bg-secondary: #1e293b;
    --bg-card: rgba(30, 41, 59, 0.7);
    --border-color: rgba(255, 255, 255, 0.08);
    
    --text-primary: #f8fafc;
    --text-secondary: #94a3b8;
    --text-muted: #64748b;
    
    --accent-blue: #3b82f6;
    --accent-blue-hover: #2563eb;
    
    --success: #10b981;
    --warning: #f59e0b;
    --danger: #ef4444;
    
    --priority-high: #f87171;
    --priority-medium: #fbbf24;
    --priority-low: #34d399;

    --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
    --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
    --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
    --shadow-glass: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
    --transition-smooth: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    --radius-lg: 12px;
    --radius-md: 8px;
}

* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

body {
    background-color: var(--bg-primary);
    color: var(--text-primary);
    font-family: var(--font-sans);
    line-height: 1.6;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
}

/* Glassmorphism Header */
header {
    background: rgba(15, 23, 42, 0.85);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border-color);
    position: sticky;
    top: 0;
    z-index: 100;
}

.nav-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 1.25rem 2rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.logo {
    font-size: 1.25rem;
    font-weight: 700;
    color: #fff;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    letter-spacing: -0.025em;
}

.logo span {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

.nav-links {
    display: flex;
    align-items: center;
    gap: 1.5rem;
}

.nav-links a {
    color: var(--text-secondary);
    text-decoration: none;
    font-weight: 500;
    font-size: 0.875rem;
    transition: var(--transition-smooth);
}

.nav-links a:hover {
    color: var(--text-primary);
}

.btn-logout {
    background: rgba(239, 68, 68, 0.1);
    color: var(--danger) !important;
    padding: 0.5rem 1rem;
    border-radius: var(--radius-md);
    border: 1px solid rgba(239, 68, 68, 0.2);
}

.btn-logout:hover {
    background: var(--danger) !important;
    color: #fff !important;
}

/* Authentication Forms Layout */
.auth-wrapper {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 3rem 1rem;
    background: radial-gradient(circle at top right, rgba(59, 130, 246, 0.08), transparent 45%),
                radial-gradient(circle at bottom left, rgba(16, 185, 129, 0.05), transparent 45%);
}

.auth-card {
    background: var(--bg-card);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-glass);
    border-radius: var(--radius-lg);
    width: 100%;
    max-width: 440px;
    padding: 2.5rem;
}

.auth-header {
    text-align: center;
    margin-bottom: 2rem;
}

.auth-header h2 {
    font-size: 1.75rem;
    font-weight: 700;
    margin-bottom: 0.5rem;
    letter-spacing: -0.02em;
}

.auth-header p {
    color: var(--text-secondary);
    font-size: 0.9rem;
}

/* Form Styling */
.form-group {
    margin-bottom: 1.25rem;
}

.form-label {
    display: block;
    margin-bottom: 0.5rem;
    font-size: 0.85rem;
    font-weight: 500;
    color: var(--text-secondary);
}

.form-input {
    width: 100%;
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: 0.75rem 1rem;
    color: var(--text-primary);
    font-size: 0.95rem;
    transition: var(--transition-smooth);
}

.form-input:focus {
    outline: none;
    border-color: var(--accent-blue);
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.btn-primary {
    width: 100%;
    background: var(--accent-blue);
    color: #fff;
    border: none;
    border-radius: var(--radius-md);
    padding: 0.75rem;
    font-size: 0.95rem;
    font-weight: 600;
    cursor: pointer;
    transition: var(--transition-smooth);
    margin-top: 0.75rem;
}

.btn-primary:hover {
    background: var(--accent-blue-hover);
    transform: translateY(-1px);
}

.btn-primary:active {
    transform: translateY(0);
}

/* Toggle Registration View (HTML/CSS Only Switcher) */
.auth-switch {
    display: none;
}

.auth-tabs {
    display: flex;
    border-bottom: 1px solid var(--border-color);
    margin-bottom: 2rem;
}

.auth-tab-label {
    flex: 1;
    text-align: center;
    padding: 0.75rem;
    font-size: 0.95rem;
    color: var(--text-secondary);
    cursor: pointer;
    font-weight: 600;
    transition: var(--transition-smooth);
}

#tab-login:checked ~ .auth-tabs label[for="tab-login"],
#tab-register:checked ~ .auth-tabs label[for="tab-register"] {
    color: var(--text-primary);
    border-bottom: 2px solid var(--accent-blue);
}

#tab-login:checked ~ #login-form {
    display: block;
}
#tab-login:checked ~ #register-form {
    display: none;
}

#tab-register:checked ~ #login-form {
    display: none;
}
#tab-register:checked ~ #register-form {
    display: block;
}

/* Alert Notification Boxes */
.alert {
    padding: 0.75rem 1rem;
    border-radius: var(--radius-md);
    font-size: 0.875rem;
    margin-bottom: 1.25rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    border: 1px solid transparent;
}

.alert-danger {
    background: rgba(239, 68, 68, 0.1);
    color: #f87171;
    border-color: rgba(239, 68, 68, 0.2);
}

.alert-success {
    background: rgba(16, 185, 129, 0.1);
    color: #34d399;
    border-color: rgba(16, 185, 129, 0.2);
}

/* Dashboard Container */
.container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 2.5rem 2rem;
    width: 100%;
    flex: 1;
}

.dashboard-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2.5rem;
}

.dashboard-header h1 {
    font-size: 2rem;
    font-weight: 700;
    letter-spacing: -0.025em;
}

.dashboard-header p {
    color: var(--text-secondary);
    font-size: 0.95rem;
}

.btn-action {
    background: var(--accent-blue);
    color: #fff;
    text-decoration: none;
    padding: 0.75rem 1.25rem;
    border-radius: var(--radius-md);
    font-weight: 600;
    font-size: 0.9rem;
    transition: var(--transition-smooth);
    border: none;
    cursor: pointer;
}

.btn-action:hover {
    background: var(--accent-blue-hover);
    transform: translateY(-1px);
}

/* Priorities & Badges */
.badge {
    display: inline-flex;
    align-items: center;
    padding: 0.25rem 0.6rem;
    border-radius: 9999px;
    font-size: 0.75rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    text-transform: uppercase;
}

.badge-high {
    background: rgba(248, 113, 113, 0.15);
    color: var(--priority-high);
}

.badge-medium {
    background: rgba(251, 191, 36, 0.15);
    color: var(--priority-medium);
}

.badge-low {
    background: rgba(52, 211, 153, 0.15);
    color: var(--priority-low);
}

.badge-pending {
    background: rgba(245, 158, 11, 0.15);
    color: var(--warning);
}

.badge-progress {
    background: rgba(59, 130, 246, 0.15);
    color: var(--accent-blue);
}

.badge-resolved {
    background: rgba(16, 185, 129, 0.15);
    color: var(--success);
}

/* Complaint Board / Cards Grid */
.complaint-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 1.5rem;
}

.complaint-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    transition: var(--transition-smooth);
}

.complaint-card:hover {
    transform: translateY(-3px);
    border-color: rgba(255, 255, 255, 0.15);
    box-shadow: var(--shadow-lg);
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 1rem;
}

.card-category {
    font-size: 0.75rem;
    color: var(--text-muted);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.card-title {
    font-size: 1.15rem;
    font-weight: 600;
    margin-bottom: 0.5rem;
    line-height: 1.4;
}

.card-desc {
    color: var(--text-secondary);
    font-size: 0.9rem;
    margin-bottom: 1.5rem;
    flex: 1;
}

.card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1px solid var(--border-color);
    padding-top: 1rem;
    font-size: 0.8rem;
    color: var(--text-muted);
}

/* Admin Dashboard Table */
.table-wrapper {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    overflow: hidden;
    margin-top: 1rem;
    box-shadow: var(--shadow-md);
}

.complaint-table {
    width: 100%;
    border-collapse: collapse;
    text-align: left;
    font-size: 0.9rem;
}

.complaint-table th, 
.complaint-table td {
    padding: 1rem 1.5rem;
    border-bottom: 1px solid var(--border-color);
}

.complaint-table th {
    background: rgba(15, 23, 42, 0.4);
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    font-size: 0.75rem;
    letter-spacing: 0.05em;
}

.complaint-table tbody tr {
    transition: var(--transition-smooth);
}

.complaint-table tbody tr:hover {
    background: rgba(255, 255, 255, 0.02);
}

.status-form {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
}

.select-status {
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    color: var(--text-primary);
    font-size: 0.8rem;
    padding: 0.35rem 0.5rem;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: var(--transition-smooth);
}

.select-status:focus {
    border-color: var(--accent-blue);
    outline: none;
}

.btn-update {
    background: rgba(255, 255, 255, 0.05);
    color: var(--text-primary);
    font-size: 0.75rem;
    padding: 0.35rem 0.75rem;
    border-radius: var(--radius-md);
    border: 1px solid var(--border-color);
    cursor: pointer;
    font-weight: 500;
    transition: var(--transition-smooth);
}

.btn-update:hover {
    background: var(--accent-blue);
    border-color: var(--accent-blue);
}

/* Footer styling */
footer {
    text-align: center;
    padding: 2rem;
    color: var(--text-muted);
    font-size: 0.8rem;
    border-top: 1px solid var(--border-color);
    background: rgba(15, 23, 42, 0.5);
    margin-top: auto;
}


==== src\main\webapp\WEB-INF\views\common\footer.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<footer>
    <div class="footer-content">
        <p>&copy; 2026 Smart Complaint Prioritization and Management System. Designed for civic resolution.</p>
    </div>
</footer>

</body>
</html>


==== src\main\webapp\WEB-INF\views\common\header.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Smart Complaint Management System</title>
    
    <!-- Embed direct link to CSS styling sheet -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>

<header>
    <div class="nav-container">
        <a href="${pageContext.request.contextPath}/dashboard" class="logo">
            <span>Smart</span>Complaint
        </a>
        
        <nav class="nav-links">
            <c:if test="${not empty sessionScope.currentUser}">
                <span class="welcome-text">Hello, <strong><c:out value="${sessionScope.currentUser.fullName}"/></strong></span>
                
                <c:if test="${sessionScope.currentUser.role == 'CITIZEN'}">
                    <a href="${pageContext.request.contextPath}/dashboard">My Complaints</a>
                    <a href="${pageContext.request.contextPath}/submit-complaint" class="btn-action">File Complaint</a>
                </c:if>
                <c:if test="${sessionScope.currentUser.role == 'ADMIN'}">
                    <a href="${pageContext.request.contextPath}/dashboard">Admin Console</a>
                </c:if>
                
                <a href="${pageContext.request.contextPath}/logout" class="btn-logout">Logout</a>
            </c:if>
            <c:if test="${empty sessionScope.currentUser}">
                <a href="${pageContext.request.contextPath}/login">Login / Register</a>
            </c:if>
        </nav>
    </div>
</header>


==== src\main\webapp\WEB-INF\views\dashboard.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<jsp:include page="common/header.jsp"/>

<div class="container">
    
    <div class="dashboard-header">
        <div>
            <h1>Dashboard</h1>
            <p>Welcome to your control panel, <strong><c:out value="${sessionScope.currentUser.fullName}"/></strong> (<c:out value="${sessionScope.currentUser.role}"/>).</p>
        </div>
        <c:if test="${sessionScope.currentUser.role == 'CITIZEN'}">
            <a href="${pageContext.request.contextPath}/submit-complaint" class="btn-action">File New Complaint</a>
        </c:if>
    </div>

    <!-- Conditional View: Admin Console vs. Citizen Track Board -->
    <c:choose>
        
        <c:when test="${sessionScope.currentUser.role == 'ADMIN'}">
            <h2 style="margin-bottom: 1rem; font-size: 1.25rem; font-weight: 600;">All Reported Complaints (Sorted by AI Priority)</h2>
            <div class="table-wrapper">
                <table class="complaint-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Title</th>
                            <th>Citizen Details</th>
                            <th>Category</th>
                            <th>Priority</th>
                            <th>Status</th>
                            <th>Resolver</th>
                            <th>Created Date</th>
                            <th>Update Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="complaint" items="${complaints}">
                            <tr>
                                <td>#<c:out value="${complaint.id}"/></td>
                                <td>
                                    <strong><c:out value="${complaint.title}"/></strong>
                                    <div style="font-size: 0.8rem; color: var(--text-secondary); margin-top: 0.25rem;">
                                        <c:out value="${complaint.description}"/>
                                    </div>
                                </td>
                                <td>
                                    <div style="font-weight: 500;"><c:out value="${complaint.citizen.fullName}"/></div>
                                    <div style="font-size: 0.75rem; color: var(--text-secondary);">Phone: <c:out value="${complaint.citizen.phone}"/></div>
                                    <div style="font-size: 0.75rem; color: var(--text-secondary);">Add: <c:out value="${complaint.citizen.address}"/></div>
                                </td>
                                <td><c:out value="${complaint.category}"/></td>
                                <td>
                                    <span class="badge badge-${complaint.priority.toLowerCase()}">
                                        <c:out value="${complaint.priority}"/>
                                    </span>
                                </td>
                                <td>
                                    <span class="badge badge-${complaint.status == 'IN_PROGRESS' ? 'progress' : complaint.status.toLowerCase()}">
                                        <c:out value="${complaint.status}"/>
                                    </span>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${not empty complaint.assignedAdmin}">
                                            <span style="font-weight: 500;"><c:out value="${complaint.assignedAdmin.fullName}"/></span>
                                            <div style="font-size: 0.75rem; color: var(--text-secondary);"><c:out value="${complaint.assignedAdmin.department}"/></div>
                                        </c:when>
                                        <c:otherwise>
                                            <span style="color: var(--text-muted); font-style: italic;">Unassigned</span>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <fmt:formatDate value="${complaint.createdAt}" pattern="yyyy-MM-dd HH:mm"/>
                                </td>
                                <td>
                                    <form action="${pageContext.request.contextPath}/update-status" method="POST" class="status-form">
                                        <input type="hidden" name="complaintId" value="${complaint.id}"/>
                                        <select name="status" class="select-status">
                                            <option value="PENDING" ${complaint.status == 'PENDING' ? 'selected' : ''}>Pending</option>
                                            <option value="IN_PROGRESS" ${complaint.status == 'IN_PROGRESS' ? 'selected' : ''}>In Progress</option>
                                            <option value="RESOLVED" ${complaint.status == 'RESOLVED' ? 'selected' : ''}>Resolved</option>
                                        </select>
                                        <button type="submit" class="btn-update">Update</button>
                                    </form>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty complaints}">
                            <tr>
                                <td colspan="9" style="text-align: center; color: var(--text-muted);">No complaints logged in the system.</td>
                            </tr>
                        </c:if>
                    </tbody>
                </table>
            </div>
        </c:when>

        <c:otherwise>
            <h2 style="margin-bottom: 1.5rem; font-size: 1.25rem; font-weight: 600;">My Submitted Incidents</h2>
            <div class="complaint-grid">
                <c:forEach var="complaint" items="${complaints}">
                    <div class="complaint-card">
                        <div>
                            <div class="card-header">
                                <span class="card-category"><c:out value="${complaint.category}"/></span>
                                <span class="badge badge-${complaint.priority.toLowerCase()}">
                                    Priority: <c:out value="${complaint.priority}"/>
                                </span>
                            </div>
                            <h3 class="card-title"><c:out value="${complaint.title}"/></h3>
                            <p class="card-desc"><c:out value="${complaint.description}"/></p>
                        </div>
                        <div class="card-footer">
                            <span>Status: 
                                <span class="badge badge-${complaint.status == 'IN_PROGRESS' ? 'progress' : complaint.status.toLowerCase()}">
                                    <c:out value="${complaint.status}"/>
                                </span>
                            </span>
                            <span>
                                <fmt:formatDate value="${complaint.createdAt}" pattern="yyyy-MM-dd"/>
                            </span>
                        </div>
                    </div>
                </c:forEach>
            </div>
            
            <c:if test="${empty complaints}">
                <div style="background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); padding: 3rem; text-align: center;">
                    <h3 style="margin-bottom: 0.5rem; font-weight: 600;">You haven't filed any complaints yet</h3>
                    <p style="color: var(--text-secondary); margin-bottom: 1.5rem; font-size: 0.9rem;">Submit civic issues to receive assistance and automatic priority resolution.</p>
                    <a href="${pageContext.request.contextPath}/submit-complaint" class="btn-action">File Your First Complaint</a>
                </div>
            </c:if>
        </c:otherwise>
        
    </c:choose>

</div>

<jsp:include page="common/footer.jsp"/>


==== src\main\webapp\WEB-INF\views\login.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<jsp:include page="common/header.jsp"/>

<div class="auth-wrapper">
    <div class="auth-card">
        
        <!-- Radio triggers for switching between Login and Registration without JS -->
        <input type="radio" id="tab-login" name="auth-tab" class="auth-switch" ${empty param.register ? 'checked' : ''}/>
        <input type="radio" id="tab-register" name="auth-tab" class="auth-switch" ${not empty param.register ? 'checked' : ''}/>
        
        <div class="auth-tabs">
            <label for="tab-login" class="auth-tab-label">Login</label>
            <label for="tab-register" class="auth-tab-label">Register</label>
        </div>

        <!-- System Alerts for Error/Success Messages -->
        <c:if test="${not empty errorMessage}">
            <div class="alert alert-danger">
                <span>&#9888;</span> <c:out value="${errorMessage}"/>
            </div>
        </c:if>
        <c:if test="${not empty successMessage}">
            <div class="alert alert-success">
                <span>&#10004;</span> <c:out value="${successMessage}"/>
            </div>
        </c:if>

        <!-- Login Form -->
        <form action="${pageContext.request.contextPath}/login" method="POST" id="login-form">
            <div class="auth-header">
                <h2>Welcome Back</h2>
                <p>Login to file and track your civic complaints.</p>
            </div>
            
            <div class="form-group">
                <label for="login-username" class="form-label">Username</label>
                <input type="text" id="login-username" name="username" class="form-input" required autocomplete="username"/>
            </div>
            
            <div class="form-group">
                <label for="login-password" class="form-label">Password</label>
                <input type="password" id="login-password" name="password" class="form-input" required autocomplete="current-password"/>
            </div>
            
            <button type="submit" class="btn-primary">Sign In</button>
        </form>

        <!-- Registration Form -->
        <form action="${pageContext.request.contextPath}/register" method="POST" id="register-form">
            <div class="auth-header">
                <h2>Create Account</h2>
                <p>Register as a citizen to submit issues immediately.</p>
            </div>
            
            <div class="form-group">
                <label for="reg-fullname" class="form-label">Full Name</label>
                <input type="text" id="reg-fullname" name="fullName" class="form-input" required autocomplete="name"/>
            </div>

            <div class="form-group">
                <label for="reg-username" class="form-label">Username</label>
                <input type="text" id="reg-username" name="username" class="form-input" required autocomplete="username"/>
            </div>

            <div class="form-group">
                <label for="reg-email" class="form-label">Email Address</label>
                <input type="email" id="reg-email" name="email" class="form-input" required autocomplete="email"/>
            </div>

            <div class="form-group">
                <label for="reg-phone" class="form-label">Phone Number</label>
                <input type="tel" id="reg-phone" name="phone" class="form-input" placeholder="e.g., 9876543210"/>
            </div>

            <div class="form-group">
                <label for="reg-address" class="form-label">Residential Address</label>
                <input type="text" id="reg-address" name="address" class="form-input" placeholder="e.g., 123 Street Name, Town"/>
            </div>
            
            <div class="form-group">
                <label for="reg-password" class="form-label">Password</label>
                <input type="password" id="reg-password" name="password" class="form-input" required autocomplete="new-password"/>
            </div>
            
            <button type="submit" class="btn-primary">Create Account</button>
        </form>

    </div>
</div>

<jsp:include page="common/footer.jsp"/>


==== src\main\webapp\WEB-INF\views\submit-complaint.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<jsp:include page="common/header.jsp"/>

<div class="auth-wrapper">
    <div class="auth-card" style="max-width: 600px;">
        <div class="auth-header">
            <h2>Submit Civic Complaint</h2>
            <p>Describe the issue in detail. Our system will dynamically assess priority based on descriptions.</p>
        </div>

        <c:if test="${not empty errorMessage}">
            <div class="alert alert-danger">
                <span>&#9888;</span> <c:out value="${errorMessage}"/>
            </div>
        </c:if>

        <form action="${pageContext.request.contextPath}/submit-complaint" method="POST">
            
            <div class="form-group">
                <label for="complaint-category" class="form-label">Category</label>
                <select id="complaint-category" name="category" class="form-input" required style="background-color: var(--bg-primary); color: var(--text-primary); cursor: pointer;">
                    <option value="" disabled selected>-- Select Incident Category --</option>
                    <option value="Electricity">Electricity (Power blackout, sparking grid, fallen wire)</option>
                    <option value="Water Supply">Water Supply (Pipeline burst, contamination, supply cuts)</option>
                    <option value="Roads">Roads & Transport (Potholes, blocked signals, streetlights broken)</option>
                    <option value="Sanitation">Sanitation & Garbage (Dump overflow, drainage block, sewage leak)</option>
                    <option value="Others">Others</option>
                </select>
            </div>

            <div class="form-group">
                <label for="complaint-title" class="form-label">Short Summary (Title)</label>
                <input type="text" id="complaint-title" name="title" class="form-input" placeholder="e.g., Water main burst causing heavy street logging" required maxLength="100"/>
            </div>

            <div class="form-group">
                <label for="complaint-desc" class="form-label">Detailed Description</label>
                <textarea id="complaint-desc" name="description" class="form-input" rows="6" placeholder="Describe the incident. Specify landmarks, impact scope, or immediate safety hazards. Use words like fire, hazard, danger, or leak for automatic priority escalation." required></textarea>
            </div>

            <div style="display: flex; gap: 1rem; align-items: center; margin-top: 1.5rem;">
                <button type="submit" class="btn-primary" style="margin: 0; flex: 2;">Submit Complaint</button>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn-action" style="flex: 1; text-align: center; background: transparent; border: 1px solid var(--border-color); color: var(--text-secondary);">Cancel</a>
            </div>

        </form>
    </div>
</div>

<jsp:include page="common/footer.jsp"/>


==== src\main\webapp\WEB-INF\web.xml ====
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">
         
    <display-name>Smart Complaint Prioritization and Management System</display-name>

    <welcome-file-list>
        <welcome-file>index.jsp</welcome-file>
    </welcome-file-list>

</web-app>


==== src\main\webapp\index.jsp ====
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Redirect the root path of the application to the login page controller
    response.sendRedirect(request.getContextPath() + "/login");
%>


==== DBInit.java ====
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBInit {
    public static void main(String[] args) {
        // We connect to the MySQL server without specifying a database first
        // because the database might not exist yet.
        String url = "jdbc:h2:file:./complaint_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        String user = "sa";
        String password = ""; 

        try {
            Class.forName("org.h2.Driver");
            Connection conn = null;
            String[] passwordsToTry = {"password", "", "root", "admin"};
            for (String p : passwordsToTry) {
                try {
                    System.out.println("Trying to connect to MySQL with password: '" + p + "'...");
                    conn = DriverManager.getConnection(url, user, p);
                    password = p;
                    System.out.println("Successfully connected!");
                    break;
                } catch (Exception e) {
                    // ignore and try next
                }
            }

            if (conn == null) {
                System.out.println("ERROR: Could not connect to local MySQL on port 3306 with common passwords.");
                return;
            }

            // Read schema.sql
            StringBuilder sql = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader("schema.sql"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sql.append(line).append("\n");
                }
            }

            // Split into statements and execute
            String[] statements = sql.toString().split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String s : statements) {
                    if (s.trim().length() > 0) {
                        stmt.execute(s.trim());
                    }
                }
                System.out.println("Database initialization completed successfully!");
            }

            // Update DBConnection.java with the discovered password
            if (!"password".equals(password)) {
                System.out.println("Password used was '" + password + "', please update DBConnection.java if necessary.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


==== pom.xml ====
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.complaint</groupId>
    <artifactId>Smart-Complaint-Prioritization-System</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>
    
    <name>Smart Complaint Prioritization and Management System</name>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencies>
        <!-- Jakarta Servlet API (Tomcat 10+) -->
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Jakarta JSP API -->
        <dependency>
            <groupId>jakarta.servlet.jsp</groupId>
            <artifactId>jakarta.servlet.jsp-api</artifactId>
            <version>3.1.1</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Jakarta Standard Tag Library (JSTL) -->
        <dependency>
            <groupId>jakarta.servlet.jsp.jstl</groupId>
            <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
            <version>3.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.web</groupId>
            <artifactId>jakarta.servlet.jsp.jstl</artifactId>
            <version>3.0.1</version>
        </dependency>
        
        <!-- H2 Database (Replaces MySQL for Zero-Config Local Setup) -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.2.224</version>
        </dependency>
    </dependencies>
    
    <build>
        <finalName>complaint-system</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
                <configuration>
                    <failOnMissingWebXml>false</failOnMissingWebXml>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.eclipse.jetty.ee10</groupId>
                <artifactId>jetty-ee10-maven-plugin</artifactId>
                <version>12.0.12</version>
                <configuration>
                    <webApp>
                        <contextPath>/complaint-system</contextPath>
                    </webApp>
                    <httpConnector>
                        <port>8080</port>
                    </httpConnector>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.eclipse.jetty.ee10</groupId>
                        <artifactId>jetty-ee10-apache-jsp</artifactId>
                        <version>12.0.12</version>
                    </dependency>
                    <dependency>
                        <groupId>org.eclipse.jetty.ee10</groupId>
                        <artifactId>jetty-ee10-glassfish-jstl</artifactId>
                        <version>12.0.12</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>


==== schema.sql ====
-- Database Initialization Script for MySQL

-- Database Initialization Script

-- 1. Create Users Table (Citizens)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Hashed password
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    address VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create Admins Table (Staff/System Admin)
CREATE TABLE IF NOT EXISTS admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Hashed password
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create Complaints Table
CREATE TABLE IF NOT EXISTS complaints (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'IN_PROGRESS', 'RESOLVED'
    priority VARCHAR(20) NOT NULL DEFAULT 'LOW',    -- 'LOW', 'MEDIUM', 'HIGH'
    citizen_id INT NOT NULL,
    admin_id INT DEFAULT NULL,                      -- Staff assigned to resolve/update
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaints_users FOREIGN KEY (citizen_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_complaints_admins FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Seed Initial Data
-- Seed Admins (Password: 'password' hashed with SHA-256)
INSERT IGNORE INTO admins (username, password, email, full_name, department, employee_id) 
VALUES ('admin', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'admin@complaints.org', 'System Administrator', 'Municipal Administration', 'EMP1001')
ON DUPLICATE KEY UPDATE id=id;

INSERT IGNORE INTO admins (username, password, email, full_name, department, employee_id) 
VALUES ('elect_admin', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'electricity_head@complaints.org', 'Sarah Connor', 'Electricity Department', 'EMP1024')
ON DUPLICATE KEY UPDATE id=id;

-- Seed Users (Citizens) (Password: 'password' hashed with SHA-256)
INSERT IGNORE INTO users (username, password, email, full_name, phone, address) 
VALUES ('citizen1', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'citizen1@testmail.com', 'Faheem Shaik', '9876543210', '123 Park Lane, Sector 4')
ON DUPLICATE KEY UPDATE id=id;

INSERT IGNORE INTO users (username, password, email, full_name, phone, address) 
VALUES ('citizen2', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'citizen2@testmail.com', 'Alex Mercer', '8765432109', '456 Garden Square, Sector 9')
ON DUPLICATE KEY UPDATE id=id;

-- Seed Complaints
INSERT IGNORE INTO complaints (title, description, category, status, priority, citizen_id, admin_id)
VALUES 
('Power Cable Sparking Near Main Park', 'The electricity wire snapped and is continuously sparking on the pavement. This is a severe threat to kids playing nearby.', 'Electricity', 'PENDING', 'HIGH', 1, NULL),
('Broken Pothole near Central Avenue', 'A huge pothole has formed in the middle of the road. Vehicles are swerving to avoid it.', 'Roads', 'IN_PROGRESS', 'MEDIUM', 2, 2),
('Slight garbage pile up outside colony gate', 'There is some dry garbage accumulated outside. Needs clean up by municipal team.', 'Sanitation', 'PENDING', 'LOW', 1, NULL);


