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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

/**
 * Test fixture pairing a {@link MockRestServiceServer} with a {@link TypeSafeClient} that
 * speaks to it. The mock server has to be bound to the {@link RestClient.Builder} before
 * the client is built, because the client clones that builder.
 *
 * @author Christian Tzolov
 */
final class MockTypeSafeServer {

	static final String BASE_URL = "https://api.typesafe.ai";

	static final String SYSTEM_ONE_URL = BASE_URL + "/v1/systemone";

	static final String MODELS_URL = BASE_URL + "/v1/models";

	private final MockRestServiceServer server;

	private final RestClient.Builder restClientBuilder;

	private MockTypeSafeServer(RestClient.Builder restClientBuilder, MockRestServiceServer server) {
		this.restClientBuilder = restClientBuilder;
		this.server = server;
	}

	static MockTypeSafeServer create() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
		return new MockTypeSafeServer(builder, server);
	}

	MockRestServiceServer server() {
		return this.server;
	}

	TypeSafeClient client() {
		return clientBuilder().build();
	}

	TypeSafeClient client(RetryPolicy retryPolicy) {
		return clientBuilder().retryPolicy(retryPolicy).build();
	}

	/**
	 * Every value the client would otherwise take from the environment is pinned here.
	 * {@code TypeSafeClient.Builder} falls back to {@code TYPESAFE_BASE_URL} and
	 * {@code TYPESAFE_DEFAULT_MODEL}, so leaving either unset would let a developer's own
	 * exported variables change the request bodies these tests compare strictly.
	 */
	TypeSafeClient.Builder clientBuilder() {
		return TypeSafeClient.builder()
			.apiKey("test-api-key")
			.baseUrl(BASE_URL)
			.defaultModel(TypeSafeModels.JEV_LATEST)
			.restClientBuilder(this.restClientBuilder);
	}

	/**
	 * @return a response creator for a successful JSON body carrying a request id
	 */
	static org.springframework.test.web.client.ResponseCreator jsonResponse(String body) {
		return MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON)
			.header(TypeSafeApiException.REQUEST_ID_HEADER, "req_0123456789");
	}

	/**
	 * @return a response creator for an error status with an arbitrary body and headers
	 */
	static org.springframework.test.web.client.ResponseCreator errorResponse(int status, String body,
			HttpHeaders headers) {
		return MockRestResponseCreators.withStatus(HttpStatusCode.valueOf(status))
			.contentType(MediaType.APPLICATION_JSON)
			.headers(headers)
			.body(body);
	}

	static org.springframework.test.web.client.ResponseCreator errorResponse(int status, String body) {
		return errorResponse(status, body, new HttpHeaders());
	}

}
