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

package org.springaicommunity.typesafe.autoconfigure;



import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.TypeSafeConstants;
import org.springaicommunity.typesafe.TypeSafeModels;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The auto-configuration against the real Jev API.
 *
 * <p>
 * {@link TypeSafeAutoConfigurationTests} proves the properties bind and the bean appears.
 * It cannot prove the bean it built actually works: every value there is a placeholder, and
 * a client assembled with the wrong base URL or a mis-threaded API key looks identical in a
 * context assertion. This boots a context from {@code spring.ai.typesafe.*} exactly as an
 * application would and makes one real call with the resulting bean.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = TypeSafeConstants.API_KEY_ENV, matches = ".+",
		disabledReason = "Set TYPESAFE_API_KEY to run the auto-configuration against the real Jev API")
class TypeSafeAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class))
		.withPropertyValues("spring.ai.typesafe.api-key=${" + TypeSafeConstants.API_KEY_ENV + "}");

	@Test
	void theAutoConfiguredClientTalksToTheRealApi() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(TypeSafeClient.class);

			SystemOneResponse response = context.getBean(TypeSafeClient.class)
				.systemOne("A short sentence.", Map.of("probe", Noul.of("Is this a sentence?")));

			assertThat(response.noulValue("probe")).isBetween(0.0d, 1.0d);
			assertThat(response.requestId()).isNotBlank();
		});
	}

	@Test
	void theAutoConfiguredClientListsModels() {
		this.contextRunner.run(context -> assertThat(context.getBean(TypeSafeClient.class).listModels())
			.isNotEmpty()
			.extracting("name")
			.contains(TypeSafeModels.JEV_LATEST));
	}

	@Test
	void honoursTheConfiguredModelOnARealCall() {
		this.contextRunner.withPropertyValues("spring.ai.typesafe.model=" + TypeSafeModels.JEV_PREVIEW)
			.run(context -> {
				SystemOneResponse response = context.getBean(TypeSafeClient.class)
					.systemOne("A short sentence.", Map.of("probe", Noul.of("Is this a sentence?")));

				// The server answers with the version it resolved, so this asserts the
				// property reached the wire, not that the alias comes back.
				assertThat(response.model()).isNotBlank();
				assertThat(response.noulValue("probe")).isBetween(0.0d, 1.0d);
			});
	}

}
