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



import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import org.springframework.util.Assert;

/**
 * One atomic thing a {@link JevJudge} checks: a question, plus what counts as passing it.
 *
 * <p>
 * The pass condition depends on the primitive, because the primitives answer differently:
 * a noul is thresholded on its truth value, a score on how far up the rubric it lands, and
 * a choice on whether the selected label is one the caller accepts.
 *
 * @param name the name the answer will carry
 * @param question the question to ask
 * @param minimum the inclusive lower bound for a noul truth value or a score; unused for a
 * choice
 * @param acceptedOptions the labels that count as passing a choice; empty for the other
 * primitives
 * @author Christian Tzolov
 */
public record JevCriterion(String name, Question question, double minimum, Set<String> acceptedOptions) {

	public JevCriterion {
		Assert.hasText(name, "name must not be empty");
		Assert.notNull(question, "question must not be null");
		// An ordered, unmodifiable copy: Set.copyOf would discard declaration order, and
		// the option list is quoted back to the model in the failure feedback.
		acceptedOptions = acceptedOptions == null ? Set.of()
				: Collections.unmodifiableSet(new LinkedHashSet<>(acceptedOptions));
	}

	/**
	 * A noul that passes when its truth value reaches {@code minimum}.
	 * @param name the name the answer will carry
	 * @param noul the question
	 * @param minimum the inclusive lower bound, between {@code 0} and {@code 1}
	 * @return the criterion
	 */
	public static JevCriterion noul(String name, Noul noul, double minimum) {
		Assert.isTrue(minimum >= 0.0d && minimum <= 1.0d, "minimum must be between 0 and 1 for a noul");
		return new JevCriterion(name, noul, minimum, Set.of());
	}

	/**
	 * A score that passes when it reaches {@code minimum}.
	 * @param name the name the answer will carry
	 * @param score the question
	 * @param minimum the inclusive lower bound on the probability-weighted score
	 * @return the criterion
	 */
	public static JevCriterion score(String name, Score score, double minimum) {
		Assert.isTrue(minimum >= 0.0d && minimum <= score.maxLevel(),
				"minimum must be between 0 and the rubric's highest level (" + score.maxLevel() + ")");
		return new JevCriterion(name, score, minimum, Set.of());
	}

	/**
	 * A choice that passes when the selected label is one of {@code acceptedOptions}.
	 * @param name the name the answer will carry
	 * @param choice the question
	 * @param acceptedOptions the labels that count as passing
	 * @return the criterion
	 */
	public static JevCriterion choice(String name, Choice choice, String... acceptedOptions) {
		Assert.notEmpty(acceptedOptions, "acceptedOptions must name at least one option");
		Assert.noNullElements(acceptedOptions, "acceptedOptions must not contain a null option");
		// Arrays.asList, not Set.of: Set.of rejects a repeated label outright and its
		// iteration order is salted per JVM run, which would make the feedback text
		// differ between runs. A duplicate here is harmless and simply collapses.
		Set<String> accepted = new LinkedHashSet<>(Arrays.asList(acceptedOptions));
		accepted.forEach(option -> Assert.isTrue(choice.criteria().containsKey(option),
				"accepted option '" + option + "' is not one of the choice's options " + choice.criteria().keySet()));
		return new JevCriterion(name, choice, 0.0d, accepted);
	}

}
