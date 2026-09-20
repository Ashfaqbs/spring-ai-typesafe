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

package org.springaicommunity.typesafe.response;



import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The discriminator values an answer can carry. {@link #UNKNOWN} is not a wire value; it
 * marks an answer kind this SDK version does not model yet.
 *
 * @author Christian Tzolov
 */
public enum AnswerType {

	NOUL("noul"), CHOICE("choice"), SCORE("score"), UNKNOWN("unknown");

	private final String value;

	AnswerType(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return this.value;
	}
}
