import java.util.Scanner;

/**
 * Artificial Intelligence Chatbot
 * This is a rule-based chatbot that responds to user questions
 * It uses Natural Language Processing (NLP) techniques like keyword matching
 */

public class AIChatbot {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("========================================");
        System.out.println("        WELCOME TO AI CHATBOT           ");
        System.out.println("========================================");
        System.out.println("Hi! I am your AI Assistant.");
        System.out.println("You can ask me anything!");
        System.out.println("Type 'bye' to exit.\n");

        // Keep chatting until user says bye
        while (true) {
            System.out.print("You: ");
            String userInput = scanner.nextLine().toLowerCase().trim();

            // Exit Condition
            if (userInput.equals("bye") || userInput.equals("exit") || userInput.equals("quit")) {
                System.out.println("Chatbot: Goodbye! Have a great day!");
                break;
            }

            // Get response from chatbot
            String response = getResponse(userInput);
            System.out.println("Chatbot: " + response);
            System.out.println();
        }

        scanner.close();
    }

    // Method to match user input with keywords and return response
    static String getResponse(String input) {

        // Greetings
        if (input.contains("hello") || input.contains("hi") || input.contains("hey")) {
            return "Hello! How are you doing today?";
        }

        // How are you?
        else if (input.contains("how are you") || input.contains("how r u")) {
            return "I am doing great, thank you for asking! How about you?";
        }

        // Name Questions
        else if (input.contains("your name") || input.contains("who are you")) {
            return "I am an AI Chatbot created by Shaik Faheemunnisa for CodeAlpha internship!";
        }

        // Age Questions
        else if (input.contains("your age") || input.contains("how old")) {
            return "I am a program, so I do not have an age. But I was created in 2026!";
        }

        // Weather
        else if (input.contains("weather")) {
            return "I cannot check live weather, but I suggest you check weather.com for updates!";
        }

        // Time
        else if (input.contains("time") || input.contains("date")) {
            return "The current date and time is: " + new java.util.Date();
        }

        // Java Questions
        else if (input.contains("java")) {
            return "Java is a popular programming language. It is object-oriented and platform independent!";
        }

        // Coding Questions
        else if (input.contains("coding") || input.contains("programming")) {
            return "Coding is awesome! I recommend practicing on platforms like LeetCode and HackerRank.";
        }

        // CodeAlpha
        else if (input.contains("codealpha") || input.contains("internship")) {
            return "CodeAlpha is a great platform for internships! Keep working hard!";
        }

        // Help
        else if (input.contains("help")) {
            return "I can answer questions about: greetings, Java, coding, weather, time, and more!";
        }

        // Thank You
        else if (input.contains("thank") || input.contains("thanks")) {
            return "You are welcome! Happy to help!";
        }

        // Good Morning / Evening / Night
        else if (input.contains("good morning")) {
            return "Good morning! Have a wonderful and productive day!";
        }
        else if (input.contains("good evening")) {
            return "Good evening! Hope your day went well!";
        }
        else if (input.contains("good night")) {
            return "Good night! Sweet dreams!";
        }

        // Jokes
        else if (input.contains("joke")) {
            return "Why do programmers prefer dark mode? Because light attracts bugs! Ha ha!";
        }

        // Favourite Colour
        else if (input.contains("favourite color") || input.contains("favorite color")) {
            return "My favourite color is Blue! It is the color of technology!";
        }

        // Love / Feelings
        else if (input.contains("love") || input.contains("feeling")) {
            return "That is a beautiful emotion! As a chatbot, I feel happy when I help people!";
        }

        // Default response when no keyword matches
        else {
            return "I am still learning! I did not understand that. Can you ask something else?";
        }
    }
}
