import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Scanner;
 
public class Number {
 
    // Single shared Scanner for all console input across the program
    private static final Scanner scanner = new Scanner(System.in);
 
    // In-memory store of all exams (no persistence to disk/db)
    private static final ArrayList<Exam> exams = new ArrayList<>();
 
    // FIX: original pattern was "yyyyMM-dd" which does NOT match the
    // "YYYY-MM-DD" format the prompts ask for, and would throw
    // DateTimeParseException on any correctly typed input.
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
 
    public static void main(String[] args) {
        boolean running = true;
 
        // Main menu loop — keeps prompting until user chooses Exit (11)
        while (running) {
            printHeader("SMART EXAM PLANNER - MAIN MENU");
 
            System.out.println("1. Add Examination");
            System.out.println("2. View All Examinations");
            System.out.println("3. View Upcoming Examination");
            System.out.println("4. Study Plan Generator");
            System.out.println("5. Preparation Tracker");
            System.out.println("6. Update Examination");
            System.out.println("7. Delete Examination");
            System.out.println("8. Search Examination");
            System.out.println("9. Sort by Exam Date");
            System.out.println("10. Exam Statistics");
            System.out.println("11. Exit");
 
            int choice = readInt("\nEnter your choice: ");
 
            // Switch expression (Java 14+) routes to the right handler
            switch (choice) {
                case 1 -> addExam();
                case 2 -> viewAllExams();
                case 3 -> viewUpcomingExam();
                case 4 -> studyPlan();
                case 5 -> preparationTracker();
                case 6 -> updateExam();
                case 7 -> deleteExam();
                case 8 -> searchExam();
                case 9 -> sortExams();
                case 10 -> statistics();
                case 11 -> {
                    running = false;
                    System.out.println("\nThank you for using Smart Exam Planner!");
                }
                default -> System.out.println("\nInvalid choice. Please try again.");
            }
        }
 
        scanner.close();
    }
 
    // Collects all fields for a new exam and adds it to the list
    private static void addExam() {
        printHeader("ADD EXAMINATION");
 
        String subject = readText("Enter subject name: ");
        LocalDate date = readDate("Enter exam date (YYYY-MM-DD): ");
        int totalChapters = readPositiveInt("Enter total number of chapters: ");
        double studyHours = readNonNegativeDouble("Enter total study hours required: ");
        String priority = readPriority();
        // Ask for completed chapters last so it can be validated against totalChapters
        int completedChapters = readChapterProgress(totalChapters);
 
        exams.add(new Exam(subject, date, totalChapters, studyHours, priority, completedChapters));
 
        System.out.println("\nExamination added successfully!");
    }
 
    // Prints every stored exam with its full details
    private static void viewAllExams() {
        printHeader("ALL EXAMINATIONS");
 
        if (exams.isEmpty()) {
            System.out.println("No examination records found.");
            return;
        }
 
        for (int i = 0; i < exams.size(); i++) {
            System.out.println("\nExam " + (i + 1));
            displayExam(exams.get(i));
        }
    }
 
    // Finds the nearest not-yet-completed exam that hasn't passed today
    private static void viewUpcomingExam() {
        printHeader("UPCOMING EXAMINATION");
 
        Exam upcoming = null;
        LocalDate today = LocalDate.now();
 
        for (Exam exam : exams) {
            // Skip completed exams and exams whose date is already in the past
            if (!exam.completed && !exam.date.isBefore(today)) {
                if (upcoming == null || exam.date.isBefore(upcoming.date)) {
                    upcoming = exam;
                }
            }
        }
 
        if (upcoming == null) {
            System.out.println("No upcoming pending examination found.");
        } else {
            displayExam(upcoming);
        }
    }
 
    // Computes a per-day chapter/study-hour target based on days remaining
    private static void studyPlan() {
        printHeader("STUDY PLAN GENERATOR");
 
        Exam exam = findExamBySubject();
        if (exam == null) return;
 
        long days = ChronoUnit.DAYS.between(LocalDate.now(), exam.date);
        int remainingChapters = exam.totalChapters - exam.completedChapters;
 
        // Guard against division by zero / negative days if exam is today or overdue
        if (days <= 0) {
            days = 1;
        }
 
        double dailyChapters = (double) remainingChapters / days;
        double dailyHours = exam.studyHours / days;
 
        System.out.println("\nSubject              : " + exam.subject);
        System.out.println("Exam Date            : " + exam.date);
        System.out.println("Days Remaining       : " + days + " days");
        System.out.println("Remaining Chapters   : " + remainingChapters);
        System.out.printf("Daily Chapter Target : %.2f chapters/day%n", dailyChapters);
        System.out.println("Study Hours Required : " + exam.studyHours);
        System.out.printf("Daily Study Target   : %.2f hours/day%n", dailyHours);
        System.out.println("Priority             : " + exam.priority);
    }
 
    // Shows completion percentage and status label for one exam
    private static void preparationTracker() {
        printHeader("PREPARATION TRACKER");
 
        Exam exam = findExamBySubject();
        if (exam == null) return;
 
        double progress = (double) exam.completedChapters / exam.totalChapters * 100;
 
        System.out.println("\nSubject            : " + exam.subject);
        System.out.println("Completed Chapters : " + exam.completedChapters);
        System.out.println("Total Chapters     : " + exam.totalChapters);
        System.out.printf("Progress           : %.2f%%%n", progress);
 
        if (exam.completedChapters == exam.totalChapters) {
            System.out.println("Status             : Completed");
        } else if (exam.completedChapters == 0) {
            System.out.println("Status             : Not Started");
        } else {
            System.out.println("Status             : In Progress");
        }
    }
 
    // Overwrites an existing exam's details (subject itself is not editable)
    private static void updateExam() {
        printHeader("UPDATE EXAMINATION");
 
        Exam exam = findExamBySubject();
        if (exam == null) return;
 
        System.out.println("\nEnter new details:");
 
        exam.date = readDate("Enter new exam date (YYYY-MM-DD): ");
        exam.totalChapters = readPositiveInt("Enter new total chapters: ");
        exam.studyHours = readNonNegativeDouble("Enter new study hours: ");
        exam.priority = readPriority();
        // Re-validate completed chapters against the (possibly new) total
        exam.completedChapters = readChapterProgress(exam.totalChapters);
 
        System.out.println("\nExamination updated successfully!");
    }
 
    // Removes an exam from the list after locating it by subject
    private static void deleteExam() {
        printHeader("DELETE EXAMINATION");
 
        Exam exam = findExamBySubject();
        if (exam == null) return;
 
        exams.remove(exam);
        System.out.println("\nExamination deleted successfully!");
    }
 
    // Case-insensitive partial match search on subject name
    private static void searchExam() {
        printHeader("SEARCH EXAMINATION");
 
        String search = readText("Enter subject name to search: ").toLowerCase();
        boolean found = false;
 
        for (Exam exam : exams) {
            if (exam.subject.toLowerCase().contains(search)) {
                displayExam(exam);
                found = true;
            }
        }
 
        if (!found) {
            System.out.println("\nNo matching examination found.");
        }
    }
 
    // Sorts the exam list in place by ascending exam date
    private static void sortExams() {
        printHeader("SORT EXAMINATIONS");
 
        exams.sort(Comparator.comparing(exam -> exam.date));
        System.out.println("Examinations sorted successfully by date.");
 
        viewAllExams();
    }
 
    // Aggregates counts and average progress across all exams
    private static void statistics() {
        printHeader("EXAM STATISTICS");
 
        if (exams.isEmpty()) {
            System.out.println("No examination records found.");
            return;
        }
 
        int completed = 0;
        double totalProgress = 0;
 
        for (Exam exam : exams) {
            if (exam.completed) {
                completed++;
            }
            totalProgress += (double) exam.completedChapters / exam.totalChapters * 100;
        }
 
        int pending = exams.size() - completed;
        double averageProgress = totalProgress / exams.size();
 
        System.out.println("Total Examinations     : " + exams.size());
        System.out.println("Completed Examinations : " + completed);
        System.out.println("Pending Examinations   : " + pending);
        System.out.printf("Average Preparation    : %.2f%%%n", averageProgress);
    }
 
    // Shared lookup helper: prompts for a subject, returns matching Exam or null
    private static Exam findExamBySubject() {
        String subject = readText("Enter subject name: ");
 
        for (Exam exam : exams) {
            if (exam.subject.equalsIgnoreCase(subject)) {
                return exam;
            }
        }
 
        System.out.println("Examination not found.");
        return null;
    }
 
    // Prints a full detail block for one exam; also flips completed flag
    // to true here if chapters are fully done (side effect on display!)
    private static void displayExam(Exam exam) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), exam.date);
        double progress = (double) exam.completedChapters / exam.totalChapters * 100;
 
        System.out.println("--------------------------------");
        System.out.println("Subject          : " + exam.subject);
        System.out.println("Exam Date        : " + exam.date);
        System.out.println("Total Chapters   : " + exam.totalChapters);
        System.out.println("Completed        : " + exam.completedChapters);
        System.out.println("Study Hours      : " + exam.studyHours);
        System.out.println("Priority         : " + exam.priority);
        System.out.printf("Progress         : %.2f%%%n", progress);
        System.out.println("Days Remaining   : " + days);
 
        // NOTE: mutating state inside a "display" method is a code smell —
        // consider moving this into the Exam constructor/setters instead.
        if (exam.completedChapters == exam.totalChapters) {
            exam.completed = true;
        }
 
        System.out.println("Status           : " + (exam.completed ? "Completed" : "Pending"));
        System.out.println("--------------------------------");
    }
 
    // Keeps re-prompting until a non-empty string is entered
    private static String readText(String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
 
            if (!input.isEmpty()) {
                return input;
            }
 
            System.out.println("Input cannot be empty.");
        }
    }
 
    // Keeps re-prompting until a parseable integer is entered
    private static int readInt(String message) {
        while (true) {
            try {
                System.out.print(message);
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }
 
    // Wraps readInt() with a "> 0" validation rule
    private static int readPositiveInt(String message) {
        while (true) {
            int value = readInt(message);
 
            if (value > 0) {
                return value;
            }
 
            System.out.println("Value must be greater than zero.");
        }
    }
 
    // Keeps re-prompting until a parseable, non-negative double is entered
    private static double readNonNegativeDouble(String message) {
        while (true) {
            try {
                System.out.print(message);
                double value = Double.parseDouble(scanner.nextLine().trim());
 
                if (value >= 0) {
                    return value;
                }
 
                System.out.println("Value cannot be negative.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
 
    // Validates completed chapters fall within [0, totalChapters]
    private static int readChapterProgress(int totalChapters) {
        while (true) {
            int completed = readInt("Enter completed chapters: ");
 
            if (completed >= 0 && completed <= totalChapters) {
                return completed;
            }
 
            System.out.println("Completed chapters must be between 0 and " + totalChapters + ".");
        }
    }
 
    // Restricts priority input to exactly High / Medium / Low (case-insensitive)
    private static String readPriority() {
        while (true) {
            String priority = readText("Enter priority (High/Medium/Low): ");
 
            if (priority.equalsIgnoreCase("High")
                    || priority.equalsIgnoreCase("Medium")
                    || priority.equalsIgnoreCase("Low")) {
                return priority;
            }
 
            System.out.println("Enter High, Medium, or Low.");
        }
    }
 
    // Parses a date string using FORMATTER, re-prompting on invalid input
    private static LocalDate readDate(String message) {
        while (true) {
            try {
                System.out.print(message);
                return LocalDate.parse(scanner.nextLine().trim(), FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use YYYY-MM-DD format.");
            }
        }
    }
 
    // Simple decorative section header printed before each menu/screen
    private static void printHeader(String title) {
        System.out.println("\n======================================");
        System.out.println("       " + title);
        System.out.println("======================================");
    }
 
    // Data model for a single exam record (no getters/setters — package-private fields)
    private static class Exam {
        String subject;
        LocalDate date;
        int totalChapters;
        double studyHours;
        String priority;
        int completedChapters;
        boolean completed;
 
        Exam(String subject, LocalDate date, int totalChapters,
             double studyHours, String priority, int completedChapters) {
            this.subject = subject;
            this.date = date;
            this.totalChapters = totalChapters;
            this.studyHours = studyHours;
            this.priority = priority;
            this.completedChapters = completedChapters;
            // Determines initial completed status at creation time
            this.completed = completedChapters == totalChapters;
        }
    }
}
