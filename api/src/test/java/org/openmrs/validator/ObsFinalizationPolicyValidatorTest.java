/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.validator;

import org.junit.jupiter.api.Test;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.UserType;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link ObsFinalizationPolicyValidator} class.
 */
public class ObsFinalizationPolicyValidatorTest extends BaseContextSensitiveTest {

	/**
	 * @see ObsFinalizationPolicyValidator#validate(Object, Errors)
	 */
	@Test
	public void validate_shouldFailIfTheNameAgentTypeOrActionIsMissing() {
		Errors errors = validate(new ObsFinalizationPolicy());

		assertTrue(errors.hasFieldErrors("name"));
		assertTrue(errors.hasFieldErrors("agentType"));
		assertTrue(errors.hasFieldErrors("action"));
	}

	/**
	 * @see ObsFinalizationPolicyValidator#validate(Object, Errors)
	 */
	@Test
	public void validate_shouldFailIfAnotherPolicyCoversTheSameAgentTypeAndConceptClass() {
		Context.getObsService().saveObsFinalizationPolicy(softwarePolicy());

		assertTrue(validate(softwarePolicy()).hasFieldErrors("conceptClass"));
	}

	/**
	 * @see ObsFinalizationPolicyValidator#validate(Object, Errors)
	 */
	@Test
	public void validate_shouldPassForAPolicyThatDiffersOnlyInConceptClass() {
		Context.getObsService().saveObsFinalizationPolicy(softwarePolicy());
		ObsFinalizationPolicy drugs = softwarePolicy();
		drugs.setConceptClass(Context.getConceptService().getConceptClass(3));

		assertFalse(validate(drugs).hasErrors());
	}

	/**
	 * @see ObsFinalizationPolicyValidator#validate(Object, Errors)
	 */
	@Test
	public void validate_shouldPassForARetiredPolicy() {
		Context.getObsService().saveObsFinalizationPolicy(softwarePolicy());
		ObsFinalizationPolicy retired = softwarePolicy();
		retired.setRetired(true);

		assertFalse(validate(retired).hasErrors());
	}

	private ObsFinalizationPolicy softwarePolicy() {
		return new ObsFinalizationPolicy("software needs attestation", UserType.SOFTWARE, null,
		        ObsFinalizationPolicy.Action.REQUIRE_ATTESTATION);
	}

	private Errors validate(ObsFinalizationPolicy policy) {
		Errors errors = new BindException(policy, "policy");
		new ObsFinalizationPolicyValidator().validate(policy, errors);
		return errors;
	}
}
