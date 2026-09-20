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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starter's whole contract with Boot is one line in a text file, and a text file does
 * not fail to compile. Every other test here registers {@link TypeSafeAutoConfiguration}
 * explicitly through {@code AutoConfigurations.of(...)}, which never reads the imports
 * file — so a class renamed or moved to another package leaves a starter that silently
 * configures nothing, with a green build.
 *
 * @author Christian Tzolov
 */
class AutoConfigurationImportsTests {

	private static final String IMPORTS_RESOURCE = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

	@Test
	void everyClassNamedInTheImportsFileIsOnTheClasspath() throws IOException, ClassNotFoundException {
		List<String> names = importedClassNames();

		assertThat(names).isNotEmpty();
		for (String name : names) {
			// Class.forName rather than a string comparison: the point is that Boot can
			// actually load what the file names, not that the text looks plausible.
			assertThat(Class.forName(name)).isNotNull();
		}
	}

	@Test
	void theStarterRegistersTheTypeSafeAutoConfiguration() throws IOException {
		assertThat(importedClassNames()).contains(TypeSafeAutoConfiguration.class.getName());
	}

	private static List<String> importedClassNames() throws IOException {
		URL resource = AutoConfigurationImportsTests.class.getClassLoader().getResource(IMPORTS_RESOURCE);
		assertThat(resource).as("the starter must ship %s", IMPORTS_RESOURCE).isNotNull();

		List<String> names = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
					names.add(trimmed);
				}
			}
		}
		return names;
	}

}
