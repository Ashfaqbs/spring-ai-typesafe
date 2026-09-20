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



import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of a {@code GET /v1/models} response.
 *
 * @param models the models available to the account
 * @author Christian Tzolov
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListModelsResponse(@JsonProperty("models") List<ModelMetadata> models) {

	public ListModelsResponse {
		models = models == null ? List.of() : List.copyOf(models);
	}
}
