package io.metaloom.ai.genai.llm;

import io.metaloom.ai.genai.utils.TextUtils;
import io.vertx.core.json.JsonObject;

/**
 * Response of a plain (non tool-calling) LLM call: the generated text plus the token accounting the
 * server reported for it.
 *
 * @param content text the model generated
 * @param usage   token accounting of the call, {@link TokenUsage#NONE} when the server did not
 *                report any
 */
public record LLMResponse(String content, TokenUsage usage) {

	public LLMResponse {
		if (usage == null) {
			usage = TokenUsage.NONE;
		}
	}

	public LLMResponse(String content) {
		this(content, TokenUsage.NONE);
	}

	/**
	 * Parse the response content as JSON. Surrounding prose and markdown fences the model may have
	 * added are stripped.
	 *
	 * @return
	 */
	public JsonObject asJson() {
		return new JsonObject(TextUtils.extractJson(content));
	}

	@Override
	public String toString() {
		return "LLMResponse[content=" + (content != null ? content.substring(0, Math.min(content.length(), 50)) : "null")
			+ ", usage=" + usage + "]";
	}
}
