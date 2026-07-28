================================================================
AI CHATBOT PROJECT — FULL SOURCE CODE
Java Backend (7 classes) + HTML/CSS Frontend
================================================================
 
 
================================================================
FILE: src/Main.java
================================================================
 
import java.util.Scanner;
 
/**
 * Entry point for the AI Chatbot console application.
 * Handles only the presentation loop — all reasoning is delegated to
 * ChatEngine, keeping this class thin and focused on I/O.
 */
public class Main {
 
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ChatEngine engine = new ChatEngine();
 
        printBanner();
 
        while (true) {
            System.out.print("You: ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String userInput = scanner.nextLine();
            String normalized = userInput.toLowerCase().trim();
 
            if (normalized.equals("bye") || normalized.equals("exit") || normalized.equals("quit")) {
                System.out.println("Chatbot: Goodbye! Have a great day!");
                break;
            }
 
            String response = engine.respond(userInput);
            System.out.println("Chatbot: " + response);
            System.out.println();
        }
 
        scanner.close();
    }
 
    private static void printBanner() {
        System.out.println("==================================================");
        System.out.println("             AI CHATBOT ASSISTANT (Java)         ");
        System.out.println("==================================================");
        System.out.println("Hi! I'm your AI Assistant. Ask me about Java, AI,");
        System.out.println("programming, general topics, or even quick maths.");
        System.out.println("Type 'bye' to exit.\n");
    }
}
 
 
================================================================
FILE: src/ChatEngine.java
================================================================
 
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
/**
 * The "brain" of the chatbot. Responsible for:
 *  - Cleaning and tokenizing raw user input
 *  - Scoring every known Intent against the input (keyword + fuzzy match)
 *  - Delegating to specialised handlers (math evaluation, name capture)
 *  - Falling back gracefully with a helpful message when nothing matches
 *
 * Kept separate from the console UI (Main.java) so the same engine could
 * later be reused by a different frontend (e.g. a web servlet) without
 * modification — a key requirement for clean, modular design.
 */
public class ChatEngine {
 
    private final IntentRepository repository;
    private final ConversationContext context;
    private final MathEvaluator mathEvaluator;
    private final Random random = new Random();
 
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:my name is|i am|i'm|call me)\\s+([a-zA-Z]+)");
 
    public ChatEngine() {
        this.repository = new IntentRepository();
        this.context = new ConversationContext();
        this.mathEvaluator = new MathEvaluator();
    }
 
    public ConversationContext getContext() {
        return context;
    }
 
    /**
     * Produces a chatbot response for the given raw user input.
     */
    public String respond(String rawInput) {
        String input = rawInput.toLowerCase().trim();
 
        if (input.isEmpty()) {
            return "Could you type something? I'm listening.";
        }
 
        // 1. Name capture — enables personalised, context-aware replies
        Matcher nameMatcher = NAME_PATTERN.matcher(input);
        if (nameMatcher.find() && !input.contains("i am fine") && !input.contains("i am good")) {
            String name = capitalize(nameMatcher.group(1));
            context.setUserName(name);
            context.recordMessage(rawInput, "name_capture");
            return "Nice to meet you, " + name + "! How can I help you today?";
        }
 
        // 2. Math expression detection
        if (MathEvaluator.looksLikeMath(input)) {
            try {
                double result = mathEvaluator.evaluate(input);
                context.recordMessage(rawInput, "math");
                return "The result is: " + formatNumber(result);
            } catch (Exception e) {
                context.recordMessage(rawInput, "math_error");
                return "That looks like a calculation, but I couldn't evaluate it. Please check the expression.";
            }
        }
 
        // 3. Ask user's own name back
        if (input.contains("what is my name") || input.contains("what's my name") || input.contains("my name?")) {
            context.recordMessage(rawInput, "recall_name");
            return context.hasUserName()
                    ? "Your name is " + context.getUserName() + "!"
                    : "You haven't told me your name yet — feel free to introduce yourself!";
        }
 
        // 4. Intent scoring across the knowledge base
        Intent bestIntent = null;
        int bestScore = 0;
 
        for (Intent intent : repository.getIntents()) {
            int score = scoreIntent(input, intent);
            if (score > bestScore) {
                bestScore = score;
                bestIntent = intent;
            }
        }
 
        context.recordMessage(rawInput, bestIntent != null ? bestIntent.getName() : "fallback");
 
        if (bestIntent != null && bestScore > 0) {
            return personalize(pickResponse(bestIntent));
        }
 
        return fallbackResponse();
    }
 
    /**
     * Scores how well an intent matches the input using exact keyword
     * containment first (strong signal), then fuzzy per-word matching
     * (typo tolerance), weighted by the intent's configured priority.
     */
    private int scoreIntent(String input, Intent intent) {
        int score = 0;
        String[] inputWords = input.replaceAll("[^a-z0-9\\s']", "").split("\\s+");
 
        for (String keyword : intent.getKeywords()) {
            if (keyword.contains(" ")) {
                // Multi-word keyword/phrase: substring containment is safe and intended
                if (input.contains(keyword)) {
                    score += 3 * intent.getPriority();
                }
                continue;
            }
 
            // Single-word keyword: match against whole words only, never as a
            // substring of an unrelated word (e.g. "yo" must not match inside "you").
            boolean exactWordMatch = false;
            for (String word : inputWords) {
                if (word.equals(keyword)) {
                    exactWordMatch = true;
                    break;
                }
            }
            if (exactWordMatch) {
                score += 3 * intent.getPriority();
                continue;
            }
            // Fuzzy fallback for typo tolerance, still whole-word only
            for (String word : inputWords) {
                if (word.length() >= 3 && FuzzyMatcher.isCloseMatch(word, keyword)) {
                    score += 1 * intent.getPriority();
                    break;
                }
            }
        }
        return score;
    }
 
    private String pickResponse(Intent intent) {
        List<String> responses = intent.getResponses();
        String chosen = responses.get(random.nextInt(responses.size()));
        if (chosen.contains("%DATETIME%")) {
            chosen = chosen.replace("%DATETIME%", new Date().toString());
        }
        return chosen;
    }
 
    private String personalize(String response) {
        // Occasionally weave the user's name in for a more natural feel
        if (context.hasUserName() && random.nextInt(4) == 0 && !response.contains(context.getUserName())) {
            return response.replaceFirst("!$", ", " + context.getUserName() + "!");
        }
        return response;
    }
 
    private String fallbackResponse() {
        String[] fallbacks = {
                "I'm still learning and didn't quite catch that. Could you rephrase it?",
                "Hmm, I'm not sure about that one yet. Try asking about Java, AI, or general topics!",
                "I don't have an answer for that right now, but feel free to ask something else."
        };
        return fallbacks[random.nextInt(fallbacks.length)];
    }
 
    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.4f", value);
    }
 
    private String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
}
 
================================================================
FILE: src/Intent.java
================================================================
 
import java.util.List;
 
/**
 * Represents a single conversational intent that the chatbot can recognise.
 * Each intent carries a set of trigger keywords, a family of possible
 * responses (so replies don't feel robotic/repetitive), and a priority
 * weight used when multiple intents partially match the same input.
 */
public class Intent {
 
    private final String name;
    private final List<String> keywords;
    private final List<String> responses;
    private final int priority;
 
    public Intent(String name, List<String> keywords, List<String> responses, int priority) {
        this.name = name;
        this.keywords = keywords;
        this.responses = responses;
        this.priority = priority;
    }
 
    public String getName() {
        return name;
    }
 
    public List<String> getKeywords() {
        return keywords;
    }
 
    public List<String> getResponses() {
        return responses;
    }
 
    public int getPriority() {
        return priority;
    }
}
 
================================================================
FILE: src/IntentRepository.java
================================================================
 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
 
/**
 * Central knowledge base of the chatbot. Each Intent bundles a topic's
 * trigger keywords with several response variations, so replies feel
 * natural rather than robotically repeating the same sentence every time.
 * Adding a new topic only requires adding one Intent here — the matching
 * engine (ChatEngine) needs no changes, which keeps the system modular
 * and easy to extend.
 */
public class IntentRepository {
 
    private final List<Intent> intents = new ArrayList<>();
 
    public IntentRepository() {
        buildIntents();
    }
 
    public List<Intent> getIntents() {
        return intents;
    }
 
    private void add(String name, List<String> keywords, List<String> responses, int priority) {
        intents.add(new Intent(name, keywords, responses, priority));
    }
 
    private void buildIntents() {
 
        add("greeting",
                Arrays.asList("hello", "hi", "hey", "greetings", "yo", "hiya"),
                Arrays.asList(
                        "Hello there! How can I help you today?",
                        "Hi! Great to see you. What's on your mind?",
                        "Hey! I'm ready whenever you are."
                ), 2);
 
        add("farewell",
                Arrays.asList("bye", "goodbye", "see you", "exit", "quit", "farewell"),
                Arrays.asList(
                        "Goodbye! It was nice chatting with you.",
                        "See you soon! Take care.",
                        "Bye! Come back anytime you have a question."
                ), 3);
 
        add("wellbeing",
                Arrays.asList("how are you", "how r u", "how are u", "how're you"),
                Arrays.asList(
                        "I'm running smoothly, thank you for asking! How are you?",
                        "Doing great and ready to help. How about yourself?"
                ), 2);
 
        add("identity",
                Arrays.asList("your name", "who are you", "what are you"),
                Arrays.asList(
                        "I am an AI-based conversational assistant built in Java.",
                        "I'm your Java-powered chatbot assistant — here to answer your questions."
                ), 2);
 
        add("creator",
                Arrays.asList("who made you", "who created you", "your creator", "your developer"),
                Arrays.asList(
                        "I was designed and developed as an academic project using Java, HTML and CSS.",
                        "I was built by a student developer as part of an AI chatbot mini-project."
                ), 2);
 
        add("age",
                Arrays.asList("your age", "how old are you"),
                Arrays.asList(
                        "I don't have an age like humans do — I exist purely as code!",
                        "Age doesn't quite apply to me, but I was recently built and I'm still learning."
                ), 1);
 
        add("capabilities",
                Arrays.asList("help", "what can you do", "your features", "commands"),
                Arrays.asList(
                        "I can chat about greetings, technology, programming, general knowledge, "
                                + "do quick calculations, tell jokes, and more. Just ask!",
                        "Try asking me about Java, AI, programming concepts, or even a math expression like 12*4."
                ), 1);
 
        add("thanks",
                Arrays.asList("thank you", "thanks", "appreciate it", "thank u"),
                Arrays.asList(
                        "You're most welcome!",
                        "Happy to help, anytime!",
                        "Glad I could assist!"
                ), 2);
 
        add("apology",
                Arrays.asList("sorry", "apologize", "my bad"),
                Arrays.asList(
                        "No worries at all!",
                        "That's completely fine, no need to apologize."
                ), 1);
 
        add("mood_good",
                Arrays.asList("i am fine", "i am good", "doing great", "i am okay", "im good"),
                Arrays.asList(
                        "That's wonderful to hear!",
                        "Glad you're doing well!"
                ), 1);
 
        add("mood_bad",
                Arrays.asList("i am sad", "not feeling well", "i am tired", "feeling down", "i am upset"),
                Arrays.asList(
                        "I'm sorry to hear that. I hope things get better soon.",
                        "That sounds tough — take a short break if you can."
                ), 2);
 
        add("time_date",
                Arrays.asList("time", "date", "today's date", "current time"),
                Arrays.asList(
                        "The current date and time is: " + "%DATETIME%"
                ), 2);
 
        add("weather",
                Arrays.asList("weather", "temperature", "raining", "climate"),
                Arrays.asList(
                        "I can't fetch live weather data, but a weather service or app can give you accurate updates.",
                        "I don't have live internet access for weather, but I'd recommend checking a weather app."
                ), 1);
 
        add("java",
                Arrays.asList("java"),
                Arrays.asList(
                        "Java is a robust, object-oriented, platform-independent programming language "
                                + "widely used for enterprise and Android applications.",
                        "Java follows 'write once, run anywhere' thanks to the JVM, and is a popular choice "
                                + "for backend and Android development."
                ), 1);
 
        add("python",
                Arrays.asList("python"),
                Arrays.asList(
                        "Python is a beginner-friendly, high-level language known for its readability "
                                + "and heavy use in AI, data science, and automation."
                ), 1);
 
        add("ai_ml",
                Arrays.asList("artificial intelligence", "machine learning", " ai ", "deep learning", "neural network"),
                Arrays.asList(
                        "Artificial Intelligence is the field of building systems that can perform tasks "
                                + "requiring human-like intelligence, such as reasoning and learning.",
                        "Machine Learning is a subset of AI where systems learn patterns from data "
                                + "instead of being explicitly programmed."
                ), 1);
 
        add("oop",
                Arrays.asList("oop", "object oriented", "class and object", "inheritance", "polymorphism", "encapsulation"),
                Arrays.asList(
                        "Object-Oriented Programming is a paradigm built around objects, using core "
                                + "principles like encapsulation, inheritance, polymorphism, and abstraction."
                ), 1);
 
        add("database",
                Arrays.asList("database", "sql", "dbms"),
                Arrays.asList(
                        "A database is an organized collection of data, typically managed using a "
                                + "Database Management System (DBMS) like MySQL or PostgreSQL."
                ), 1);
 
        add("algorithm",
                Arrays.asList("algorithm", "data structure"),
                Arrays.asList(
                        "An algorithm is a well-defined, step-by-step procedure for solving a problem "
                                + "or performing a computation."
                ), 1);
 
        add("coding",
                Arrays.asList("coding", "programming", "learn to code"),
                Arrays.asList(
                        "Coding is the process of writing instructions for a computer to execute. "
                                + "Practicing on platforms like LeetCode or HackerRank helps build skill.",
                        "Programming is a great skill to build — consistent daily practice is the fastest way to improve."
                ), 1);
 
        add("codealpha",
                Arrays.asList("codealpha", "internship"),
                Arrays.asList(
                        "CodeAlpha offers hands-on internship experience through real project tasks — "
                                + "a great way to apply what you've learned."
                ), 1);
 
        add("joke",
                Arrays.asList("joke", "make me laugh", "funny"),
                Arrays.asList(
                        "Why do programmers prefer dark mode? Because light attracts bugs!",
                        "Why do Java developers wear glasses? Because they don't see sharp (C#)!",
                        "I would tell you a UDP joke, but you might not get it."
                ), 1);
 
        add("motivation",
                Arrays.asList("motivate me", "motivation", "i feel like giving up", "inspire me"),
                Arrays.asList(
                        "Every expert was once a beginner — keep going, progress compounds over time.",
                        "Small consistent effort beats occasional bursts of motivation. You've got this!"
                ), 2);
 
        add("favorite_color",
                Arrays.asList("favorite color", "favourite color", "favourite colour"),
                Arrays.asList(
                        "If I had to pick, I'd say blue — it reminds me of clean, structured code."
                ), 1);
 
        add("compliment",
                Arrays.asList("you are smart", "you are helpful", "good job", "well done", "you are great"),
                Arrays.asList(
                        "Thank you so much, that means a lot!",
                        "I appreciate that! I'll keep trying my best."
                ), 1);
 
        add("greeting_morning",
                Arrays.asList("good morning"),
                Arrays.asList("Good morning! Hope you have a productive day ahead."), 3);
 
        add("greeting_evening",
                Arrays.asList("good evening"),
                Arrays.asList("Good evening! Hope your day went well."), 3);
 
        add("greeting_night",
                Arrays.asList("good night"),
                Arrays.asList("Good night! Rest well."), 3);
    }
}
 
================================================================
FILE: src/ConversationContext.java
================================================================
 
import java.util.ArrayList;
import java.util.List;
 
/**
 * Holds short-term memory for the current session so the chatbot can give
 * context-aware responses instead of treating every message in isolation
 * (e.g. remembering the user's name once they introduce themselves).
 */
public class ConversationContext {
 
    private String userName = null;
    private String lastIntent = null;
    private final List<String> history = new ArrayList<>();
    private int messageCount = 0;
 
    public void recordMessage(String userInput, String matchedIntent) {
        history.add(userInput);
        lastIntent = matchedIntent;
        messageCount++;
    }
 
    public void setUserName(String name) {
        this.userName = name;
    }
 
    public String getUserName() {
        return userName;
    }
 
    public boolean hasUserName() {
        return userName != null && !userName.isEmpty();
    }
 
    public String getLastIntent() {
        return lastIntent;
    }
 
    public int getMessageCount() {
        return messageCount;
    }
}
 
================================================================
FILE: src/MathEvaluator.java
================================================================
 
import java.util.regex.Pattern;
 
/**
 * Lightweight, dependency-free arithmetic expression evaluator.
 * Supports +, -, *, /, %, ^ and parentheses using the classic
 * recursive-descent parsing technique (no external library, no
 * insecure eval-style execution).
 */
public class MathEvaluator {
 
    private static final Pattern MATH_PATTERN =
            Pattern.compile("^[\\d\\s+\\-*/^%().]+$");
 
    private String expression;
    private int position;
 
    /** Returns true if the input looks like a pure arithmetic expression. */
    public static boolean looksLikeMath(String input) {
        String cleaned = input.replace(" ", "");
        if (cleaned.isEmpty()) return false;
        boolean hasDigit = cleaned.chars().anyMatch(Character::isDigit);
        boolean hasOperator = cleaned.chars()
                .anyMatch(c -> "+-*/^%".indexOf(c) >= 0);
        return hasDigit && hasOperator && MATH_PATTERN.matcher(input).matches();
    }
 
    /** Evaluates a mathematical expression and returns the numeric result. */
    public double evaluate(String expr) {
        this.expression = expr.replace(" ", "");
        this.position = 0;
        double result = parseExpression();
        return result;
    }
 
    private double parseExpression() {
        double value = parseTerm();
        while (position < expression.length()) {
            char op = expression.charAt(position);
            if (op == '+' || op == '-') {
                position++;
                double next = parseTerm();
                value = (op == '+') ? value + next : value - next;
            } else {
                break;
            }
        }
        return value;
    }
 
    private double parseTerm() {
        double value = parseFactor();
        while (position < expression.length()) {
            char op = expression.charAt(position);
            if (op == '*' || op == '/' || op == '%') {
                position++;
                double next = parseFactor();
                if (op == '*') value *= next;
                else if (op == '/') value /= next;
                else value %= next;
            } else {
                break;
            }
        }
        return value;
    }
 
    private double parseFactor() {
        double value = parseBase();
        while (position < expression.length() && expression.charAt(position) == '^') {
            position++;
            double exponent = parseBase();
            value = Math.pow(value, exponent);
        }
        return value;
    }
 
    private double parseBase() {
        if (position < expression.length() && expression.charAt(position) == '-') {
            position++;
            return -parseBase();
        }
        if (position < expression.length() && expression.charAt(position) == '(') {
            position++;
            double value = parseExpression();
            if (position < expression.length() && expression.charAt(position) == ')') {
                position++;
            }
            return value;
        }
        int start = position;
        while (position < expression.length() &&
                (Character.isDigit(expression.charAt(position)) || expression.charAt(position) == '.')) {
            position++;
        }
        if (start == position) {
            throw new IllegalArgumentException("Invalid expression");
        }
        return Double.parseDouble(expression.substring(start, position));
    }
}
 
================================================================
FILE: src/FuzzyMatcher.java
================================================================
 
/**
 * Provides approximate ("fuzzy") string matching so the chatbot can still
 * understand a user who mistypes a keyword, e.g. "wheather" instead of
 * "weather". Implemented with the classic Levenshtein edit-distance
 * dynamic-programming algorithm.
 */
public class FuzzyMatcher {
 
    /**
     * Computes the Levenshtein distance (minimum number of single-character
     * edits: insertions, deletions, substitutions) between two strings.
     */
    public static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
 
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost
                    );
                }
            }
        }
        return dp[a.length()][b.length()];
    }
 
    /**
     * Returns true if two words are close enough to be considered a match.
     * The allowed edit distance scales with word length so short words
     * still require an exact/near-exact match.
     */
    public static boolean isCloseMatch(String word, String keyword) {
        if (word.equals(keyword)) {
            return true;
        }
        int maxAllowedDistance = keyword.length() <= 4 ? 1 : 2;
        return levenshteinDistance(word, keyword) <= maxAllowedDistance;
    }
}
 
================================================================
FILE: frontend/index.html
================================================================
 
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>AI Chatbot Assistant</title>
<style>
  :root {
    --bg: #0f1720;
    --panel: #16212c;
    --panel-alt: #1c2b38;
    --accent: #3ecf9e;
    --accent-soft: rgba(62, 207, 158, 0.15);
    --text-primary: #e7edf3;
    --text-muted: #8ea0af;
    --user-bubble: #3ecf9e;
    --bot-bubble: #22323f;
    --border: #263544;
  }
 
  * { box-sizing: border-box; margin: 0; padding: 0; }
 
  body {
    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
    background: var(--bg);
    color: var(--text-primary);
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
 
  .app {
    width: 100%;
    max-width: 480px;
    height: 720px;
    background: var(--panel);
    border-radius: 18px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    box-shadow: 0 20px 60px rgba(0,0,0,0.45);
    border: 1px solid var(--border);
  }
 
  .header {
    background: var(--panel-alt);
    padding: 18px 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-bottom: 1px solid var(--border);
  }
 
  .avatar {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--accent), #2a9d7f);
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 16px;
    color: #04120c;
  }
 
  .header-text h1 {
    font-size: 15px;
    font-weight: 600;
  }
 
  .header-text .status {
    font-size: 12px;
    color: var(--accent);
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 2px;
  }
 
  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--accent);
    box-shadow: 0 0 6px var(--accent);
  }
 
  .chat-area {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
 
  .msg-row {
    display: flex;
    flex-direction: column;
    max-width: 78%;
  }
 
  .msg-row.bot { align-self: flex-start; align-items: flex-start; }
  .msg-row.user { align-self: flex-end; align-items: flex-end; }
 
  .bubble {
    padding: 11px 15px;
    border-radius: 16px;
    font-size: 14px;
    line-height: 1.5;
  }
 
  .msg-row.bot .bubble {
    background: var(--bot-bubble);
    color: var(--text-primary);
    border-bottom-left-radius: 4px;
  }
 
  .msg-row.user .bubble {
    background: var(--user-bubble);
    color: #04120c;
    font-weight: 500;
    border-bottom-right-radius: 4px;
  }
 
  .msg-time {
    font-size: 10.5px;
    color: var(--text-muted);
    margin-top: 4px;
    padding: 0 4px;
  }
 
  .suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 0 20px 14px 20px;
  }
 
  .chip {
    background: var(--accent-soft);
    color: var(--accent);
    border: 1px solid rgba(62,207,158,0.35);
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
  }
 
  .input-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    background: var(--panel-alt);
    border-top: 1px solid var(--border);
  }
 
  .input-bar input {
    flex: 1;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 24px;
    padding: 11px 16px;
    color: var(--text-primary);
    font-size: 14px;
    outline: none;
  }
 
  .input-bar input::placeholder { color: var(--text-muted); }
 
  .send-btn {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    background: var(--accent);
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
  }
 
  .send-btn svg { width: 18px; height: 18px; }
 
  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 12px 15px;
    background: var(--bot-bubble);
    border-radius: 16px;
    border-bottom-left-radius: 4px;
    width: fit-content;
  }
 
  .typing-indicator span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--text-muted);
    opacity: 0.6;
  }
</style>
</head>
<body>
 
<div class="app">
  <div class="header">
    <div class="avatar">AI</div>
    <div class="header-text">
      <h1>AI Chatbot Assistant</h1>
      <div class="status"><span class="status-dot"></span>Online</div>
    </div>
  </div>
 
  <div class="chat-area">
    <div class="msg-row bot">
      <div class="bubble">Hi! I'm your AI Assistant. Ask me about Java, AI, programming, general topics, or even quick maths.</div>
      <div class="msg-time">09:41 AM</div>
    </div>
 
    <div class="msg-row user">
      <div class="bubble">my name is Shaik</div>
      <div class="msg-time">09:41 AM</div>
    </div>
 
    <div class="msg-row bot">
      <div class="bubble">Nice to meet you, Shaik! How can I help you today?</div>
      <div class="msg-time">09:41 AM</div>
    </div>
 
    <div class="msg-row user">
      <div class="bubble">what is java</div>
      <div class="msg-time">09:42 AM</div>
    </div>
 
    <div class="msg-row bot">
      <div class="bubble">Java follows 'write once, run anywhere' thanks to the JVM, and is a popular choice for backend and Android development.</div>
      <div class="msg-time">09:42 AM</div>
    </div>
 
    <div class="msg-row user">
      <div class="bubble">12*4+6</div>
      <div class="msg-time">09:42 AM</div>
    </div>
 
    <div class="msg-row bot">
      <div class="bubble">The result is: 54</div>
      <div class="msg-time">09:42 AM</div>
    </div>
 
    <div class="msg-row bot">
      <div class="typing-indicator">
        <span></span><span></span><span></span>
      </div>
    </div>
  </div>
 
  <div class="suggestions">
    <div class="chip">Tell me a joke</div>
    <div class="chip">What is AI?</div>
    <div class="chip">Motivate me</div>
  </div>
 
  <div class="input-bar">
    <input type="text" placeholder="Type your message..." />
    <button class="send-btn">
      <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 11L21 3L13 21L11 13L3 11Z" fill="#04120c"/>
      </svg>
    </button>
  </div>
</div>
 
</body>
</html>
