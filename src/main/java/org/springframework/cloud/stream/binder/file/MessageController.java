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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
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
			FileOutputStream stream = new FileOutputStream(file, true);
			try {
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
						StringBuilder sb = new StringBuilder();
						if (!message.getHeaders().isEmpty()) {
							StringBuilder hb = new StringBuilder();
							for (Entry<String, Object> entry : message.getHeaders()
									.entrySet()) {
								if (!"id".equals(entry.getKey())
										&& !"timestamp".equals(entry.getKey())
										&& entry.getValue() instanceof String) {
									if (hb.length() == 0) {
										hb.append("#headers\n");
									}
									hb.append(entry.getKey()).append("=")
											.append(entry.getValue()).append("\n");
								}
							}
							if (hb.length() > 0) {
								sb.append(hb);
							}
						}
						String value = message.getPayload().toString();
						boolean needsEnd = false;
						if (value.contains("\n") || sb.length() > 0) {
							sb.append("#payload\n");
							needsEnd = true;
						}
						sb.append(value).append("\n");
						if (needsEnd) {
							sb.append("#end\n");
						}
						logger.debug("Sending to " + file + ": " + sb);
						StreamUtils.copy(sb.toString(), StandardCharsets.UTF_8, stream);
						stream.flush();
					}
				}
			}
			finally {
				stream.close();
			}
		}

		private void listen() throws IOException {
			FileInputStream inputStream = new FileInputStream(file);
			BufferedReader br = null;
			br = new BufferedReader(new InputStreamReader(inputStream));
			logger.debug("Receiving from " + file);
			while (running.get()) {
				String line = br.readLine();
				MessageHeaders headers = null;
				if (line != null && line.equals("#headers")) {
					Map<String, Object> map = new LinkedHashMap<>();
					while (running.get() && line != null) {
						line = br.readLine();
						logger.debug("Header line from " + file + ": " + line);
						if (line == null || line.startsWith("#")) {
							break;
						}
						int index = line.indexOf("=");
						String key = index >= 0 ? line.substring(0, index) : line;
						String value = index >= 0 ? line.substring(index + 1) : null;
						map.put(key, value);
					}
					headers = map.isEmpty() ? null : new MessageHeaders(map);
				}
				StringBuilder sb = new StringBuilder();
				boolean nested = false;
				while (running.get() && line != null) {
					logger.debug("Line from " + file + ": " + line);
					if (line.equals("#payload")) {
						nested = true;
						line = br.readLine();
						continue;
					}
					if (line.equals("#end")) {
						break;
					}
					sb.append(line);
					if (nested) {
						line = br.readLine();
						if (line.equals("#end")) {
							break;
						}
						sb.append(System.getProperty("line.separator"));
					}
					else {
						break;
					}
				}
				if (sb.length() > 0 || headers != null) {
					MessageBuilder<String> builder = MessageBuilder
							.withPayload(sb.toString());
					if (headers != null) {
						builder.copyHeadersIfAbsent(headers);
					}
					Message<String> message = builder.build();
					logger.debug("Assembed from " + file + ": " + message);
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
				if (line == null) {
					// Reached end of file. So it's not a fifo, or the producer closed it,
					// and we should sleep to prevent a busy wait. TODO: should we also
					// empty the file, if it is a file?
					try {
						Thread.sleep(20L);
					}
					catch (InterruptedException e) {
						running.set(false);
						Thread.currentThread().interrupt();
					}
				}
			}
			br.close();
		}

	}
}
