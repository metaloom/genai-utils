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
