package io.metaloom.ai.genai.llm.prompt.impl;

import java.util.HashMap;
import java.util.Map;

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
        String output = substitute(template, parameters);
        if (text != null) {
            return output + text;
        } else {
            return output;
        }
    }

    /**
     * Replace every {@code ${name}} placeholder of the template in a single left-to-right pass.
     *
     * <p>
     * Substituted values are never scanned again. Replacing one parameter after the other (in HashMap order) did that: a
     * value containing {@code ${other}} - typically untrusted text such as the content to translate - had that
     * placeholder rewritten whenever {@code other} happened to be applied later. Placeholders without a parameter are
     * kept verbatim, as before.
     * </p>
     */
    static String substitute(String template, Map<String, String> parameters) {
        if (template == null || parameters.isEmpty()) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        int pos = 0;
        while (pos < template.length()) {
            int start = template.indexOf("${", pos);
            if (start < 0) {
                break;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                break;
            }
            out.append(template, pos, start);
            String name = template.substring(start + 2, end);
            String value = parameters.get(name);
            if (value != null) {
                out.append(value);
                pos = end + 1;
            } else {
                // Unknown placeholder: keep the "${" and continue right after it, so a nested "${a${b}}" still resolves "${b}".
                out.append("${");
                pos = start + 2;
            }
        }
        out.append(template, pos, template.length());
        return out.toString();
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
    public String text() {
        return text;
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
