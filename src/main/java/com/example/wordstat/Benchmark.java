package com.example.wordstat;

import java.util.Random;

/**
 * Benchmarks {@link WordAnalyzer#analyze} on a generated corpus.
 *
 * <p>IMPORTANT: {@link WordAnalyzer#tokenize} keeps only alphabetic characters, so a vocabulary
 * like "word0".."word9999" would collapse into a single token ("word") after tokenization --
 * silently defeating {@code --vocab} entirely. This generator instead builds distinct
 * alphabetic-only words (bijective base-26, like spreadsheet column names: a, b, ..., z, aa, ab,
 * ...), so the requested vocabulary size is the actual vocabulary size.
 *
 * <p>Usage: {@code java -cp target/classes com.example.wordstat.Benchmark --words 20000 --vocab
 * 15000}
 */
public final class Benchmark {

  private Benchmark() {}

  /**
   * Converts a non-negative index to a unique lowercase-letter word.
   *
   * @param index zero-based index
   * @return a unique lowercase string of letters
   */
  public static String indexToWord(int index) {
    StringBuilder letters = new StringBuilder();
    int n = index + 1;
    while (n > 0) {
      n -= 1;
      letters.append((char) ('a' + (n % 26)));
      n /= 26;
    }
    return letters.reverse().toString();
  }

  /**
   * Builds a synthetic corpus with a genuinely distinct vocabulary.
   *
   * @param wordCount total number of words to generate
   * @param vocabSize number of distinct words in the vocabulary
   * @param seed random seed for reproducibility
   * @return a space-separated string of wordCount words
   */
  public static String makeCorpus(int wordCount, int vocabSize, long seed) {
    Random rng = new Random(seed);
    String[] vocab = new String[vocabSize];
    for (int i = 0; i < vocabSize; i++) {
      vocab[i] = indexToWord(i);
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < wordCount; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(vocab[rng.nextInt(vocabSize)]);
    }
    return sb.toString();
  }

  /**
   * Times a single analyze() call.
   *
   * @param wordCount corpus size in words
   * @param vocabSize distinct word count
   * @param topN number of top words requested
   * @return elapsed wall-clock seconds
   */
  public static double run(int wordCount, int vocabSize, int topN) {
    String text = makeCorpus(wordCount, vocabSize, 42L);
    long start = System.nanoTime();
    WordAnalyzer.analyze(text, topN);
    long elapsedNanos = System.nanoTime() - start;
    return elapsedNanos / 1_000_000_000.0;
  }

  /**
   * CLI entry point for the benchmark.
   *
   * @param args command-line arguments: --words N --vocab N
   */
  public static void main(String[] args) {
    int words = 20000;
    int vocab = 15000;

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--words") && i + 1 < args.length) {
        words = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("--vocab") && i + 1 < args.length) {
        vocab = Integer.parseInt(args[i + 1]);
        i++;
      }
    }

    double elapsed = run(words, vocab, 10);
    System.out.printf(
        "words=%d vocab=%d elapsed_seconds=%.4f%n", words, vocab, elapsed);
  }
}
