package com.example.wordstat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * wordstat: a word-frequency CLI tool.
 *
 * <p>This class is INTENTIONALLY sub-optimal. It exists as a teaching example: an AI coding
 * agent (Claude Code, Codex, Copilot, Gemini) is meant to read this, find the algorithmic and
 * style issues, and produce an improved version with benchmarks proving the improvement.
 *
 * <p>Known issues (do not fix here; this is the "before" state):
 *
 * <ul>
 *   <li>{@link #topWords} is O(n^2) via repeated linear scans for the max.
 *   <li>{@link #tokenize} rebuilds a string one character at a time.
 *   <li>{@link #countWords} uses a list with linear search instead of a {@code HashMap}, making
 *       it O(n^2) overall.
 * </ul>
 */
public final class WordAnalyzer {

  private WordAnalyzer() {}

  /**
   * A single word and its occurrence count.
   *
   * @param word the lowercase word
   * @param count the number of occurrences
   */
  public record WordCount(String word, int count) {}

  /**
   * Splits text into lowercase alphabetic words.
   *
   * @param text raw input text
   * @return a list of lowercase word tokens
   */
  public static List<String> tokenize(String text) {
    List<String> words = new ArrayList<>();
    Matcher matcher = Pattern.compile("[a-z]+").matcher(text.toLowerCase());
    while (matcher.find()) {
      words.add(matcher.group());
    }
    return words;
  }

  /**
   * Counts word frequencies using linear-search lookup.
   *
   * @param words tokenized words
   * @return pairs of (word, count), order of first appearance
   */
  public static List<WordCount> countWords(List<String> words) {
    Map<String, Integer> map = new LinkedHashMap<>();
    for (String word : words) {
      map.put(word, map.getOrDefault(word, 0) + 1);
    }
    List<WordCount> counts = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      counts.add(new WordCount(entry.getKey(), entry.getValue()));
    }
    return counts;
  }

  /**
   * Returns the top-n counts by frequency, descending.
   *
   * @param counts list of word counts
   * @param n number of top entries to return
   * @return up to n entries sorted by count descending
   */
  public static List<WordCount> topWords(List<WordCount> counts, int n) {
    return counts.stream()
        .sorted((a, b) -> Integer.compare(b.count(), a.count()))
        .limit(n)
        .toList();
  }

  /**
   * Runs the full analysis pipeline on a text blob.
   *
   * @param text raw input text
   * @param topN number of top words to return
   * @return top-n word counts
   */
  public static List<WordCount> analyze(String text, int topN) {
    List<String> words = tokenize(text);
    List<WordCount> counts = countWords(words);
    return topWords(counts, topN);
  }

  /**
   * Parsed command-line arguments.
   *
   * @param filePath path to the input file
   * @param topN number of top words to display
   */
  public record ParsedArgs(String filePath, int topN) {}

  /**
   * Parses CLI arguments in the form: FILE [-n|--top N].
   *
   * @param args raw command-line arguments
   * @return parsed file path and top-n count
   * @throws IllegalArgumentException when arguments are missing or malformed
   */
  public static ParsedArgs parseArgs(String[] args) {
    String filePath = null;
    int topN = 10;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-n") || arg.equals("--top")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("missing value for " + arg);
        }
        try {
          topN = Integer.parseInt(args[i + 1]);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("invalid integer for " + arg + ": " + args[i + 1]);
        }
        i++;
      } else if (filePath == null) {
        filePath = arg;
      } else {
        throw new IllegalArgumentException("unexpected argument: " + arg);
      }
    }

    if (filePath == null) {
      throw new IllegalArgumentException("missing required argument: file");
    }

    return new ParsedArgs(filePath, topN);
  }

  /**
   * Runs the CLI logic against injectable output streams, for testability.
   *
   * @param args command-line arguments
   * @param out stream for normal output
   * @param err stream for error output
   * @return process exit code (0 on success, 1 on error)
   * @throws IOException if the input file cannot be read
   */
  public static int run(String[] args, java.io.PrintStream out, java.io.PrintStream err)
      throws IOException {
    ParsedArgs parsed;
    try {
      parsed = parseArgs(args);
    } catch (IllegalArgumentException e) {
      err.println("error: " + e.getMessage());
      return 1;
    }

    Path path = Path.of(parsed.filePath());
    if (!Files.isRegularFile(path)) {
      err.println("error: file not found: " + parsed.filePath());
      return 1;
    }

    String text = Files.readString(path);
    List<WordCount> results = analyze(text, parsed.topN());
    for (WordCount entry : results) {
      out.println(entry.word() + "\t" + entry.count());
    }

    return 0;
  }
}
