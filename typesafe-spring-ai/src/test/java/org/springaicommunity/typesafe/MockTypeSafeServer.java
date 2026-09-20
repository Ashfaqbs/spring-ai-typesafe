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



import org.springaicommunity.typesafe.exception.TypeSafeApiException;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

/**
 * Pairs a {@link MockRestServiceServer} with a {@link TypeSafeClient} that talks to it, so the
 * judge and the advisor can be exercised against real HTTP plumbing without a real API
 * key.
 *
 * @author Christian Tzolov
 */
public final class MockTypeSafeServer {

	public static final String BASE_URL = "https://api.typesafe.ai";

	public static final String SYSTEM_ONE_URL = BASE_URL + "/v1/systemone";

	private final MockRestServiceServer server;

	private final TypeSafeClient client;

	private MockTypeSafeServer(MockRestServiceServer server, TypeSafeClient client) {
		this.server = server;
		this.client = client;
	}

	public static MockTypeSafeServer create() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).bufferContent().build();
		// baseUrl and defaultModel are pinned so that a developer's exported
		// TYPESAFE_BASE_URL / TYPESAFE_DEFAULT_MODEL cannot reach these offline tests.
		TypeSafeClient client = TypeSafeClient.builder()
			.apiKey("test-api-key")
			.baseUrl(BASE_URL)
			.defaultModel(TypeSafeModels.JEV_LATEST)
			.retryPolicy(RetryPolicy.noRetry())
			.restClientBuilder(restClientBuilder)
			.build();
		return new MockTypeSafeServer(server, client);
	}

	public MockRestServiceServer server() {
		return this.server;
	}

	public TypeSafeClient client() {
		return this.client;
	}

	public static ResponseCreator jsonResponse(String body) {
		return MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON)
			.header(TypeSafeApiException.REQUEST_ID_HEADER, "req_0123456789");
	}

	/**
	 * @return a response creator for an error status, so a test can exercise what happens
	 * when one call of a fan-out fails while its siblings succeed
	 */
	public static ResponseCreator errorResponse(int status, String body) {
		return MockRestResponseCreators.withStatus(HttpStatusCode.valueOf(status))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body);
	}

}
