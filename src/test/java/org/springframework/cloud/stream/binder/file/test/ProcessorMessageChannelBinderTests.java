/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.stream.binder.file.test;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.binder.file.MessageController;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest({ "logging.level.root=INFO",
		"logging.level.org.springframework.cloud.stream.binder.file=DEBUG", "logging.level.org.springframework.integration=DEBUG" })
@DirtiesContext
public class ProcessorMessageChannelBinderTests {

	private String prefix = "target/stream";

	@Autowired
	private Processor processor;

	@Autowired
	private MessageController controller;

	@Before
	public void init() throws Exception {
	}

	@Test
	public void supplier() throws Exception {
		processor.output().send(MessageBuilder.withPayload("hello").build());
		String message = (String) controller.receive("output", 100, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("hello");
	}

	@Test
	public void function() throws Exception {
		controller.send("input", MessageBuilder.withPayload("hello").build());
		String message = (String) controller.receive("output", 100, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("HELLO");
	}

	@Test
	public void multi() throws Exception {
		controller.send("input", MessageBuilder.withPayload("hello").build());
		controller.send("input", MessageBuilder.withPayload("world").build());
		String message = (String) controller.receive("output", 100, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("HELLO");
		message = (String) controller.receive("output", 2000, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("WORLD");
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public String uppercase(String input) {
			return input.toUpperCase();
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(
					ProcessorMessageChannelBinderTests.TestConfiguration.class,
					"--logging.level.root=INFO");
		}

	}

}
