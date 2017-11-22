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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StreamUtils;

/**
 * @author Dave Syer
 *
 */
public class MessageController implements Closeable {

	private static Log logger = LogFactory.getLog(MessageController.class);

	private String prefix;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final Map<String, FileAdapter> queues = new HashMap<>();

	private ExecutorService executor = Executors.newCachedThreadPool();

	public MessageController(String prefix) {
		this.prefix = prefix;
		new File(prefix).mkdirs();
	}

	@Override
	public void close() throws IOException {
		running.set(false);
		executor.shutdownNow();
	}

	public void bind(String name, String group, MessageChannel inputTarget) {
		running.set(true);
		queues.computeIfAbsent(name, key -> new FileAdapter(key)).target = inputTarget;
	}

	public Message<?> receive(String name, long timeout, TimeUnit unit) {
		running.set(true);
		try {
			return queues.computeIfAbsent(name, key -> new FileAdapter(key)).input
					.poll(timeout, unit);
		}
		catch (InterruptedException e) {
			running.set(false);
			Thread.currentThread().interrupt();
			return null;
		}
	}

	public void subscribe(String name, SubscribableChannel outboundBindTarget) {
		outboundBindTarget.subscribe(message -> {
			send(name, message);
		});
	}

	public void send(String name, Message<?> message) {
		running.set(true);
		try {
			queues.computeIfAbsent(name, key -> new FileAdapter(key)).output.put(message);
		}
		catch (InterruptedException e) {
			running.set(false);
			Thread.currentThread().interrupt();
		}
	}

	class FileAdapter {
		private File file;
		private final SynchronousQueue<Message<?>> input = new SynchronousQueue<>();
		private final SynchronousQueue<Message<?>> output = new SynchronousQueue<>();
		private MessageChannel target;

		public FileAdapter(String name) {
			this(name, null);
		}

		public FileAdapter(String name, MessageChannel target) {
			this.target = target;
			this.file = new File(prefix + "/" + name);
			if (!this.file.exists()) {
				logger.debug("Creating: " + file);
				try {
					this.file.createNewFile();
				}
				catch (IOException e) {
					logger.error("Cannot create new file", e);
				}
			}
			logger.debug("Starting background processing for: " + file);
			executor.submit(() -> {
				try {
					listen();
				}
				catch (IOException e) {
					logger.error("Failed to read: " + file, e);
				}
			});
			executor.submit(() -> {
				try {
					write();
				}
				catch (IOException e) {
					logger.error("Failed to write: " + file, e);
				}
			});
		}

		private void write() throws IOException {
			while (running.get()) {
				Message<?> message = null;
				try {
					message = output.take();
				}
				catch (InterruptedException e) {
					running.set(false);
					Thread.currentThread().interrupt();
				}
				if (message != null) {
					String value = message.getPayload().toString();
					logger.debug("Sending to " + file + ": " + value);
					if (!value.endsWith("\n")) {
						value = value + "\n";
					}
					try (FileOutputStream stream = new FileOutputStream(file, true)) {
						StreamUtils.copy(value + "\n\n", StandardCharsets.UTF_8, stream);
					}
				}
			}
		}

		private void listen() throws IOException {
			FileInputStream inputStream = new FileInputStream(file);
			BufferedReader br = null;
			br = new BufferedReader(new InputStreamReader(inputStream));
			logger.debug("Receiving from " + file);
			while (running.get()) {
				StringBuilder sb = new StringBuilder();
				String line = null;
				int count = 0;
				while (running.get() && count < 2 && (line = br.readLine()) != null) {
					logger.debug("Line from " + file + ": " + line);
					if (line.length() == 0) {
						count++;
					}
					else {
						count = 0;
					}
					if (count == 0) {
						sb.append(line + System.getProperty("line.separator"));
					}
				}
				if (line != null && count > 1) {
					if (line.length() > 0) {
						sb.append(line + System.getProperty("line.separator"));
					}
					Message<String> message = MessageBuilder.withPayload(sb.toString())
							.build();
					if (this.target != null) {
						target.send(message);
					}
					else {
						try {
							input.put(message);
						}
						catch (InterruptedException e) {
							running.set(false);
							Thread.currentThread().interrupt();
						}
					}
				}
			}
			br.close();
		}
	}
}
