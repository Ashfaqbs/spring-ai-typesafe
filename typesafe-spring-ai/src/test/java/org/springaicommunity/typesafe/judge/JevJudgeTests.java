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

package org.springaicommunity.typesafe.judge;




import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.MockTypeSafeServer;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

/**
 * Threshold and feedback-synthesis behaviour of the judge. Jev returns numbers, never
 * prose, so the feedback these tests assert on is built by the SDK out of the rubric the
 * caller supplied.
 *
 * @author Christian Tzolov
 */
class JevJudgeTests {

	private static final Score HELPFULNESS = Score.builder()
		.instructions("How well does `assistant_answer` address `user_question`?")
		.level("Terrible: irrelevant or off-topic")
		.level("Mostly unhelpful: misses the main point")
		.level("Mostly helpful: minor gaps remain")
		.level("Excellent: fully and correctly addressed")
		.build();

	private static final Noul PLAUSIBLE = Noul.builder()
		.instructions("Are the values in `assistant_answer` physically plausible?")
		.whenTrue("Every value is physically possible")
		.whenFalse("Contains an impossible or absurd value")
		.build();

	private final MockTypeSafeServer mock = MockTypeSafeServer.create();

	@Test
	void passesWhenEveryCriterionIsMet() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":3.2,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"0":0.01,"1":0.04,"2":0.15,"3":0.80},"confidence":0.86},
				  "is_plausible":{"type":"noul","noul":0.95}
				},"usage":{"input_tokens":120,"output_tokens":20}}""");

		JevVerdict verdict = judge().judge("What is the weather in Paris?", "It is 15 degrees Celsius in Paris.");

		assertThat(verdict.passed()).isTrue();
		assertThat(verdict.feedback()).isEmpty();
		assertThat(verdict.failures()).isEmpty();
		assertThat(verdict.summary()).isEqualTo("passed=true [helpfulness=PASSED, is_plausible=PASSED]");
		assertThat(verdict.response().requestId()).isEqualTo("req_0123456789");
	}

	@Test
	void failsANoulAndQuotesItsFalseSideAsTheDefect() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":3.0,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"3":1.0},"confidence":0.9},
				  "is_plausible":{"type":"noul","noul":0.04}
				},"usage":{}}""");

		JevVerdict verdict = judge().judge("What is the weather in Paris?", "It is -255 degrees Celsius in Paris.");

		assertThat(verdict.passed()).isFalse();
		assertThat(verdict.failures()).singleElement()
			.satisfies(finding -> assertThat(finding.name()).isEqualTo("is_plausible"));
		assertThat(verdict.feedback())
			.isEqualTo("- is_plausible: Contains an impossible or absurd value (scored 0.04, needs at least 0.70)");
	}

	@Test
	void failsAScoreAndNamesBothTheLevelReachedAndTheLevelRequired() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":1.2,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"0":0.10,"1":0.65,"2":0.20,"3":0.05},"confidence":0.71},
				  "is_plausible":{"type":"noul","noul":0.99}
				},"usage":{}}""");

		JevVerdict verdict = judge().judge("What is the weather in Paris?", "Weather is hard to predict.");

		assertThat(verdict.passed()).isFalse();
		assertThat(verdict.feedback()).isEqualTo(
				"- helpfulness: rated \"Mostly unhelpful: misses the main point\" (1.20), "
						+ "needs to reach 2.00 which is \"Mostly helpful: minor gaps remain\"");
	}

	@Test
	void reportsBothDefectsWhenBothCriteriaFail() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":0.4,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"0":0.70,"1":0.20,"2":0.07,"3":0.03},"confidence":0.68},
				  "is_plausible":{"type":"noul","noul":0.10}
				},"usage":{}}""");

		JevVerdict verdict = judge().judge("What is the weather in Paris?", "It is -255 degrees in Narnia.");

		assertThat(verdict.failures()).hasSize(2);
		assertThat(verdict.feedback()).contains("- helpfulness: rated \"Terrible: irrelevant or off-topic\"")
			.contains("- is_plausible: Contains an impossible or absurd value");
	}

	@Test
	void treatsAFlatDistributionAsUndecidedRatherThanFailed() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":1.5,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"0":0.25,"1":0.25,"2":0.25,"3":0.25},"confidence":0.20},
				  "is_plausible":{"type":"noul","noul":0.99}
				},"usage":{}}""");

		JevVerdict verdict = judge().judge("What is the weather in Paris?", "Possibly mild.");

		assertThat(verdict.passed()).isTrue();
		assertThat(verdict.failures()).isEmpty();
		assertThat(verdict.inconclusive()).singleElement()
			.satisfies(finding -> assertThat(finding.name()).isEqualTo("helpfulness"));
	}

	@Test
	void canBeConfiguredToRejectAnUndecidedCriterion() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{
				  "helpfulness":{"type":"score","score":1.5,
				    "legend":{"0":"Terrible","1":"Mostly unhelpful","2":"Mostly helpful","3":"Excellent"},
				    "probabilities":{"0":0.25,"1":0.25,"2":0.25,"3":0.25},"confidence":0.20}
				},"usage":{}}""");

		JevJudge strict = JevJudge.builder(this.mock.client())
			.score("helpfulness", HELPFULNESS, 2.0d)
			.minConfidence(0.5d)
			.failOnInconclusive(true)
			.build();

		JevVerdict verdict = strict.judge("What is the weather in Paris?", "Possibly mild.");

		assertThat(verdict.passed()).isFalse();
		assertThat(verdict.feedback()).contains("the rubric levels were not well separated")
			.contains("confidence 0.20");
	}

	@Test
	void nouIsNotGatedOnConfidenceBecauseItCarriesNone() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{"is_plausible":{"type":"noul","noul":0.99}},"usage":{}}""");

		JevJudge strict = JevJudge.builder(this.mock.client())
			.noul("is_plausible", PLAUSIBLE, 0.7d)
			.minConfidence(0.99d)
			.failOnInconclusive(true)
			.build();

		assertThat(strict.judge("q", "a").passed()).isTrue();
	}

	@Test
	void judgesAChoiceAgainstTheAcceptedOptions() {
		respondWith("""
				{"model":"jev-1.13.0","answers":{"tone":{"type":"choice","choice":"dismissive",
				  "probabilities":{"helpful":0.12,"neutral":0.20,"dismissive":0.68},"confidence":0.74}},"usage":{}}""");

		JevJudge toneJudge = JevJudge.builder(this.mock.client())
			.choice("tone",
					Choice.builder()
						.instructions("What tone does `assistant_answer` take?")
						.option("helpful", "Answers and offers next steps")
						.option("neutral", "Answers plainly")
						.option("dismissive", "Brushes the question off")
						.build(),
					"helpful", "neutral")
			.build();

		JevVerdict verdict = toneJudge.judge("q", "Figure it out yourself.");

		assertThat(verdict.passed()).isFalse();
		assertThat(verdict.feedback()).contains("tone: classified as \"dismissive\" (confidence 0.74)")
			.contains("acceptable values are");
	}

	@Test
	void rejectsAnAcceptedOptionThatIsNotOneOfTheChoiceOptions() {
		Choice tone = Choice.of("What tone?", "helpful", "neutral");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> JevCriterion.choice("tone", tone, "enthusiastic"))
			.withMessageContaining("is not one of the choice's options");
	}

	@Test
	void rejectsAScoreThresholdAboveTheRubric() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevCriterion.score("helpfulness", HELPFULNESS, 9.0d))
			.withMessageContaining("highest level (3)");
	}

	@Test
	void rejectsDuplicateCriterionNames() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> JevJudge.builder(this.mock.client())
				.noul("is_plausible", PLAUSIBLE, 0.7d)
				.noul("is_plausible", PLAUSIBLE, 0.8d))
			.withMessageContaining("already declared");
	}

	@Test
	void describesAStructuredCriterionAsJsonRatherThanJavaToString() {
		// This is the path that reaches a model: describeFalseSide -> JevFinding.detail ->
		// feedback() -> appended to the retry prompt. A criterion described with a POJO or a
		// map used to render as Java syntax, describing the value differently from how it
		// was sent.
		this.mock.server()
			.expect(requestTo(MockTypeSafeServer.SYSTEM_ONE_URL))
			.andRespond(MockTypeSafeServer.jsonResponse(
					"{\"model\":\"jev-1.13.0\",\"answers\":{\"is_plausible\":{\"type\":\"noul\",\"noul\":0.04}},"
							+ "\"usage\":{}}"));

		JevJudge judge = JevJudge.builder(this.mock.client())
			.noul("is_plausible", Noul.builder()
				.instructions("Are the values plausible?")
				.whenFalse(Map.of("what", "An impossible value", "example", "-255 C"))
				.build(), 0.7d)
			.build();

		// Map.of has a randomised iteration order, so assert on the rendering of a field
		// rather than on which one Jackson happens to write first.
		assertThat(judge.judge("q", "a").feedback()).contains("\"what\":\"An impossible value\"")
			.doesNotContain("what=An impossible value");
	}

	@Test
	void rejectsAJudgeWithoutCriteria() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevJudge.builder(this.mock.client()).build())
			.withMessageContaining("at least one criterion");
	}

	@Test
	void acceptsARepeatedOptionAndKeepsTheOptionsInDeclarationOrder() {
		// Set.of would throw on the duplicate, and its iteration order is salted per JVM
		// run, which would make the feedback text differ between runs.
		Choice tone = Choice.of("What tone?", "helpful", "neutral", "dismissive");

		JevCriterion criterion = JevCriterion.choice("tone", tone, "helpful", "neutral", "helpful");

		assertThat(criterion.acceptedOptions()).containsExactly("helpful", "neutral");
	}

	@Test
	void rejectsANullAcceptedOption() {
		Choice tone = Choice.of("What tone?", "helpful", "neutral");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> JevCriterion.choice("tone", tone, "helpful", null))
			.withMessageContaining("must not contain a null option");
	}

	@Test
	void reportsAMissingAnswerAsUndecidedRatherThanFailingTheWholeCall() {
		// A partial response should degrade that one criterion, the same way an
		// unrecognised answer kind does, not abort the caller's chat call.
		this.mock.server()
			.expect(requestTo(MockTypeSafeServer.SYSTEM_ONE_URL))
			.andRespond(MockTypeSafeServer.jsonResponse("""
					{"model":"jev-1.13.0","answers":{
					  "helpfulness":{"type":"score","score":3.0,
					    "legend":{"0":"Terrible","1":"Unhelpful","2":"Helpful","3":"Excellent"},
					    "probabilities":{"3":1.0},"confidence":0.9}
					},"usage":{}}"""));

		JevVerdict verdict = judge().judge("q", "a");

		assertThat(verdict.passed()).isTrue();
		assertThat(verdict.findings()).extracting(JevFinding::name).contains("is_plausible");
		assertThat(verdict.findings())
			.filteredOn(finding -> finding.name().equals("is_plausible"))
			.singleElement()
			.satisfies(finding -> {
				assertThat(finding.outcome()).isEqualTo(JevFinding.Outcome.INCONCLUSIVE);
				assertThat(finding.detail()).contains("returned no answer");
			});
	}

	private JevJudge judge() {
		return JevJudge.builder(this.mock.client())
			.score("helpfulness", HELPFULNESS, 2.0d)
			.noul("is_plausible", PLAUSIBLE, 0.7d)
			.minConfidence(0.5d)
			.build();
	}

	private void respondWith(String body) {
		this.mock.server().expect(requestTo(MockTypeSafeServer.SYSTEM_ONE_URL)).andRespond(MockTypeSafeServer.jsonResponse(body));
	}

}
