package io.metaloom.ai.genai.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A tool's JSON Schema must survive the trip into the OpenAI SDK unchanged.
 *
 * <p>
 * The SDK serialises with its own Jackson mapper, which has no Vert.x module registered. A
 * {@link JsonObject} handed to it is written as an <b>array of its entries</b>, because that is
 * what {@code Iterable<Map.Entry<String, Object>>} means to Jackson — so
 * {@code {"properties":{"query":{"type":"string"}}}} left as
 * {@code {"properties":[{"query":[{"type":"string"}]}]}}.
 * </p>
 *
 * <p>
 * Nothing on the way out rejects that, and a server that only renders the schema into a chat
 * template answers normally with the wreckage in its prompt — which is how it went unnoticed. A
 * server that compiles the schema into a grammar refuses the whole request: llama.cpp answers
 * {@code 400 ... JSON schema error at #: properties must be an object}. So the assertion here is
 * deliberately made with a <em>plain</em> mapper, the same kind the SDK uses.
 * </p>
 */
public class ToolSchemaSerializationTest {

	private static final ObjectMapper PLAIN = new ObjectMapper();

	@Test
	@DisplayName("A nested schema serialises as objects, not as arrays of entries")
	public void testNestedObjectsSurvive() throws Exception {
		JsonObject schema = new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("query", new JsonObject().put("type", "string").put("description", "Search term")))
			.put("required", new JsonArray().add("query"));

		String json = PLAIN.writeValueAsString(OpenAILLMProvider.plain(schema));

		assertEquals("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
			+ "\"description\":\"Search term\"}},\"required\":[\"query\"]}", json);
	}

	@Test
	@DisplayName("Arrays of schemas keep their shape too")
	public void testArraysOfSchemasSurvive() throws Exception {
		// map_over's item schema: an array whose items are a oneOf of two schemas. Three levels of
		// nesting inside a JsonArray, which the old code corrupted at every one of them.
		JsonObject schema = new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("items", new JsonObject()
					.put("type", "array")
					.put("items", new JsonObject()
						.put("oneOf", new JsonArray()
							.add(new JsonObject().put("type", "string"))
							.add(new JsonObject()
								.put("type", "object")
								.put("properties", new JsonObject()
									.put("text", new JsonObject().put("type", "string"))))))));

		String json = PLAIN.writeValueAsString(OpenAILLMProvider.plain(schema));

		assertEquals("{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\",\"items\":"
			+ "{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"object\",\"properties\":"
			+ "{\"text\":{\"type\":\"string\"}}}]}}}}", json);
	}

	@Test
	@DisplayName("Nothing Vert.x is left in the converted tree")
	public void testNoVertxTypesRemain() {
		JsonObject schema = new JsonObject()
			.put("properties", new JsonObject().put("a", new JsonObject().put("type", "string")))
			.put("anyOf", new JsonArray().add(new JsonObject().put("type", "null")));

		assertNoVertx(OpenAILLMProvider.plain(schema));
	}

	@Test
	@DisplayName("A parameterless tool keeps an empty object, not an empty array")
	public void testEmptyPropertiesStaysAnObject() throws Exception {
		// The degenerate case a schema-compiling server is fussiest about: `properties` present
		// and empty. An empty JsonObject is an empty Iterable, so it used to serialise as `[]`.
		JsonObject schema = new JsonObject().put("type", "object").put("properties", new JsonObject());

		assertEquals("{\"type\":\"object\",\"properties\":{}}",
			PLAIN.writeValueAsString(OpenAILLMProvider.plain(schema)));
	}

	@Test
	@DisplayName("Scalars and nulls pass through untouched")
	public void testScalarsPassThrough() throws Exception {
		JsonObject schema = new JsonObject()
			.put("maxItems", 25)
			.put("multipleOf", 1.5d)
			.put("deprecated", false)
			.putNull("default");

		assertEquals("{\"maxItems\":25,\"multipleOf\":1.5,\"deprecated\":false,\"default\":null}",
			PLAIN.writeValueAsString(OpenAILLMProvider.plain(schema)));
	}

	private static void assertNoVertx(Object value) {
		if (value instanceof Map<?, ?> map) {
			map.values().forEach(ToolSchemaSerializationTest::assertNoVertx);
			return;
		}
		if (value instanceof List<?> list) {
			list.forEach(ToolSchemaSerializationTest::assertNoVertx);
			return;
		}
		if (value != null) {
			assertInstanceOf(Comparable.class, value, "a leaf should be a scalar, got " + value.getClass());
		}
	}
}
