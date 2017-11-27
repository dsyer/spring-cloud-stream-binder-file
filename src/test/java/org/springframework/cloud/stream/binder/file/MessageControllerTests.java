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

package org.springframework.cloud.stream.binder.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class MessageControllerTests {

	private MessageController controller = new MessageController("target/test");
	private File root;

	@Before
	public void init() throws IOException {
		root = new File("target/test");
		FileSystemUtils.deleteRecursively(root);
		root.mkdirs();
		new File(root, "input").createNewFile();
		new File(root, "output").createNewFile();
	}

	@After
	public void close() throws Exception {
		controller.close();
	}

	@Test
	public void subscribe() throws Exception {
		SubscribableChannel outbound = new DirectChannel();
		controller.subscribe("output", outbound);
		outbound.send(MessageBuilder.withPayload("hello").build());
		String result = getOutput("output");
		assertThat(result).isEqualTo("hello\n");
	}

	@Test
	public void bind() throws Exception {
		SubscribableChannel inbound = new DirectChannel();
		controller.bind("input", "default", inbound);
		AtomicReference<Message<?>> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		inbound.subscribe(message -> {
			result.set(message);
			latch.countDown();
		});
		write("world\n", "input");
		latch.await(1000L, TimeUnit.MILLISECONDS);
		assertThat(result.get().getPayload()).isEqualTo("world");
	}

	@Test
	public void sendNoHeaders() throws Exception {
		controller.send("output", MessageBuilder.withPayload("hello").build());
		String result = getOutput("output");
		assertThat(result).isEqualTo("hello\n");
	}

	@Test
	public void sendNoHeadersTwoMessages() throws Exception {
		controller.send("output", MessageBuilder.withPayload("hello").build());
		controller.send("output", MessageBuilder.withPayload("world").build());
		String result = getOutput("output", "world");
		assertThat(result).isEqualTo("hello\nworld\n");
	}

	@Test
	public void sendHeaders() throws Exception {
		controller.send("output",
				MessageBuilder.withPayload("world").setHeader("foo", "bar").build());
		String result = getOutput("output");
		assertThat(result).isEqualTo("#headers\nfoo=bar\n#payload\nworld\n#end\n");
	}

	@Test
	public void sendHeadersTwoMessages() throws Exception {
		controller.send("output",
				MessageBuilder.withPayload("hello").setHeader("foo", "bar").build());
		controller.send("output",
				MessageBuilder.withPayload("world").setHeader("foo", "baz").build());
		String result = getOutput("output", "world");
		assertThat(result).isEqualTo(
				"#headers\nfoo=bar\n#payload\nhello\n#end\n#headers\nfoo=baz\n#payload\nworld\n#end\n");
	}

	@Test
	public void receiveNoHeaders() throws Exception {
		write("hello\n", "input");
		Message<?> result = controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("hello");
	}

	@Test
	public void receiveExplicitPayloadNoHeaders() throws Exception {
		write("#payload\nhello\n#end\n", "input");
		Message<?> result = controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("hello");
	}

	@Test
	public void receiveNoHeadersTwoMessages() throws Exception {
		write("hello\nworld\n", "input");
		controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		Message<?> result = controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("world");
	}

	@Test
	public void receiveHeaders() throws Exception {
		write("#headers\nfoo=bar\n#payload\nworld\n#end\n", "input");
		Message<?> result = controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("world");
		assertThat(result.getHeaders()).containsEntry("foo", "bar");
	}

	@Test
	public void receiveHeadersEmptyPayload() throws Exception {
		write("#headers\nfoo=bar\n#end\n", "input");
		Message<?> result = controller.receive("input", 100L, TimeUnit.MILLISECONDS);
		assertThat(result.getPayload()).isEqualTo("");
		assertThat(result.getHeaders()).containsEntry("foo", "bar");
	}

	private void write(String value, String filename)
			throws IOException, FileNotFoundException {
		StreamUtils.copy(value, Charset.forName("UTF-8"),
				new FileOutputStream(new File(root, filename)));
	}

	private String getOutput(String output) throws Exception {
		return getOutput(output, null);
	}

	private String getOutput(String output, String pattern) throws Exception {
		File file = new File(root, output);
		String result = null;
		int i = 0;
		while (StringUtils.isEmpty(result) && i++ < 10) {
			result = StreamUtils.copyToString(new FileInputStream(file),
					Charset.defaultCharset());
			Thread.sleep(20L);
			if (pattern != null && !result.contains(pattern)) {
				result = null;
			}
		}
		return result;
	}

}
