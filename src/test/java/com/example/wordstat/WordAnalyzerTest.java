package com.example.wordstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.wordstat.WordAnalyzer.ParsedArgs;
import com.example.wordstat.WordAnalyzer.WordCount;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link WordAnalyzer}, targeting 100% line and branch coverage. */
final class WordAnalyzerTest {

  @Test
  void tokenizeBasicTextReturnsLowercaseWords() {
    List<String> result = WordAnalyzer.tokenize("Hello, world! Hello again.");
    assertEquals(List.of("hello", "world", "hello", "again"), result);
  }

  @Test
  void tokenizeEmptyStringReturnsEmptyList() {
    assertTrue(WordAnalyzer.tokenize("").isEmpty());
  }

  @Test
  void tokenizeNoTrailingDelimiterReturnsWord() {
    assertEquals(List.of("abc"), WordAnalyzer.tokenize("abc"));
  }

  @Test
  void tokenizeOnlyDelimitersReturnsEmptyList() {
    assertTrue(WordAnalyzer.tokenize("!!! ,,, ...").isEmpty());
  }

  @Test
  void countWordsBasicCountsCorrectly() {
    List<String> words = List.of("a", "b", "a", "c", "b", "a");
    List<WordCount> counts = WordAnalyzer.countWords(words);
    Map<String, Integer> countsByWord = new HashMap<>();
    for (WordCount wordCount : counts) {
      countsByWord.put(wordCount.word(), wordCount.count());
    }
    assertEquals(3, countsByWord.get("a"));
    assertEquals(2, countsByWord.get("b"));
    assertEquals(1, countsByWord.get("c"));
  }

  @Test
  void countWordsEmptyReturnsEmptyList() {
    assertTrue(WordAnalyzer.countWords(List.of()).isEmpty());
  }

  @Test
  void topWordsBasicReturnsDescendingByCount() {
    List<WordCount> counts =
        List.of(new WordCount("a", 3), new WordCount("b", 5), new WordCount("c", 1));
    List<WordCount> top = WordAnalyzer.topWords(counts, 2);
    assertEquals(2, top.size());
    assertEquals("b", top.get(0).word());
    assertEquals("a", top.get(1).word());
  }

  @Test
  void topWordsCountLargerThanListReturnsAll() {
    List<WordCount> counts = List.of(new WordCount("a", 1));
    List<WordCount> top = WordAnalyzer.topWords(counts, 5);
    assertEquals(1, top.size());
  }

  @Test
  void topWordsCountZeroReturnsEmpty() {
    List<WordCount> counts = List.of(new WordCount("a", 1));
    assertTrue(WordAnalyzer.topWords(counts, 0).isEmpty());
  }

  @Test
  void topWordsDoesNotMutateInput() {
    List<WordCount> counts = List.of(new WordCount("a", 1), new WordCount("b", 2));
    WordAnalyzer.topWords(counts, 1);
    assertEquals(2, counts.size());
    assertEquals("a", counts.get(0).word());
  }

  @Test
  void analyzeEndToEndReturnsExpectedTopWords() {
    String text = "the cat sat on the mat the cat ran";
    List<WordCount> result = WordAnalyzer.analyze(text, 2);
    assertEquals(2, result.size());
    assertEquals("the", result.get(0).word());
    assertEquals(3, result.get(0).count());
    assertEquals("cat", result.get(1).word());
    assertEquals(2, result.get(1).count());
  }

  @Test
  void parseArgsDefaultsUsesTopTen() {
    ParsedArgs parsed = WordAnalyzer.parseArgs(new String[] {"file.txt"});
    assertEquals("file.txt", parsed.filePath());
    assertEquals(10, parsed.topN());
  }

  @Test
  void parseArgsCustomTopUsesGivenValue() {
    ParsedArgs parsed = WordAnalyzer.parseArgs(new String[] {"file.txt", "-n", "3"});
    assertEquals(3, parsed.topN());
  }

  @Test
  void parseArgsLongOptionUsesGivenValue() {
    ParsedArgs parsed = WordAnalyzer.parseArgs(new String[] {"file.txt", "--top", "7"});
    assertEquals(7, parsed.topN());
  }

  @Test
  void parseArgsMissingValueForTopThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WordAnalyzer.parseArgs(new String[] {"file.txt", "-n"}));
  }

  @Test
  void parseArgsInvalidIntegerForTopThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WordAnalyzer.parseArgs(new String[] {"file.txt", "-n", "abc"}));
  }

  @Test
  void parseArgsUnexpectedExtraArgumentThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WordAnalyzer.parseArgs(new String[] {"file.txt", "extra"}));
  }

  @Test
  void parseArgsMissingFileThrows() {
    assertThrows(IllegalArgumentException.class, () -> WordAnalyzer.parseArgs(new String[] {}));
  }

  @Test
  void runSuccessPrintsTopWords(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("sample.txt");
    Files.writeString(file, "dog dog cat");

    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    int exitCode = WordAnalyzer.run(new String[] {file.toString(), "-n", "2"}, out, err);

    assertEquals(0, exitCode);
    String output = outBytes.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("dog\t2"));
    assertTrue(output.contains("cat\t1"));
  }

  @Test
  void runFileNotFoundReturnsOneAndPrintsError(@TempDir Path tempDir) throws IOException {
    Path missing = tempDir.resolve("does_not_exist.txt");

    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    int exitCode = WordAnalyzer.run(new String[] {missing.toString()}, out, err);

    assertEquals(1, exitCode);
    assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("error: file not found"));
  }

  @Test
  void runBadArgumentsReturnsOneAndPrintsError() throws IOException {
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    int exitCode = WordAnalyzer.run(new String[] {}, out, err);

    assertEquals(1, exitCode);
    assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("error:"));
  }
}
