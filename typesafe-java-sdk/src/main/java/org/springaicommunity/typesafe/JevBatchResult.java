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

package org.springaicommunity.typesafe;



import org.jspecify.annotations.Nullable;
import org.springaicommunity.typesafe.exception.TypeSafeException;

/**
 * The outcome of one request in a batch: either a value or the failure that replaced it.
 *
 * <p>
 * A batch reports failures per request rather than throwing, because the recipes that need
 * one call per item — reranking a candidate list, classifying retrieved passages — would
 * otherwise lose every good answer to one bad one. A caller that does want the exception
 * calls {@link #orThrow()}.
 *
 * @param <T> the value type
 * @param index the position of this request in the list that was submitted
 * @param value the result, or {@code null} when the request failed
 * @param failure the failure, or {@code null} when the request succeeded
 * @author Christian Tzolov
 */
public record JevBatchResult<T>(int index, @Nullable T value, @Nullable TypeSafeException failure) {

	/**
	 * @return {@code true} when this request produced a value
	 */
	public boolean succeeded() {
		return this.failure == null;
	}

	/**
	 * @return the value
	 * @throws TypeSafeException the original failure, when this request did not succeed
	 */
	public T orThrow() {
		if (this.failure != null) {
			throw this.failure;
		}
		// Unreachable for a well-formed result: one of the two is always set.
		if (this.value == null) {
			throw new TypeSafeException("Batch result " + this.index + " carried neither a value nor a failure");
		}
		return this.value;
	}

	/**
	 * @param fallback the value to use when this request failed
	 * @return the value, or {@code fallback}
	 */
	public T orElse(T fallback) {
		return this.value == null ? fallback : this.value;
	}

}
