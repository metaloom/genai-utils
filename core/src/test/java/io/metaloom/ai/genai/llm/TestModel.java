package io.metaloom.ai.genai.llm;

public class TestModel implements LargeLanguageModel {

	private String id;
	private String url;
	private long ctxWindowSize;

	public TestModel(String id, String url, long ctxWindowSize) {
		this.id = id;
		this.url = url;
		this.ctxWindowSize = ctxWindowSize;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String url() {
		return url;
	}

	@Override
	public long contextWindow() {
		return ctxWindowSize;
	}

	public static TestModel mistral24bQ8(String url) {
		return new TestModel("mistralai/Mistral-Small-24B-Instruct-2501", url, 128_000);
	}

	/**
	 * A reasoning model whose chat template honours {@code reasoning_effort} (low, medium, xhigh).
	 */
	public static TestModel qwen3(String url) {
		return new TestModel("orcarouter/Qwen3.8-27B-Uncensored-GGUF", url, 37_120);
	}

}
