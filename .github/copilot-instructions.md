# Copilot instructions for incident-triage-agent

## Project purpose

A lean, focused Spring Boot repository demonstrating tool-calling AI agents and
structured outputs using Spring AI. It intentionally isolates Spring AI, `@Tool`
method binding, agent orchestration, and structured JSON outputs — it is **not**
a production-ready operations tool (no auth, no persistence, no real cloud/APM
integrations, no external/production vector database). A minimal, in-memory
vector-based RAG tool is included alongside keyword search purely to illustrate
the pattern (see below). Keep changes aligned with this narrow scope; do not
add enterprise boilerplate unless explicitly requested.

## Tech stack

- **Language:** Java 25
- **Framework:** Spring Boot 4.1.x
- **AI:** Spring AI 2.0.x, with `spring-ai-starter-model-ollama` (default) and
  `spring-ai-starter-model-openai` (optional, via the `openai` Spring profile)
- **Default model:** `qwen3:14b` via a locally running Ollama server (open-source,
  offline-friendly, no API key required)
- **Embedding model:** `nomic-embed-text` via Ollama, used only by the optional
  `searchRunbookSemantic` RAG tool (in-memory `SimpleVectorStore`, no external
  vector database)
- **Build tool:** Maven (`./mvnw`)

## Agent design conventions

These exist because they are the substance of the demo — don't quietly undo them:

- **Evidence must stay grounded.** `IncidentAssessment.evidence` is never taken from the
  model. Every `@Tool` method records its real argument and real result via
  `ToolCallRecorder`, and `TriageAgentService` substitutes that recorded trail into the
  response. Any new tool must record its result the same way, or its work becomes
  invisible in the evidence trail.
- **`ToolCallRecorder` is per-run and thread-bound.** `TriageAgentService` brackets each
  run with `start()` and a `finally { clear(); }`. Keep that bracketing, or entries leak
  between requests sharing a pooled thread.
- **Never combine `.tools(...)` with `.entity(...)` in one `ChatClient` call.**
  `TriageAgentService` deliberately runs two calls — investigate (tools, prose), then
  structure (no tools, `entity`). Spring AI implements `entity(...)` by appending "only
  provide a RFC8259 compliant JSON response" to the user message, which overrides the
  instruction to call tools: the model skips tool calling and returns a well-formed
  assessment with empty `evidence`, with no error anywhere. Merging the two calls back
  together silently destroys the evidence guarantee above.
- **The severity rubric lives in two places** — `ASSESSMENT_SYSTEM_PROMPT` in
  `TriageAgentService` and the `Severity` enum's Javadoc. Change both together, otherwise
  the code documents a scale the model was never given.
- **Both runbook tools must search the same corpus, chunked the same way** (via
  `Runbooks.splitSections`). That equivalence is what makes comparing lexical and
  semantic retrieval meaningful.
- **Keyword search must stay selective.** Stop-word filtering and whole-word matching are
  deliberate: a naive `contains()` matches "in" inside "instance", making every query
  return every runbook and rendering the tool (and its contrast with semantic search)
  worthless. `RunbookSearchToolTest` guards this.

## Package conventions

- Group ID / base package: **`eu.volsch.lab`**
- Sub-packages: `agent`, `model`, `tool`, `web`, `config` — do **not** repeat
  `lab` or any other segment already present in the base package (no
  duplicate hierarchy segments, e.g. never `eu.volsch.lab.lab.*`).
- Mirror the main package structure under `src/test/java` for test classes.
- Shared runbook constants and helpers (classpath location, document metadata key,
  LLM excerpt formatting) live in the package-private `tool/Runbooks` utility class
  — reuse it rather than re-declaring `"classpath:runbooks/*.md"`, `"filename"` or
  the excerpt truncation logic, so the keyword and semantic tools always return
  evidence to the model in an identical format.
- Use locale-independent `String.toLowerCase(Locale.ROOT)` (never the no-arg
  overload) for any case-insensitive matching, so behavior doesn't change under a
  different default locale.

## Code style & formatting

- Formatting is enforced with **Google Java Format** via the **Spotless** Maven
  plugin, bound to the `verify` phase (`spotless-maven-plugin`, `googleJavaFormat`,
  `GOOGLE` style).
- Before committing, auto-fix formatting with:
  ```bash
  ./mvnw spotless:apply
  ```
- CI/build will fail (`spotless:check`) if code is not correctly formatted —
  always run `spotless:apply` after making changes.

## Quality checks

- Static analysis is enforced with **SpotBugs** (`spotbugs-maven-plugin`, bound
  to the `verify` phase, `effort=Max`, `threshold=Medium`).
- Justified suppressions (confirmed false positives or accepted, documented
  risks only) go in `spotbugs-exclude.xml` with an explanatory comment — never
  use it to silently hide real issues.
- **Code coverage** is enforced with **JaCoCo** (`jacoco-maven-plugin`), bound to
  the `verify` phase: `jacoco-check` fails the build below **80% minimum**
  instruction and branch coverage (bundle-wide). Trivial/not-meaningfully-testable
  code is excluded from the measured bundle in the plugin `<excludes>` config: the
  `@SpringBootApplication` bootstrap class, `@Configuration` classes (`config`
  package), and plain data-carrier records/enums (`model` package,
  `web/TriageRequest.java`) — extend that exclude list, don't just skip writing
  tests, if you add another genuinely trivial class.
  - Mockito's inline mock maker (default since Mockito 5) retransforms mocked
    classes' bytecode in place, which corrupts JaCoCo's coverage bookkeeping for
    *any* class touched in the same test JVM fork if Mockito self-attaches at
    runtime. This is fixed by loading Mockito as a **static javaagent** in the
    `maven-surefire-plugin` `<argLine>`, combined with JaCoCo's own agent (JaCoCo
    is configured with `<propertyName>jacocoArgLine</propertyName>` instead of the
    default `argLine` property so both can be concatenated). Keep this combined
    `argLine` if you touch the surefire/jacoco plugin config, or `@MockitoBean`/
    `@Mock`-using tests will silently show 0% coverage for the classes they mock.
  - HTML report: `target/site/jacoco/index.html` (generated by `./mvnw test` or
    `./mvnw verify`, from the fast unit/slice tests only — the Testcontainers
    integration test is excluded from this run and from the coverage numbers).
- Run the full quality gate locally with:
  ```bash
  ./mvnw verify
  ```

## Javadoc

- All public classes, constructors, and methods must have Javadoc (summary plus
  `@param`/`@return`/`@throws` as applicable).
- Package-private and private helper methods that aren't trivially named should
  also get a short Javadoc summary for clarity.
- Keep comments and Javadoc focused on *why*/behavior, not restating the method
  signature.

## Testing

- Fast unit/slice tests (tool logic, `@WebMvcTest`, `@SpringBootTest` context
  load) run by default via:
  ```bash
  ./mvnw test
  ```
- A Docker-dependent Testcontainers integration test
  (`TriageAgentIntegrationTest`) spins up a real Ollama server, pulls a small
  open-source chat model (`qwen2.5:1.5b`) and the `nomic-embed-text` embedding
  model, and exercises the full tool-calling + structured-output loop
  end-to-end (including the semantic search tool). It is tagged `integration`
  and excluded from the default `mvn test` run (`maven-surefire-plugin`
  `excludedGroups`). Run it explicitly with:
  ```bash
  ./mvnw test -Pintegration-test
  ```
  One of its tests asserts `evidence` is **non-empty** against the real model. That
  assertion is the only thing that catches tool calling silently not happening — keep it.
- The `searchRunbookSemantic` tool indexes its `VectorStore` **lazily**, on
  first call — never at application-context startup — so plain `@SpringBootTest`
  context-loads checks never require a live embedding model.
- New tool/service logic should get fast unit tests; only add to the
  Testcontainers integration test if genuinely exercising the real model loop.

## Continuous Integration

- `.github/workflows/build-and-test.yml` runs on every push/PR to `main` (and via
  `workflow_dispatch`):
  - `build-and-test` job: `./mvnw verify` (build, fast tests, Spotless, SpotBugs,
    JaCoCo coverage gate); uploads the JaCoCo HTML report as a build artifact.
  - `integration-test` job (`needs: build-and-test`): `./mvnw test -Pintegration-test`
    on a GitHub-hosted Ubuntu runner — Docker is preinstalled there, so
    Testcontainers/Ollama work with no extra setup; it has a 20-minute timeout for
    the `qwen2.5:1.5b` + `nomic-embed-text` model pulls.
  - `license-report` job (independent, runs in parallel): generates the dependency
    license report (`./mvnw license:aggregate-third-party-report`) and uploads it as
    a build artifact. Informational only, does not fail the build.
  - `dependency-review` job (pull requests only, `if: github.event_name ==
    'pull_request'`): runs GitHub's official `actions/dependency-review-action`
    against an `allow-licenses` SPDX list, **failing the PR** if a newly introduced
    dependency's license isn't on it, or it has a known high/critical vulnerability.
    If a legitimately compatible dependency gets flagged (e.g. an unusual "GPL ...
    WITH Classpath-exception" SPDX expression), add it to `allow-dependencies-licenses`
    in the workflow rather than widening the allow-list.
- All third-party GitHub Actions (`actions/checkout`, `actions/setup-java`,
  `actions/upload-artifact`, `actions/dependency-review-action`) are pinned to their
  latest major version tags — check https://github.com/<action>/releases when adding
  new actions or bumping these, and keep both workflow files (`build-and-test.yml`
  and `copilot-setup-steps.yml`) consistent with each other.
- This is separate from `.github/workflows/copilot-setup-steps.yml`, which only
  preconfigures Copilot's own cloud-agent environment and is not a CI/test workflow.
- If you add a new opt-in test suite/profile, wire it into this workflow (a new job
  or an added step) and document it here and in the README's "Continuous
  Integration" section.

## Maven command reference

| Purpose                              | Command                              |
|---------------------------------------|---------------------------------------|
| Compile                               | `./mvnw compile`                     |
| Run fast tests                        | `./mvnw test`                        |
| Run Docker-based integration test     | `./mvnw test -Pintegration-test`     |
| Auto-format code                      | `./mvnw spotless:apply`              |
| Full quality gate (build+tests+lint+spotbugs) | `./mvnw verify`               |
| Run the app locally                   | `./mvnw spring-boot:run`             |
| Audit dependency licenses             | `./mvnw license:aggregate-third-party-report` |

## License

Licensed under Apache License, Version 2.0 (see `LICENSE`, which carries the
`Copyright 2026 Volker Schmidt` notice). New files do not need individual per-file
license headers — one root `LICENSE` file is sufficient. Trademark attributions and
third-party notices live in the root `NOTICE` file: if you add a dependency on, or
prominent reference to, another vendor's trademarked product, add it there rather
than to the README. Before
adding a new dependency, prefer permissive licenses (Apache 2.0, MIT, BSD) or
dual-licensed frameworks usable under a permissive option; avoid GPL/AGPL-only
dependencies, which are incompatible with distributing this project under Apache 2.0.
This is enforced automatically on pull requests by the `dependency-review` CI job
(see "Continuous Integration" above) via GitHub's dependency graph — a new
dependency that fails it must be replaced or justified, not suppressed. Note this
job only runs on `pull_request` (it diffs base vs. head), not on direct pushes.
