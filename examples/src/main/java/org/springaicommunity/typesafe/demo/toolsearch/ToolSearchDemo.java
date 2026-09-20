/*
 * Copyright 2026 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springaicommunity.typesafe.demo.toolsearch;

import java.util.List;

import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.TypeSafeConstants;
import org.springaicommunity.typesafe.toolsearch.JevToolIndex;

import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;

/**
 * Choosing a tool by asking, next to choosing one by matching text.
 *
 * <p>
 * Both indexes implement Spring AI's {@link ToolIndex}, so they are interchangeable behind
 * the tool-search advisor. The queries below are ordered to make the difference visible:
 * the first two are worded nothing like the tool they need, and the last one needs no tool
 * at all.
 *
 * <p>
 * That last case is the one to watch. A lexical or vector index ranks whatever it holds and
 * returns a best match, because ranking is all it can do — there is no position in the
 * index that means "none of these". {@link JevToolIndex} asks a second, independent
 * question about whether any tool applies, and returns nothing when the answer is no. The
 * difference between offering the model a plausible wrong tool and offering it none is the
 * difference between a confidently wrong action and a straight answer.
 *
 * <p>
 * Run it with {@code TYPESAFE_API_KEY} set. The regex baseline needs nothing.
 *
 * @author Christian Tzolov
 */
public final class ToolSearchDemo {

	private static final String SESSION = "demo-session";

	private static final List<ToolReference> TOOLS = List.of(
			tool("currentWeather", "Returns the current temperature and conditions for a named place"),
			tool("sendEmail", "Sends an email message to one or more recipients"),
			tool("createInvoice", "Creates and issues an invoice for a customer"),
			tool("searchOrders", "Finds past orders belonging to a customer"),
			tool("bookMeeting", "Schedules a meeting in the calendar with a set of attendees"));

	private static final List<String> QUERIES = List.of(
			// Shares vocabulary with the tool's own description, which is the case lexical
			// matching is built for.
			"create an invoice for Acme Corp",
			// Say the same thing in the words a person would actually use.
			"bill Acme Corp for last month", "is it raining in Amsterdam right now",
			"drop a line to the finance team about the overdue payment",
			// Needs no tool at all.
			"what is the capital of Peru");

	private ToolSearchDemo() {
	}

	public static void main(String[] args) throws java.io.IOException {
		TypeSafeClient client = TypeSafeClient.builder()
			.apiKey(System.getenv(TypeSafeConstants.API_KEY_ENV))
			.build();

		JevToolIndex jev = JevToolIndex.builder(client).build();
		jev.indexTools(SESSION, TOOLS);

		try (RegexToolIndex regex = new RegexToolIndex()) {
			regex.indexTools(SESSION, TOOLS);

			System.out.printf("%-52s  %-26s  %s%n", "QUERY", "regex (baseline)", "jev");
			System.out.println("─".repeat(110));
			for (String query : QUERIES) {
				System.out.printf("%-52s  %-26s  %s%n", truncate(query), top(regex, query), top(jev, query));
			}
			System.out.println("─".repeat(110));
		}

		System.out.println();
		System.out.println("The baseline matches on words. It finds the invoice tool when the query happens");
		System.out.println("to contain 'invoice', and finds nothing once the same request is phrased the way");
		System.out.println("someone would actually say it — the meaning is unchanged, the vocabulary is not.");
		System.out.println();
		System.out.println("The last query is the one that needs the second question. Ranking alone always");
		System.out.println("produces a winner, because probabilities over the tools must sum to one, so a");
		System.out.println("choice on its own would hand back a tool here too. Asking separately whether any");
		System.out.println("tool applies is what lets the answer be none, and the model is then told about");
		System.out.println("no tools instead of a plausible wrong one.");
	}

	private static String top(ToolIndex index, String query) {
		ToolSearchResponse response = index.search(new ToolSearchRequest(SESSION, query, 1, null));
		if (response.toolReferences().isEmpty()) {
			return "(no tool applies)";
		}
		ToolReference best = response.toolReferences().get(0);
		Double score = best.relevanceScore();
		return score == null ? best.toolName() : "%s %.2f".formatted(best.toolName(), score);
	}

	private static ToolReference tool(String name, String summary) {
		return ToolReference.builder().toolName(name).summary(summary).build();
	}

	private static String truncate(String text) {
		return text.length() <= 50 ? text : text.substring(0, 47) + "...";
	}

}
