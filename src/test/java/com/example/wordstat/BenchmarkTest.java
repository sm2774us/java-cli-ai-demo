package com.example.wordstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link Benchmark}. */
final class BenchmarkTest {

  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream capturedOut;

  @BeforeEach
  void redirectStdout() {
    capturedOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStdout() {
    System.setOut(originalOut);
  }

  @Test
  void indexToWordProducesDistinctAlphabeticWords() {
    assertEquals("a", Benchmark.indexToWord(0));
    assertEquals("z", Benchmark.indexToWord(25));
    assertEquals("aa", Benchmark.indexToWord(26));
    assertEquals("ab", Benchmark.indexToWord(27));
  }

  @Test
  void indexToWordAllValuesAreDistinct() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      assertTrue(seen.add(Benchmark.indexToWord(i)), "duplicate at index " + i);
    }
  }

  @Test
  void makeCorpusProducesRequestedWordCount() {
    String corpus = Benchmark.makeCorpus(50, 10, 1L);
    String[] parts = corpus.split(" ");
    assertEquals(50, parts.length);
  }

  @Test
  void makeCorpusIsDeterministicForSameSeed() {
    String a = Benchmark.makeCorpus(20, 5, 7L);
    String b = Benchmark.makeCorpus(20, 5, 7L);
    assertEquals(a, b);
  }

  @Test
  void runReturnsNonNegativeElapsedSeconds() {
    double elapsed = Benchmark.run(100, 20, 5);
    assertTrue(elapsed >= 0.0);
  }

  @Test
  void mainWithDefaultsPrintsExpectedFormat() {
    Benchmark.main(new String[] {"--words", "50", "--vocab", "10"});
    String output = capturedOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("words=50 vocab=10 elapsed_seconds="));
  }

  @Test
  void mainWithNoArgsUsesDefaults() {
    Benchmark.main(new String[] {});
    String output = capturedOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("words=20000 vocab=15000 elapsed_seconds="));
  }

  @Test
  void mainIgnoresUnrecognizedTrailingFlag() {
    Benchmark.main(new String[] {"--unknown"});
    String output = capturedOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("elapsed_seconds="));
  }

  @Test
  void mainIgnoresWordsFlagWithNoTrailingValue() {
    Benchmark.main(new String[] {"--words"});
    String output = capturedOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("words=20000 vocab=15000 elapsed_seconds="));
  }

  @Test
  void mainIgnoresVocabFlagWithNoTrailingValue() {
    Benchmark.main(new String[] {"--vocab"});
    String output = capturedOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("words=20000 vocab=15000 elapsed_seconds="));
  }
}
