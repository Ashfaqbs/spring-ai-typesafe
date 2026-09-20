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



import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A chat model that replies with a scripted sequence of answers and records the prompts it
 * was given, so a test can assert what the advisor fed back on a retry. Plain
 * implementation rather than a mock: the advisor's loop is the thing under test, and a
 * script reads better than stubbing.
 *
 * @author Christian Tzolov
 */
public class ScriptedChatModel implements ChatModel {

	private final Deque<String> answers = new ArrayDeque<>();

	private final List<Prompt> receivedPrompts = new ArrayList<>();

	public ScriptedChatModel(String... answers) {
		this.answers.addAll(List.of(answers));
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		this.receivedPrompts.add(prompt);
		String answer = this.answers.isEmpty() ? "" : this.answers.poll();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
	}

	/**
	 * @return every prompt this model was called with, in order
	 */
	public List<Prompt> receivedPrompts() {
		return List.copyOf(this.receivedPrompts);
	}

	/**
	 * @return the number of times the model was called
	 */
	public int callCount() {
		return this.receivedPrompts.size();
	}

	/**
	 * @param index the zero-based call index
	 * @return the text of the last user message of that call
	 */
	public String userMessageOfCall(int index) {
		List<org.springframework.ai.chat.messages.Message> instructions = this.receivedPrompts.get(index)
			.getInstructions();
		for (int i = instructions.size() - 1; i >= 0; i--) {
			if (instructions.get(i) instanceof org.springframework.ai.chat.messages.UserMessage userMessage) {
				return userMessage.getText();
			}
		}
		return "";
	}

}
