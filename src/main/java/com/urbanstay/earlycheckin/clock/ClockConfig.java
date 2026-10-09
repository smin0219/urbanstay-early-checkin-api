package com.urbanstay.earlycheckin.clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	VirtualClock virtualClock(ClockProperties properties) {
		return new VirtualClock(properties.start(), properties.zone());
	}

}
