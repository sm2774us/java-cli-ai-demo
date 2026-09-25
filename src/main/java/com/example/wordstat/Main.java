package com.example.wordstat;

import java.io.IOException;

/**
 * CLI launcher.
 *
 * <p>Deliberately kept to a single trivial delegating line: {@link WordAnalyzer#run} holds all
 * real logic and is fully unit tested; this class only exists because {@code System.exit} cannot
 * be safely exercised from a unit test without killing the test JVM, so it is excluded from the
 * coverage gate (see the jacoco-maven-plugin {@code excludes} configuration in {@code pom.xml}).
 */
public final class Main {

  private Main() {}

  /**
   * CLI entry point.
   *
   * @param args command-line arguments
   * @throws IOException if the input file cannot be read
   */
  public static void main(String[] args) throws IOException {
    System.exit(WordAnalyzer.run(args, System.out, System.err));
  }
}
