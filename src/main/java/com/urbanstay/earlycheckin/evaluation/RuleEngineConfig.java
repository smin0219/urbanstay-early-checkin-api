package com.urbanstay.earlycheckin.evaluation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.urbanstay.earlycheckin.rule.CheckInPolicy;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.RoomAssignmentRule;

@Configuration(proxyBeanMethods = false)
class RuleEngineConfig {

	@Bean
	CheckInPolicy checkInPolicy(CheckInPolicyProperties properties) {
		return properties.toPolicy();
	}

	@Bean
	CheckInRuleEngine checkInRuleEngine(CheckInPolicy policy) {
		return new CheckInRuleEngine(policy);
	}

	@Bean
	RoomAssignmentRule roomAssignmentRule(CheckInPolicy policy) {
		return new RoomAssignmentRule(policy);
	}

}
