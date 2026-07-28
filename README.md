# Smart Complaint Prioritization and Management System

A Java MVC web application designed to help citizens report civic issues and allow administrators to track and resolve them efficiently. The system uses a smart backend algorithm to automatically estimate the priority (HIGH, MEDIUM, LOW) of a complaint based on keyword descriptions and categories.

## Tech Stack
*   **Backend**: Java 17, Jakarta Servlets 6.0, JSPs 3.1
*   **Frontend**: Pure HTML & CSS (No JavaScript)
*   **Database**: H2 Embedded Database (Zero Configuration)
*   **Build Tool & Server**: Maven & Eclipse Jetty

## Requirements
*   Java Development Kit (JDK) 17+
*   Maven installed on your system (or use a Maven wrapper)

## Setup Instructions

### 1. Database Configuration
**Zero setup required!** This project uses an embedded H2 database. When the server starts for the first time, it automatically creates a local database file (`complaint_db.mv.db`) right inside the project folder and initializes all the necessary tables and seed data automatically. 

### 2. Running the Application
To run the web application, open a terminal in the project directory and run the following Maven command:

```bash
mvn clean compile jetty:run
```
*(Note: The first time you run this, Maven will download the necessary server libraries).*

### 3. Accessing the System
Once the server prints `[INFO] Started ServerConnector`, open your web browser and navigate to:
**http://localhost:8080/complaint-system/**

*   **Admin Login**: `admin` / `password`
*   **Citizen Login**: `citizen1` / `password`
