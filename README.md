# Spring AI TypeSafe

A Java client for the [TypeSafe AI](https://docs.typesafe.ai/introduction) **System One**
API (`jev`), built on Spring `RestClient` and Jackson 3, plus Spring AI integrations that
use it as an LLM-as-a-judge, a guardrail, a RAG post-processor and a tool index.

📖 **Full documentation in [`docs/`](docs/index.md)** — built with MkDocs Material:

```bash
pip install mkdocs-material && mkdocs serve
```

Jev is not a chat model. It takes a **state** and a map of **typed questions**, and returns
structured answers — no text generation, no JSON to parse, no schema to coerce a model
into honouring. Every question in a call is answered against the same state in parallel, so
the documented pattern is *atomic questions, composed in code* rather than one broad rubric
prompt.

```java
SystemOneResponse response = typeSafeClient.systemOne(
        "Help! My payouts have been failing for 3 days.",
        Map.of(
            "is_urgent",   Noul.of("Does this convey urgency?"),
            "department",  Choice.builder()
                    .instructions("Which team should handle this?")
                    .option("billing",   "Payments, invoicing, refunds")
                    .option("technical", "Bugs, outages, integrations")
                    .option("sales",     "Pricing, upgrades, new accounts")
                    .build(),
            "frustration", Score.of("How frustrated is the customer?",
                    "Calm", "Frustrated", "Very angry")));

double urgency    = response.noulValue("is_urgent");        // 0.92
String department = response.choiceValue("department");     // "technical"
double confidence = response.choice("department").confidence();  // 0.82
double frustration = response.scoreValue("frustration");    // 1.6
```

## Modules

| Module | What it is | Depends on |
| ------ | ---------- | ---------- |
| `typesafe-java-sdk` | The client: `TypeSafeClient`, `TypeSafeApi`, the question/answer model, retries, typed exceptions | `spring-web`, Jackson 3 |
| `spring-ai-starter-typesafe` | `spring.ai.typesafe.*` properties and a `TypeSafeClient` bean | the SDK, Boot autoconfigure |
| `typesafe-spring-ai` | `JevJudge`, the advisors, and the RAG and tool-search integrations | the SDK, `spring-ai-client-chat`; `spring-ai-rag` and `spring-ai-tool-search-tool` optional |
| `typesafe-bom` | Bill of materials pinning the three published artifacts | — |
| `examples` | Seven runnable demos, from a plain-Java quickstart to a RAG pipeline | starter, `typesafe-spring-ai`, Anthropic |

Java 17, Spring Boot 4.0.7, Spring AI 2.0.1 — the same versions as `voxxeddays2026-demo`,
so `typesafe-spring-ai` drops into that reactor unchanged.

### Packages

`typesafe-java-sdk` splits along the same lines the Python SDK's own modules do
(`types.questions`, `types.responses`, `exceptions`, `constants`):

| Package | Holds |
| ------- | ----- |
| `org.springaicommunity.typesafe` | `TypeSafeClient`, `RetryPolicy`, `JsonContent`, `TypeSafeConstants`, `TypeSafeModels`, `JevBatchOptions`, `JevBatchResult` — the vocabulary you touch constantly |
| `…typesafe.question` | `Question`, `Noul`, `Choice`, `Score`, `NoulCriteria`, `QuestionType`, `SystemOneRequest` |
| `…typesafe.response` | `Answer`, `NoulAnswer`, `ChoiceAnswer`, `ScoreAnswer`, `UnknownAnswer`, `SystemOneResponse`, `Usage`, `ListModelsResponse`, `ModelMetadata` |
| `…typesafe.exception` | the whole failure hierarchy |
| `…typesafe.api` | `TypeSafeApi`, `TypeSafeResponseErrorHandler` — the only code that knows about HTTP |

`Question` and `Answer` are sealed, so each keeps its permitted subtypes in its own
package: without a `module-info.java`, a cross-package `permits` clause does not compile.

`typesafe-spring-ai` adds four packages, and the starter adds `…typesafe.autoconfigure`:

| Package | Holds | Needs |
| ------- | ----- | ----- |
| `…typesafe.judge` | `JevJudge`, `JevCriterion`, `JevVerdict`, `JevFinding`, `JevConfidenceGate`, `JevCompositeScore`, `JevConsistency`, `JevEvaluator` | — |
| `…typesafe.advisor` | `JevSelfRefineAdvisor`, `JevGuardrailAdvisor`, `JevGuardrail` | — |
| `…typesafe.rag` | `JevDocumentReranker`, `JevDocumentFilter` | `spring-ai-rag` |
| `…typesafe.toolsearch` | `JevToolIndex` | `spring-ai-tool-search-tool` |

The last two dependencies are declared `<optional>true</optional>`, so an application that
does not do RAG or tool search never sees those classes and never pays for the dependency.
Declare the matching artifact yourself to use them; `examples` does.

## Naming

Names track the official SDKs so knowledge transfers between them. Types that mirror
something the Python or JavaScript SDK exposes carry its name; `Jev`-prefixed types are
this project's own additions with no counterpart there.

| This SDK | Python SDK | JavaScript SDK |
| -------- | ---------- | -------------- |
| `TypeSafeClient` | `TypeSafeClient` | `TypeSafeClient` |
| `TypeSafeConstants` | `typesafe_sdk.constants` | `ENV` |
| `RetryPolicy` | `RetryPolicy` | `RetryPolicy` |
| `Noul` `Choice` `Score` | `Noul` `Choice` `Score` | `NoulQuestion` `ChoiceQuestion` `ScoreQuestion` |
| `NoulCriteria` | `NoulCriteria` | — |
| `Question` `Answer` | `Question` `Answer` | `Question` |
| `NoulAnswer` `ChoiceAnswer` `ScoreAnswer` | same | `NoulResponse` `ChoiceResponse` `ScoreResponse` |
| `SystemOneRequest` `SystemOneResponse` | same | `SystemOneRequest` `SystemOneResult` |
| `Usage` `ModelMetadata` `ListModelsResponse` | same | `Usage` `ModelCard` |
| `JsonContent` | `JSONContent` | `EntryType` |
| `TypeSafeException` | `TypeSafeError` | `TypeSafeError` |
| `TypeSafeApiException` | `TypeSafeAPIError` | `APIError` |
| `TypeSafeRateLimitException` | `TypeSafeRateLimitError` | `RateLimitError` |
| `TypeSafeApiTimeoutException` | `TypeSafeAPITimeoutError` | `APITimeoutError` |
| `TypeSafeOverloadedException` (extends `TypeSafeInternalServerException`) | — (folded into `TypeSafeInternalServerError`) | — (folded into `InternalServerError`) |
| `TypeSafeMissingAnswerException` `TypeSafeAnswerTypeException` | — (`KeyError` / `AttributeError`) | — |
| `TypeSafeErrorDetail` | — | — |
| — | — | `APIUserAbortError` |
| `JevJudge` `JevVerdict` `JevSelfRefineAdvisor` | — | — |

The remaining status-mapped failures — `TypeSafeBadRequestException`,
`TypeSafeAuthenticationException`, `TypeSafePermissionDeniedException`,
`TypeSafeNotFoundException`, `TypeSafeUnprocessableEntityException`,
`TypeSafeInternalServerException`, `TypeSafeApiConnectionException` and
`TypeSafeApiResponseValidationException` — each carry their Python name with the suffix
swapped. Twelve of this SDK's fifteen failure types are a one-to-one mirror of Python's,
which the JavaScript SDK mirrors in turn with the `TypeSafe` prefix dropped from everything
but the base. The three that are not — `TypeSafeOverloadedException` and the two client-side
answer failures — are explained below.

Five deliberate departures from a literal transcription:

- **`...Exception`, not `...Error`.** Both SDKs suffix failures `Error`, but in Java
  `Error` names a `java.lang.Error` — an unrecoverable JVM condition you are not supposed
  to catch. Every failure here is a `RuntimeException`, so the `Exception` suffix says
  what is true. The prefix and the shape of the hierarchy are Python's.
- **`TypeSafeOverloadedException` exists here and in neither official SDK.** The API
  documents `529 Overloaded` — "TypeSafe is temporarily overloaded, retry after a short
  delay" — but 529 is numerically a 5xx, so Python and JavaScript both fold it into their
  internal-server error. This SDK names it, because a transient overload worth retrying and
  a server fault that is not are worth telling apart.

  It is a **subclass of `TypeSafeInternalServerException`**, not a sibling, so the
  refinement costs nothing in portability: `catch (TypeSafeInternalServerException)` still
  catches an overload, exactly as `except TypeSafeInternalServerError` does in Python, and
  catching `TypeSafeOverloadedException` narrows to the one 5xx you may want to treat
  differently. A sibling would have silently dropped 529 out of the handler a Python user
  had just translated.
- **`JsonContent`, not `JSONContent`.** Java lowercases an acronym's tail (`JsonNode`,
  `JsonPath`, `HttpHeaders`).
- **`TypeSafeMissingAnswerException` and `TypeSafeAnswerTypeException` have no counterpart
  because Python does not need one.** There, `response.answers["nope"]` already raises
  `KeyError` and reading `.choice` off a noul raises `AttributeError`; the language supplies
  the failure. Java would otherwise surface the same two mistakes as a bare
  `NullPointerException` or `ClassCastException`, which name the symptom rather than the
  cause. These are the typed translation of behaviour Python gets for free, not extra
  concepts.
- **`TypeSafeErrorDetail` is prefixed `TypeSafe`, not `Jev`, despite having no counterpart.**
  Neither official SDK parses the error body — both stop at the raw string. The rule above
  would make this a `Jev` type, but the prefix here tracks *whose vocabulary a type
  belongs to*, and `detail`, `error_type` and the validation-error shape are the API's, not
  this project's. The `Jev` prefix stays for the judge layer, which invents its own concepts.

The Spring Boot property prefix is `spring.ai.typesafe`, following the Spring AI
convention of naming the provider rather than the model — the same reason the OpenAI
starter is `spring.ai.openai` and not `spring.ai.gpt`.

## The three primitives

| Primitive | Ask | Get back |
| --------- | --- | -------- |
| `Noul` | a yes/no question | `noul`, a truth value in `[0, 1]`. **No confidence** — the value already is the certainty, so it is thresholded directly. |
| `Choice` | pick one label | `choice`, `probabilities` per label, `confidence` |
| `Score` | place on an ordered rubric | `score` (probability-weighted, continuous), `legend`, `probabilities` per level, `confidence` |

`state`, `instructions` and every `criteria` description accept a string, a JSON object, a
JSON array or `null` — the documentation's `EntryType`. That union is modelled as
`JsonContent`, and every builder method is overloaded for `String`, `Map`, `List` and
`JsonContent`, so the structured-instruction recipes from the docs are expressible:

```java
Noul.builder()
    .instructions(Map.of(
        "question", "Does the `message` ask the recipient to disclose a credential?",
        "inspect",  "message",
        "focus",    "A request to send the credential, not to reset it."))
    .whenTrue(Map.of(
        "what",     "Asks the recipient to reply with a password, PIN or one-time code",
        "examples", List.of("Reply with your password", "Send us the 6-digit code")))
    .whenFalse("No sensitive credential is requested")
    .build();
```

Answers are a sealed hierarchy — `NoulAnswer`, `ChoiceAnswer`, `ScoreAnswer` and
`UnknownAnswer`. `UnknownAnswer` is the forward-compatibility escape hatch: a primitive
added to Jev after this SDK version arrives as raw JSON rather than failing the call.

```java
Answer answer = response.answer("department");
if (answer instanceof ChoiceAnswer choice && choice.confidence() >= 0.8) {
    route(choice.value());
}
```

## Client

```java
TypeSafeClient client = TypeSafeClient.builder()
        .apiKey(System.getenv(TypeSafeConstants.API_KEY_ENV))
        .baseUrl(TypeSafeConstants.DEFAULT_BASE_URL)
        .defaultModel(TypeSafeModels.JEV_LATEST)
        .timeout(TypeSafeConstants.DEFAULT_TIMEOUT)
        .retryPolicy(RetryPolicy.defaults())
        .build();
```

Every one of those is optional. `TypeSafeClient.builder().build()` reads `TYPESAFE_API_KEY`,
`TYPESAFE_BASE_URL` and `TYPESAFE_DEFAULT_MODEL` from the environment — the same variables
the Python and JavaScript SDKs read, named in `TypeSafeConstants`.
`apiKey(Supplier<String>)` is consulted per request, so a rotated key takes effect without
rebuilding the client.

**Errors** map to their own exception types, so callers branch on the failure rather than a
number:

```
TypeSafeException
└─ TypeSafeApiException          status, body, headers, endpoint, requestId,
   │                             errorType, errorMessage, validationErrors
   ├─ TypeSafeBadRequestException              400
   ├─ TypeSafeAuthenticationException          401
   ├─ TypeSafePermissionDeniedException        403
   ├─ TypeSafeNotFoundException                404
   ├─ TypeSafeUnprocessableEntityException     422
   ├─ TypeSafeRateLimitException               429   + retryAfterMs
   ├─ TypeSafeInternalServerException          5xx
   │  └─ TypeSafeOverloadedException           529   *
   └─ TypeSafeApiResponseValidationException            + fieldPath
TypeSafeApiConnectionException
└─ TypeSafeApiTimeoutException
TypeSafeMissingAnswerException, TypeSafeAnswerTypeException    (client side)  *
```

`*` marks the three types with no counterpart in the Python or JavaScript SDK; see
[Naming](#naming) for why each is here. Everything else mirrors Python one for one.

Which status you actually get is not always the obvious one, so branch on the exception
rather than guessing: a **rejected** key is a 401 but a **missing** one is a 403, an unknown
model or a malformed question is a 400 rather than a 404, a body that fails request
validation is a 422, and a 404 only ever means an unrouted path.

**Error bodies** arrive in a single `detail` envelope that takes three shapes, all three read
by `TypeSafeErrorDetail` and reachable from any `TypeSafeApiException`:

| Response | `detail` is | What you get |
| -------- | ----------- | ------------ |
| 400, 401, 403 | an object | `errorType()` (`authentication_error`, `api_usage_error`, …) and `errorMessage()` |
| 422 | an array | `validationErrors()`, each with `type()`, `loc()`, `path()` and `msg()` |
| 404 | a string | `errorMessage()` |

```java
catch (TypeSafeApiException ex) {
    log.warn("{} failed: {} [{}] (request {})",
            ex.endpoint(), ex.errorMessage(), ex.errorType(), ex.requestId());
    ex.validationErrors().forEach(e -> log.warn("  {}: {}", e.path(), e.msg()));
}
```

The parse is best-effort and lazy: `body()` always keeps the raw text, so an unrecognised
shape costs nothing and is simply reported as-is.

**Retries** default to the same policy as the official SDKs: 2 retries, exponential backoff
from 500ms to 5s with 25% jitter subtracted, on 408/429/5xx and connection failures, within
a 30s budget. A 429 carrying `retry-after-ms` overrides the computed backoff. The budget
counts the next attempt's own HTTP timeout, not just the wait before it, so a call never
overruns it by a whole request: when the time left cannot fit another attempt, the last
failure is thrown instead. Declare the transport's real timeout on the builder even when
supplying your own `RestClient.Builder`, since that is the figure the budget uses.

Every response carries the `x-typesafe-request-id` header as `response.requestId()`, so the
id travels with the data it describes.

## Spring Boot

```properties
spring.ai.typesafe.api-key=${TYPESAFE_API_KEY}
spring.ai.typesafe.model=jev-latest
spring.ai.typesafe.timeout=10s
spring.ai.typesafe.retry.max-retries=2
spring.ai.typesafe.retry.initial-backoff=500ms
spring.ai.typesafe.retry.total-timeout=30s
```

A `TypeSafeClient` bean appears once `api-key` is set — an application that has not been given a
key still starts. Defining your own `TypeSafeClient` bean switches the auto-configuration off.

## LLM-as-a-judge

`JevSelfRefineAdvisor` is a self-refine advisor: it judges each response, and when the
response falls short it feeds the defect back into the prompt and tries again. It is a
drop-in replacement for a hand-rolled judge advisor that prompts a second chat model and
parses a rating out of it.

```java
JevJudge judge = JevJudge.builder(typeSafeClient)
    .score("helpfulness", Score.builder()
        .instructions("How well does `assistant_answer` address `user_question`?")
        .level("Terrible: irrelevant or off-topic")
        .level("Mostly unhelpful: misses the main point")
        .level("Mostly helpful: minor gaps remain")
        .level("Excellent: fully and correctly addressed")
        .build(), 2.0)
    .noul("is_plausible", Noul.builder()
        .instructions("Are the numeric values in `assistant_answer` physically plausible?")
        .whenFalse("At least one value is impossible")
        .build(), 0.7)
    .minConfidence(0.5)
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(new WeatherTools())
    .defaultAdvisors(JevSelfRefineAdvisor.builder()
            .judge(judge)
            .maxRepeatAttempts(3)
            .build())
    .build();
```

Three things are different from a judge model:

- **Several independent checks in one call.** Relevance, plausibility and groundedness are
  separate questions with separate thresholds. An answer that is fluent and on topic but
  quotes an impossible temperature fails on `is_plausible` alone; a single overall rating
  averages that problem away.
- **Feedback is synthesised, not generated.** Jev returns numbers, never prose, so the
  advisor builds the retry feedback from the structured answer and the rubric the caller
  wrote. It is deterministic and more specific than a judge model's commentary:
  `helpfulness: rated "Mostly unhelpful: misses the main point" (1.20), needs to reach 2.00
  which is "Mostly helpful: minor gaps remain"`. Override it with
  `JevJudge.Builder#feedbackRenderer`.
- **Low confidence is undecided, not failed.** Confidence is a statistic over the answer's
  own distribution: a flat one means the levels or options did not separate well for this
  state, which is not the same as the answer being wrong. Those criteria are reported as
  `INCONCLUSIVE` and do not block, unless `failOnInconclusive(true)`.

`adviseStream` is unsupported — a verdict needs the whole answer, so there is nothing
useful to emit incrementally.

`JevJudge` also implements Spring AI's evaluation SPI through `JevEvaluator`, which makes it
a drop-in replacement for `RelevancyEvaluator` and `FactCheckingEvaluator` — both of which
prompt a second chat model and come down to `"yes".equalsIgnoreCase(response)`.
`EvaluationResponse` has room for one score, so that is the fraction of criteria that
passed, with the per-criterion detail in its metadata.

## Batches

Most of this API is used by packing many questions into one call, where the service reads
the state once and answers them in parallel. A batch is the other shape — the *same*
question asked about many different states, which is irreducibly one call per item:

```java
List<JevBatchResult<SystemOneResponse>> results =
        client.systemOneAll(requests, JevBatchOptions.ofConcurrency(4));
```

Results stay aligned with the input, so `results.get(i)` always answers `requests.get(i)`.
Each request carries the client's own retry policy, and a request that fails for good does
not abort the batch — its slot holds the typed failure and the rest still run, unless
`failFast` is set. Four at a time is the default and sits far below the published limit of
1,200 requests per minute.

Reach for it when the states differ, not when the questions do. Several questions about one
state belong in a single `systemOne` call, which is cheaper and faster for the same answers.

## RAG

Two `DocumentPostProcessor` implementations, so both drop into `RetrievalAugmentationAdvisor`
unchanged. They need the optional `spring-ai-rag` dependency.

```java
RetrievalAugmentationAdvisor.builder()
    .documentRetriever(retriever)
    .documentPostProcessors(
            JevDocumentFilter.builder(typeSafeClient).build(),
            JevDocumentReranker.builder(typeSafeClient).topK(5).build())
    .build();
```

**`JevDocumentReranker`** asks one noul per passage — *could this answer the query?* — and
sorts on the result. Similarity is a good way to find candidates and a poor way to order
them, because a passage can be about the right subject without containing the answer.

**`JevDocumentFilter`** asks four questions per passage and routes on the first threshold
that matches, safety before usefulness:

| Answer | Then |
| ------ | ---- |
| `contains_prompt_injection` > 0.70 | excluded |
| `contradicts_query_premise` > 0.70 | kept, tagged `CONFLICTING` |
| `is_relevant` < 0.45 | excluded |
| `contains_answer_evidence` > 0.55 | included |
| otherwise | excluded |

The injection check is the one worth having: retrieved text is untrusted input, and a
pipeline that passes it through unexamined is asking a model to read whatever an attacker
got indexed. Contradicting passages are kept and labelled rather than dropped — a passage
that disagrees with the query's premise is usually the most useful thing retrieved, since it
is what lets an answer say the premise is wrong. Group them separately in your prompt.

A passage whose call fails is kept unclassified: a transport error is not evidence about a
document, and silently shrinking the context is worse than passing it through.

## Tool search

`JevToolIndex` implements Spring AI's `ToolIndex` beside the Lucene, regex and vector
implementations, and needs the optional `spring-ai-tool-search-tool` dependency.

It asks two questions in one call: a `Choice` over the indexed tools, whose probabilities
become each `ToolReference`'s relevance score, and a `Noul` asking whether any tool applies
at all. The second is the point. Choice probabilities sum to one, so a ranking always
produces a winner — as do Lucene and embeddings, which have no position meaning "none of
these". Below the applicability threshold the result is empty and the model is told about no
tools rather than a plausible wrong one.

The whole tool set goes into each call, which is right for the tens of tools a session holds
and wrong for thousands; past a few hundred, put a cheap index in front and let Jev choose
among its shortlist.

## Guardrails

`JevGuardrailAdvisor` screens the user message before the model sees it and the reply before
the caller does. Each direction is one call carrying its hazard nouls plus a 0–3 severity
score. A blocked input never reaches the model at all.

Two thresholds give three postures: above `actionThreshold` (0.70) the hazard's action
applies, above `reviewThreshold` (0.35) the turn is flagged for a human rather than decided
by a number, and a severity of 2.0 or more promotes a review to a block — a borderline
probability about something serious is not a borderline problem. Outcomes are `PASS`,
`REVIEW`, `BLOCK` and `SUPPORT`, and when several hazards fire the most serious wins.

Both directions are screened because they fail differently: an output battery is the only
one that notices a jailbreak that actually worked. This is a different job from
`JevSelfRefineAdvisor`, which judges quality and retries to improve it — retrying does not
help here, because an unsafe answer is not a draft.

## Composing decisions

Three small classes from the documented patterns:

- **`JevConfidenceGate`** — a universal floor plus per-action thresholds, returning
  `EXECUTE`, `CONFIRM` or `ESCALATE`. The answer says what; confidence says whether to act
  unattended, and reading a balance need not be as sure as transferring one.
- **`JevConsistency`** — runs the same questions N times, each with a fresh throwaway `uid`
  to keep the draws independent, and reports mean, standard deviation and range per
  question. `unstableAt(threshold)` names the questions whose samples fall on both sides of
  a threshold, which is how you find out which of your thresholds rest on noise.
- **`JevCompositeScore`** — weighted, normalised combination of score dimensions, **for
  ranking only**. Do not gate on it: the argument for Jev over one rubric prompt is that
  independent checks get independent thresholds, and any average hides exactly the defect
  that argument is about. Use it to order a field of candidates that have all already
  passed, which is what the documentation's own résumé example does.

## Build and test

```bash
mvn clean verify
```

The whole suite runs offline. HTTP is exercised through `MockRestServiceServer` bound to
the `RestClient.Builder`, with the request and response fixtures copied verbatim from
`docs.typesafe.ai/api`, compared strictly. Covered: the three documented request shapes,
structured instructions and criteria, string/object/array state, the three response shapes
with integer-keyed maps, unknown answer kinds, every error status, `retry-after-ms`
handling, the retry budget, property binding, the judge's thresholds and feedback wording,
and the advisor's full retry loop.

Do not export `TYPESAFE_BASE_URL` or `TYPESAFE_DEFAULT_MODEL` while working on the SDK.
`TypeSafeClient.builder()` falls back to both, and although every test now pins them
explicitly, the demos and any throwaway `main` you write will pick them up.

### Integration tests

The `*IT.java` classes talk to the real API, so they are **opt-in twice over**: behind the
`integration-tests` profile, and behind `@EnabledIfEnvironmentVariable` on `TYPESAFE_API_KEY`.
That mirrors how the `spring-ai` reactor gates its own, and means an exported key never turns
an ordinary build into a billed one.

```bash
mvn clean verify                        # unit tests only — offline, no key, no cost
mvn clean verify -Pintegration-tests    # adds the ITs; skips them if no key is set
```

| IT | Module | Covers |
| -- | ------ | ------ |
| `TypeSafeLiveApiIT` | `typesafe-java-sdk` | models, the three primitives, object and array state, structured instructions, `jev-preview`, resolved model, usage, 401 and 422 mapping |
| `TypeSafeAutoConfigurationIT` | `spring-ai-starter-typesafe` | a context booted from `spring.ai.typesafe.*` making real calls with the auto-configured bean |
| `JevJudgeIT` | `typesafe-spring-ai` | the judge's premise: that real answers separate on the criteria, and that a defect fails its own criterion alone |

They assert on shape, range and which criteria fail — never on Jev's numbers. It is a model,
and a test that pinned its output would be measuring the weather. A full run is a few seconds
and a fraction of a cent.

`JevJudgeIT` is the one that earns its keep. The offline tests pin the judge's arithmetic
against canned answers; this checks the premise underneath it — that asking "is this
helpful?" and "are these values possible?" separately really does let a fluent, on-topic
answer fail on an impossible temperature alone, which is the whole argument for using Jev
over a second chat model.

## Running against the real API

```bash
export TYPESAFE_API_KEY=...
export ANTHROPIC_API_KEY=...

# Sanity check the key without Java
curl -s https://api.typesafe.ai/v1/models -H "Authorization: Bearer $TYPESAFE_API_KEY"

# Put the modules in the local repository first
mvn install -DskipTests

# The three primitives in one call
mvn -pl examples spring-boot:run \
    -Dspring-boot.run.main-class=org.springaicommunity.typesafe.demo.JevQuickstart

# The self-refine loop
mvn -pl examples spring-boot:run

# The same checks as an assertion, as part of the suite
mvn verify -Pintegration-tests
```

`spring-boot:run` is a goal on one module, so it takes `-pl examples` **without** `-am` —
adding `-am` makes Maven try to run the goal on the parent as well, which fails. Only the
self-refine loop needs `ANTHROPIC_API_KEY`; the quickstart talks to Jev alone.

## Verified against the live API

This SDK was written against the published documentation alone. On **2026-09-19** it was run
against the real service and the three details the documentation never stated were settled:

- **The error body shape** for 4xx is a `detail` envelope with three forms, now modelled by
  `TypeSafeErrorDetail` and described under [Errors](#client).
- **`usage` counts are always present**, with `input_tokens` and `output_tokens`. They stay
  typed as nullable `Integer` — the API is free to omit them and `totalTokens()` already
  treats an absent count as zero — but no live response has omitted one.
- **`GET /v1/models` does not paginate.** The body is a bare `{"models":[…]}` with no cursor,
  so `ListModelsResponse` stays a plain list.

Three further things the documentation does not spell out, all confirmed live:

- **The response `model` is the resolved version, not the alias sent.** Asking for
  `jev-latest` comes back as `jev-1.13.0`, so the id travels with the answer it produced.
- **`release_date` is a full ISO-8601 timestamp**, not the `yyyy-MM-dd` first assumed. It is
  kept as a `String`, so this was a documentation fix rather than a code one.
- **Unknown `criteria` keys are not rejected** — a misspelled key returns 200 and is silently
  ignored. Wire names cannot be verified by probing, which is why the offline fixtures compare
  request bodies strictly.

One thing remains unverified: the **429 body and `retry-after-ms` header** have not been seen
in the wild, since provoking them means deliberately exceeding the published rate limit
(250k tokens/s, 1,200 requests/min). The handling is exercised offline in
`TypeSafeErrorMappingTests`.
