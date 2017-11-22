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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.stream.binder.file.MessageController;
import org.springframework.cloud.stream.binder.file.FileMessageChannelBinder;
import org.springframework.cloud.stream.config.codec.kryo.KryoCodecAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.codec.Codec;

/**
 * @author Dave Syer
 */
@Configuration
@Import({ PropertyPlaceholderAutoConfiguration.class, KryoCodecAutoConfiguration.class })
public class FileMessageChannelBinderConfiguration {

	@Autowired
	private Codec codec;

	@Bean
	public FileMessageChannelBinder fileMessageChannelBinder(
			MessageController controller) {

		FileMessageChannelBinder messageChannelBinder = new FileMessageChannelBinder(
				controller);
		messageChannelBinder.setCodec(this.codec);
		return messageChannelBinder;
	}

}
