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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest({ "spring.cloud.stream.binder.file.prefix=target/streams",
		"logging.level.root=INFO",
		"logging.level.org.springframework.cloud.stream.binder.file=DEBUG",
		"logging.level.org.springframework.integration=DEBUG" })
@DirtiesContext
public class HeadersTests {

	@Autowired
	private Processor processor;

	@Autowired
	private MessageController controller;

	@BeforeClass
	public static void init() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/streams"));
	}

	@Test
	public void supplier() throws Exception {
		processor.output().send(
				MessageBuilder.withPayload("hello").setHeader("foo", "bar").build());
		Message<?> message = controller.receive("output", 100, TimeUnit.MILLISECONDS);
		assertThat((String) message.getPayload()).contains("hello");
		assertThat(message.getHeaders()).containsEntry("foo", "bar");
	}

	@Test
	public void function() throws Exception {
		controller.send("input",
				MessageBuilder.withPayload("hello").setHeader("foo", "bar").build());
		Message<?> message = controller.receive("output", 100, TimeUnit.MILLISECONDS);
		assertThat((String) message.getPayload()).contains("HELLO");
		assertThat(message.getHeaders()).containsEntry("foo", "bar");
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public Message<String> uppercase(Message<String> input) {
			return MessageBuilder.withPayload(input.getPayload().toUpperCase())
					.copyHeaders(input.getHeaders()).build();
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(HeadersTests.TestConfiguration.class,
					"--logging.level.root=INFO");
		}

	}

}
