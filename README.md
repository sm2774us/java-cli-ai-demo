# java-cli-ai-demo -- Java 25 AI-refactor teaching example

A deliberately **sub-optimal** CLI (`wordstat`) plus a fully automated,
human-gated pipeline for having an AI agent improve it, benchmark the
improvement, and ship it through PR review, changelog, and semver
release -- using **only free-tier tooling**. This is the Java/Maven
counterpart of `py-cli-ai-demo` and `csharp-cli-ai-demo`, built
like-for-like from the same design.

## What's sub-optimal here (on purpose)

`src/main/java/com/example/wordstat/WordAnalyzer.java`:
- `countWords` uses a `List<WordCount>` with linear search per word →
  **O(n²)**.
- `topWords` does a linear max-scan per output slot → **O(n·k)**.
- `tokenize` builds strings character-by-character instead of using a
  single regex pass.

This is the "before" state an AI agent is asked to optimize (target:
`HashMap<String,Integer>` + `PriorityQueue<WordCount>` or a sorted
stream, i.e. O(n log k)).

## Stack

| Concern | Tool |
|---|---|
| Runtime | Java 25 |
| Build | **Maven** (`mvn compile` / `mvn verify`) |
| Tests | **JUnit 5** + `jacoco-maven-plugin`, **100% line+branch coverage gate** |
| Style | Google Java Style Guide, enforced via `maven-checkstyle-plugin`'s bundled `google_checks.xml`; auto-fixed via `fmt-maven-plugin` (google-java-format) |
| CI | GitHub Actions |
| AI agent (free tier) | **Google Gemini `gemini-3.5-flash-lite`**, called directly via the REST `generateContent` endpoint |

### Why Gemini only, and why raw REST instead of an SDK/Action

Claude's and OpenAI's APIs are both pay-as-you-go with no ongoing free
tier, so neither fits a "free tier for learning" repo. Gemini's
`gemini-3.5-flash-lite` is available free via Google AI Studio, so
it's the only agent wired into automation here. Rather than depend on
a third-party GitHub Action, `scripts/ai_improve.py` (a small Python
orchestration script -- tooling only, not part of the Java deliverable
itself; see the note below on why it isn't Java too) calls the
endpoint directly:

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent" \
  -H "Content-Type: application/json" \
  -H "X-goog-api-key: $GEMINI_API_KEY" \
  -X POST -d '{"contents": [{"parts": [{"text": "..."}]}]}'
```

`scripts/ai_improve.py` is that same call in Python (`urllib`, no
dependencies), with a prompt asking for the full new content of the
source file and its test file, which it then parses and writes to
disk, then verifies with `mvn fmt:format` + `mvn checkstyle:check` +
`mvn verify` (JUnit + JaCoCo) + a real benchmark, retrying with Gemini
up to 4 times on any failure.

> **Why is the orchestration script Python, not Java, in a "Java-only"
> repo?** It's CI tooling that drives the automation (HTTP calls,
> retry logic, shelling out to `mvn`), never compiled into or shipped
> with `wordstat.jar`, exactly analogous to how the C# reference repo
> also keeps this script in Python. `ubuntu-latest` runners ship
> Python 3 preinstalled, so no extra setup step is needed. If you'd
> rather have this in Java too, it's a straightforward port (a small
> class doing `HttpClient` calls + `ProcessBuilder` for `mvn`/`java`)
> -- just ask.

> **GitHub Copilot** and **OpenAI Codex** are covered as manual/optional
> comparison points -- see `docs/copilot-agent-note.md`. Codex CLI has
> no free API tier, so it isn't wired into any automation.

## Benchmark corpus note

`Benchmark.java` generates its corpus with distinct alphabetic words
(`a`, `b`, ..., `z`, `aa`, `ab`, ...) rather than `word0`, `word1`,
etc. -- `tokenize` keeps only letters, so a numeric-suffix vocabulary
would silently collapse into a single token and defeat `--vocab`
entirely. With a genuine `--words 20000 --vocab 15000` corpus the
sub-optimal implementation runs orders of magnitude slower than a
correct `HashMap`/`PriorityQueue` fix, a large enough margin that CI
timing noise can't cause a false failure.

## Local setup

```bash
mvn compile
mvn checkstyle:check
mvn verify   # runs JUnit + enforces the 100% line+branch coverage gate
java -cp target/classes com.example.wordstat.Benchmark --words 20000 --vocab 15000
java -cp target/classes com.example.wordstat.Main some_file.txt -n 10

# try the AI rewrite locally (needs a free Gemini API key from
# https://aistudio.google.com/apikey)
export GEMINI_API_KEY=your_key_here
python3 scripts/ai_improve.py \
  --instruction "improve algorithm and performance" \
  --file src/main/java/com/example/wordstat/WordAnalyzer.java \
  --tests src/test/java/com/example/wordstat/WordAnalyzerTest.java
```

## The automated workflows

### 1) `ai-improve.yml` -- "improve algorithm and performance"

Manual-trigger only (`workflow_dispatch`) -- this **is** the
supervision gate. Runs baseline tests, calls Gemini directly via REST
to rewrite `WordAnalyzer.java` + its tests, then a self-heal loop
(`mvn fmt:format` → hard `checkstyle:check` gate → 100%-coverage
`mvn verify` gate → real benchmark comparison), retrying with Gemini
on any failure up to 4 times, **refusing to open a PR if the change
isn't measurably faster**. Never merges anything -- branch protection
enforces that even if it tried.

```bash
gh workflow run ai-improve.yml -f instruction="improve algorithm and performance"
```

**Required repo secret:** `GEMINI_API_KEY` (free tier from Google AI
Studio -- https://aistudio.google.com/apikey).

### 2) `open-pr.yml` -- for your own hand-written changes

For changes you write yourself, no AI involved: create a feature
branch, commit and push it as usual -- `ci.yml` runs style+test
feedback on every branch push, not just on `main`/`master`. When
you're done:

```bash
gh workflow run open-pr.yml -f branch=feature/my-change -f title="My change"
```

Re-verifies checkstyle + 100% coverage on that exact branch before
opening anything; never approves or merges.

### 3) `changelog-release.yml` -- "changelog"

Manual-trigger only. Takes a PR number and generates a `CHANGELOG.md`
section from the commit log, bumps semver (`patch`/`minor`/`major`),
tags, and cuts a GitHub Release. No AI involved -- deterministic on
purpose. Runs in two phases across two invocations because branch
protection blocks direct pushes to the protected branch: phase 1
writes the changelog entry on its own branch and prints the exact
`open-pr.yml` command to open a PR for it; after you approve and merge
that PR, phase 2 (re-running the same command) tags and publishes the
release. See the workflow file's header comment for the full detail.

```bash
gh workflow run changelog-release.yml -f pr_number=42 -f bump=patch
```

## Human review is enforced, not just convention

Same model as the other two reference repos: branch protection (set up
once via repo Settings → Branches, or `setup-branch-protection.yml`
with a fine-grained PAT -- see that workflow's header comment) requires
at least one human approval and a passing `ci.yml` check before
anything merges, with no bypass even for the repo owner.

```bash
gh pr review <number> --approve
gh pr merge <number> --squash
```

## Economical AI usage (avoiding token-maxxing)

- The improve-workflow prompt is short, scoped to exactly two files,
  names the exact target Java idioms (`HashMap`, `PriorityQueue`,
  `Pattern.compile`) instead of leaving the approach open-ended, and
  `gemini-3.5-flash-lite` is the smallest/cheapest tier that can do
  this reliably.
- The self-heal retry loop (format → style → test → benchmark, up to 4
  attempts) only calls Gemini again when something actually failed,
  feeding it the specific error instead of re-explaining the whole
  task.
- The workflow **fails fast** so a bad or wasteful AI run never
  reaches PR review, and branch protection means a bad PR can't reach
  the default branch even if review is skipped.
- Free-tier key only -- this repo is for building intuition before
  spending money on a paid plan/product.

## Layout

```
src/main/java/com/example/wordstat/WordAnalyzer.java   sub-optimal CLI logic (the "before")
src/main/java/com/example/wordstat/Main.java            tiny System.exit launcher (excluded from coverage)
src/main/java/com/example/wordstat/Benchmark.java       before/after timing harness (tokenize-safe corpus)
src/test/java/com/example/wordstat/WordAnalyzerTest.java  100%-coverage JUnit 5 suite
src/test/java/com/example/wordstat/BenchmarkTest.java      100%-coverage JUnit 5 suite for Benchmark
scripts/ai_improve.py       Gemini REST caller + self-heal retry loop (Maven-driven)
scripts/wrap_comments.py    auto-fixer for over-long // comment lines
pom.xml                                       Maven build: JUnit 5, JaCoCo (100% gate), checkstyle (Google style), fmt-maven-plugin
.github/workflows/ci.yml                       mvn compile + checkstyle + 100%-coverage gate
.github/workflows/ai-improve.yml               manual: Gemini rewrite -> self-heal -> PR
.github/workflows/open-pr.yml                  manual: open a PR for your own branch
.github/workflows/changelog-release.yml        manual: merge PR -> changelog -> semver tag
.github/workflows/setup-branch-protection.yml  manual, one-time: enforce human review
```
