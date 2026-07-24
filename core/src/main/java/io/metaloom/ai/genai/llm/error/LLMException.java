package io.metaloom.ai.genai.llm.error;

public class LLMException extends Exception {

	private static final long serialVersionUID = 6143461659140566923L;

	public LLMException(String msg, Exception e) {
		super(msg, e);
	}

	public LLMException(String msg) {
		super(msg);
	}

}
