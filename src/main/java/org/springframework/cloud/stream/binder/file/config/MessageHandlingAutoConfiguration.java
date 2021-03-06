/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.stream.binder.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.file.MessageController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dave Syer
 */
@Configuration
@ConfigurationProperties("spring.cloud.stream.binder.file")
public class MessageHandlingAutoConfiguration {

	/**
	 * The prefix for the file names.
	 */
	private String prefix = "target/stream";

	/**
	 * Maximum time to wait on start up for files to exist.
	 */
	private long timeoutMillis = 10000;

	public long getTimeoutMillis() {
		return this.timeoutMillis;
	}

	public void setTimeoutMillis(long timeoutMillis) {
		this.timeoutMillis = timeoutMillis;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	@Bean
	public MessageController messageController() {
		MessageController controller = new MessageController(prefix);
		controller.setTimeout(timeoutMillis);
		return controller;
	}
}
