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
		"spring.cloud.stream.bindings.output.contentType=application/json",
		"spring.cloud.stream.bindings.input.contentType=application/json",
		"logging.level.root=INFO",
		"logging.level.org.springframework.cloud.stream.binder.file=DEBUG",
		"logging.level.org.springframework.integration=DEBUG" })
@DirtiesContext
public class PojoMessageChannelBinderTests {

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
		processor.output().send(MessageBuilder.withPayload(new Foo("hello")).build());
		String message = (String) controller.receive("output", 1000, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("\"hello\"");
	}

	@Test
	public void function() throws Exception {
		controller.send("input", MessageBuilder.withPayload("{\"value\":\"world\"}").build());
		String message = (String) controller.receive("output", 1000, TimeUnit.MILLISECONDS)
				.getPayload();
		assertThat(message).contains("\"WORLD\"");
	}

	@SpringBootApplication
	@EnableBinding(Processor.class)
	protected static class TestConfiguration {
		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public Foo uppercase(Foo input) {
			return new Foo(input.getValue().toUpperCase());
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(PojoMessageChannelBinderTests.TestConfiguration.class,
					"--logging.level.root=INFO");
		}

	}

	public static class Foo {
		private String value;

		public Foo() {
		}

		public Foo(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}
