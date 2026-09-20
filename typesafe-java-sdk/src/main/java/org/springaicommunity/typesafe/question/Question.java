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

package org.springaicommunity.typesafe.question;



import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.typesafe.JsonContent;

/**
 * A typed question evaluated against a {@code state} by a System One model. Jev answers
 * every question of a request in parallel against the same state, so questions are meant
 * to be atomic and composed in code rather than bundled into one broad prompt.
 *
 * @author Christian Tzolov
 * @see Noul
 * @see Choice
 * @see Score
 */
public sealed interface Question permits Noul, Choice, Score {

	/**
	 * @return the primitive this question uses
	 */
	@JsonIgnore
	QuestionType type();

	/**
	 * @return the wire discriminator, written as the {@code type} property
	 */
	@JsonProperty("type")
	default String typeName() {
		return type().value();
	}

	/**
	 * @return what the model is being asked; text, a JSON object or a JSON array, or
	 * {@code null} when the criteria alone carry the question
	 */
	@Nullable
	JsonContent instructions();
}
