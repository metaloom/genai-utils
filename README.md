# Gen AI Utils

This project contains utilities to be used for generative ai.

## Maven

```xml
<dependency>
  <groupId>io.metaloom.utils</groupId>
  <artifactId>genai-utils</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```


## Reasoning level

How much a reasoning model should think is configured per call on the `LLMContext`:

```java
LLMContext ctx = LLMContext.ctx(prompt, model)
    .reasoning(ReasoningLevel.LOW);   // or ctx.setReasoningLevel(...)
```

| Level | `enable_thinking` | `reasoning_effort` |
| --- | --- | --- |
| `UNSET` (default) | – | – |
| `OFF` | `false` | `none` |
| `ON` (what `enableThink()` sets) | `true` | – |
| `MINIMAL` / `LOW` / `MEDIUM` / `HIGH` / `XHIGH` | `true` | the level's name in lower case |

`UNSET` sends nothing at all and leaves the server at its own default. Every other level travels in
`chat_template_kwargs`, which is how llama.cpp and vLLM pass values into the chat template; the
effort is additionally sent as the top level `reasoning_effort` field that OpenAI itself reads.

Not every model knows every effort. OpenAI's reasoning models accept `minimal`, `low`, `medium`,
`high` and (from gpt-5.1-codex-max on) `xhigh`, while the Qwen3.8 chat template only accepts `low`,
`medium` and `xhigh` and fails the request for anything else - so pick a level the target model
supports.

To dial the reasoning of a whole deployment up or down without touching the call sites, set the
default that a fresh context starts with:

```bash
-Dgenai.reasoning.level=low     # or: GENAI_REASONING_LEVEL=low
```

The system property wins over the environment variable; an unparsable value is logged and ignored.

## Token usage

Every LLM call reports the token accounting the server sent in the `usage` object of the
OpenAI-compatible response (vLLM, llama.cpp, Ollama `/v1`, OpenAI) as a `TokenUsage`:

```java
LLMProvider provider = new OpenAILLMProvider();

// Plain generation
LLMResponse response = provider.generateResponse(ctx);
System.out.println(response.content() + " took " + response.usage().totalTokens() + " tokens");

// Tool calling
ToolCallResponse toolResponse = provider.generateWithTools(ctx);
TokenUsage usage = toolResponse.usage();

// Streaming - the last event carries the accounting of the whole call
provider.generateStreamWithTools(ctx)
    .ofType(StreamEvent.Completed.class)
    .subscribe(completed -> System.out.println(completed.usage()));
```

`TokenUsage` exposes the prompt, completion and total counts plus the reasoning tokens
(`completion_tokens_details.reasoning_tokens`) and the cached prompt tokens
(`prompt_tokens_details.cached_tokens`). Fields a server does not report are `0`; use
`isReported()` to tell "no accounting at all" apart from a real count, and `plus(...)` to add up the
spend of a multi-turn agent loop.

Streamed calls ask for the accounting via `stream_options.include_usage`, which makes the server
append one final, choice-less chunk holding it. `generateStream(ctx)` surfaces that as a last,
text-less `Chunk` whose `usage()` is filled in. Turn the request off with
`setStreamUsageEnabled(false)` on the context for servers that reject the field.


## Release Process

```bash
# Update maven version to next release
mvn versions:set -DgenerateBackupPoms=false

# Now run tests locally or via GitHub actions
mvn clean package

# Deploy to maven central and auto-close staging repo. 
# Adding the property will trigger the profiles in the parent pom to include gpg,javadoc...
mvn clean deploy -Drelease
```
