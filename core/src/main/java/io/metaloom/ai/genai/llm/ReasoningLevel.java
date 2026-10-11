package io.metaloom.ai.genai.llm;

import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How much reasoning ("thinking") the LLM should spend on a call.
 *
 * <p>
 * A level maps onto the two knobs an OpenAI-compatible server understands:
 * <ul>
 * <li>{@code enable_thinking} - the boolean the Qwen/DeepSeek style chat templates read to turn the
 * reasoning block on or off. It travels in {@code chat_template_kwargs}, which is how llama.cpp and
 * vLLM pass values into the chat template.</li>
 * <li>{@code reasoning_effort} - the effort budget. OpenAI defines it as a top level request field;
 * llama.cpp and vLLM additionally accept it as a chat template argument. It is sent in both places
 * so that either kind of server picks it up.</li>
 * </ul>
 *
 * <p>
 * Not every model knows every effort value. OpenAI's reasoning models accept {@code minimal},
 * {@code low}, {@code medium}, {@code high} and (from gpt-5.1-codex-max on) {@code xhigh}, while the
 * Qwen3.8 chat template only accepts {@code low}, {@code medium} and {@code xhigh} and fails the
 * request for anything else. Pick a level the target model supports.
 */
public enum ReasoningLevel {

	/**
	 * Send no reasoning preference at all and let the server or the chat template decide. This is
	 * the default of a fresh {@link LLMContext}.
	 */
	UNSET(null, null),

	/**
	 * Turn reasoning off.
	 */
	OFF(Boolean.FALSE, "none"),

	/**
	 * Turn reasoning on without stating an effort - the model uses its own default budget.
	 */
	ON(Boolean.TRUE, null),

	/**
	 * The smallest amount of reasoning the model offers. Not supported by every model.
	 */
	MINIMAL(Boolean.TRUE, "minimal"),

	/**
	 * Brief, focused reasoning.
	 */
	LOW(Boolean.TRUE, "low"),

	/**
	 * The usual trade-off between latency and answer quality.
	 */
	MEDIUM(Boolean.TRUE, "medium"),

	/**
	 * Reason thoroughly. Not supported by every model.
	 */
	HIGH(Boolean.TRUE, "high"),

	/**
	 * Reason as thoroughly as the model can. Not supported by every model.
	 */
	XHIGH(Boolean.TRUE, "xhigh");

	private static final Logger logger = LoggerFactory.getLogger(ReasoningLevel.class);

	/**
	 * System property that sets the level used by contexts which do not choose one themselves.
	 */
	public static final String LEVEL_PROPERTY_KEY = "genai.reasoning.level";

	/**
	 * Environment variable that sets the level used by contexts which do not choose one themselves.
	 * The system property wins when both are set.
	 */
	public static final String LEVEL_ENV_KEY = "GENAI_REASONING_LEVEL";

	private final Boolean thinking;

	private final String effort;

	ReasoningLevel(Boolean thinking, String effort) {
		this.thinking = thinking;
		this.effort = effort;
	}

	/**
	 * Value for the {@code enable_thinking} chat template argument.
	 *
	 * @return {@code null} when the level states no preference
	 */
	public Boolean thinking() {
		return thinking;
	}

	/**
	 * Value for the {@code reasoning_effort} request field.
	 *
	 * @return {@code null} when the level states no effort
	 */
	public String effort() {
		return effort;
	}

	/**
	 * Whether the level asks the model to reason.
	 *
	 * @return true for every level but {@link #OFF} and {@link #UNSET}
	 */
	public boolean isThinkEnabled() {
		return Boolean.TRUE.equals(thinking);
	}

	/**
	 * Whether the level carries any preference to send to the server.
	 *
	 * @return false only for {@link #UNSET}
	 */
	public boolean isSet() {
		return this != UNSET;
	}

	/**
	 * Parse a level from its name or from the {@code reasoning_effort} value it maps to. Accepted
	 * spellings are case insensitive and include {@code none}, {@code off} and {@code false} for
	 * {@link #OFF} as well as {@code on} and {@code true} for {@link #ON}.
	 *
	 * @param value
	 * @return the parsed level, or {@link #UNSET} when the value is null or blank
	 * @throws IllegalArgumentException when the value names no known level
	 */
	public static ReasoningLevel parse(String value) {
		if (value == null || value.isBlank()) {
			return UNSET;
		}
		String key = value.trim().toLowerCase(Locale.ROOT);
		switch (key) {
		case "none":
		case "off":
		case "false":
		case "disabled":
			return OFF;
		case "on":
		case "true":
		case "enabled":
			return ON;
		default:
			for (ReasoningLevel level : values()) {
				if (level.name().toLowerCase(Locale.ROOT).equals(key)) {
					return level;
				}
			}
			throw new IllegalArgumentException("Unknown reasoning level '" + value + "'. Supported values are "
				+ Arrays.toString(values()) + " (or none/off/on).");
		}
	}

	/**
	 * Same as {@link #parse(String)} but answers with the fallback instead of failing on an unknown
	 * value.
	 *
	 * @param value
	 * @param fallback
	 * @return
	 */
	public static ReasoningLevel parse(String value, ReasoningLevel fallback) {
		try {
			return parse(value);
		} catch (IllegalArgumentException e) {
			logger.warn("Ignoring unparsable reasoning level '{}'. Falling back to {}.", value, fallback);
			return fallback;
		}
	}

	/**
	 * Level applied to a new {@link LLMContext}. It is read from the {@value #LEVEL_PROPERTY_KEY}
	 * system property, then from the {@value #LEVEL_ENV_KEY} environment variable, so that a
	 * deployment can dial the reasoning of every call up or down without touching the call sites.
	 *
	 * @return the configured level, or {@link #UNSET} when neither is set
	 */
	public static ReasoningLevel defaultLevel() {
		String value = System.getProperty(LEVEL_PROPERTY_KEY);
		if (value == null || value.isBlank()) {
			value = System.getenv(LEVEL_ENV_KEY);
		}
		return parse(value, UNSET);
	}
}
