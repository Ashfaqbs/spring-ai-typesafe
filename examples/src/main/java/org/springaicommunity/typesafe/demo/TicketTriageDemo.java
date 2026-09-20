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

package org.springaicommunity.typesafe.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.TypeSafeConstants;
import org.springaicommunity.typesafe.judge.JevConfidenceGate;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.SystemOneResponse;

/**
 * Speculative fan-out and confidence-gated routing, on the support tickets from the
 * TypeSafe documentation.
 *
 * <p>
 * Where {@code JevQuickstart} asks the three questions it always needs, this asks five,
 * two of which are usually irrelevant. {@code bug_severity} only matters for a technical
 * report and {@code refund_requested} only for a billing one, but which of those a ticket
 * is is exactly what the call is working out. Asking them anyway costs nothing measurable,
 * because every question in a call is answered in parallel against a state the service
 * reads once — so the alternative, a second round trip once the department is known, is
 * strictly slower for the same answers.
 *
 * <p>
 * The routing then uses two axes rather than one. The department says where the ticket
 * goes; the confidence says whether it goes there by itself. Auto-closing a ticket costs
 * more when it is wrong than routing one does, so it demands more confidence — that
 * asymmetry lives in {@link JevConfidenceGate} rather than in scattered {@code if}
 * statements.
 *
 * <p>
 * Run it with {@code TYPESAFE_API_KEY} set; no chat model and no Spring context involved.
 *
 * @author Christian Tzolov
 */
public final class TicketTriageDemo {

	private static final List<String> TICKETS = List.of(
			"Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. "
					+ "I'm losing sales. Please help ASAP.",
			"You charged me twice for March. Please refund one of them.",
			"Just wanted to say the new dashboard is lovely. No issue, keep it up!");

	private TicketTriageDemo() {
	}

	public static void main(String[] args) {
		TypeSafeClient client = TypeSafeClient.builder()
			.apiKey(System.getenv(TypeSafeConstants.API_KEY_ENV))
			.build();

		// Routing a ticket is reversible and cheap. Closing one without a human reading it
		// is neither, so it has to be surer of itself.
		JevConfidenceGate gate = JevConfidenceGate.builder()
			.floor(0.60d)
			.require("auto_close", 0.90d)
			.build();

		for (String ticket : TICKETS) {
			System.out.println("─".repeat(78));
			System.out.println(ticket);
			System.out.println();

			SystemOneResponse response = client.systemOne(ticket, questions());

			String department = response.choiceValue("department");
			double confidence = response.choice("department").confidence();

			System.out.printf("  urgent       : %.2f%n", response.noulValue("is_urgent"));
			System.out.printf("  department   : %s (confidence %.2f)%n", department, confidence);
			System.out.printf("  frustration  : %.2f -> %s%n", response.scoreValue("frustration"),
					response.score("frustration").nearestLabel());

			// The speculative half. Both answers came back; only one of them is meaningful,
			// and which one is decided here rather than by a second call.
			switch (department) {
				case "technical" -> System.out.printf("  bug severity : %.2f -> %s%n",
						response.scoreValue("bug_severity"), response.score("bug_severity").nearestLabel());
				case "billing" -> System.out.printf("  refund asked : %.2f%n",
						response.noulValue("refund_requested"));
				default -> System.out.println("  (neither speculative question applies to this ticket)");
			}

			// An untroubled ticket is a candidate for closing rather than routing, which is
			// the higher-stakes action and therefore the stricter gate.
			boolean untroubled = response.noulValue("is_urgent") < 0.2d && response.scoreValue("frustration") < 0.5d;
			String action = untroubled ? "auto_close" : "route_to_" + department;

			System.out.printf("  => %s: %s%n", action, switch (gate.decide(action, confidence)) {
				case EXECUTE -> "done automatically";
				case CONFIRM -> "proposed, waiting for a human to confirm";
				case ESCALATE -> "too uncertain to act on, sent to a person";
			});
		}
		System.out.println("─".repeat(78));
	}

	private static Map<String, Question> questions() {
		Map<String, Question> questions = new LinkedHashMap<>();
		questions.put("is_urgent", Noul.builder()
			.instructions("Does this message convey urgency or time-sensitivity?")
			.whenTrue("Explicitly time-sensitive, or describes an ongoing loss")
			.whenFalse("No urgency expressed")
			.build());
		questions.put("department", Choice.builder()
			.instructions("Which team should handle this?")
			.option("billing", "Payments, invoicing, refunds, subscriptions")
			.option("technical", "Bugs, outages, integrations that do not work")
			.option("sales", "Pricing, upgrades, new accounts")
			.option("feedback", "Praise, suggestions, anything needing no action")
			.build());
		questions.put("frustration",
				Score.of("How frustrated does the customer appear?", "Calm, just stating facts",
						"Frustrated but civil", "Very angry, strong language"));

		// Speculative: asked of every ticket, read for some.
		questions.put("bug_severity",
				Score.of("If this reports a defect, how severe is it?", "Not a defect report",
						"Cosmetic or minor inconvenience", "A feature is unusable", "Data loss, or a total outage"));
		questions.put("refund_requested", Noul.builder()
			.instructions("Is the customer asking for money back?")
			.whenTrue("Asks for a refund, reversal or credit")
			.whenFalse("No request for money back")
			.build());
		return questions;
	}

}
