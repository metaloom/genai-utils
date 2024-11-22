package io.metaloom.ai.genai.llm.prompt.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.PromptKey;

public class PromptImpl implements Prompt {

    private final String template;
    private final PromptKey key;
    private final Map<String, String> parameters = new HashMap<>();
    private String text;

    public PromptImpl(String template, PromptKey key) {
        this.template = template;
        this.key = key;
    }

    public PromptImpl(String template) {
        this(template, null);
    }

    @Override
    public String input() {
        String output = template;
        // Poor Mans Template Handling
        for (Entry<String, String> entry : parameters.entrySet()) {
            output = output.replaceAll("\\$\\{" + entry.getKey() + "\\}", entry.getValue());
        }
        if (text != null) {
            return output + text;
        } else {
            return output;
        }
    }

    @Override
    public PromptKey key() {
        return key;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public Map<String, String> parameters() {
        return parameters;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return key() + "\n" + input();
    }

}
