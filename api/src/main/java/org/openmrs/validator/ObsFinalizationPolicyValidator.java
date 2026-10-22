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

import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validates {@link ObsFinalizationPolicy} objects.
 *
 * @since 3.0.0
 */
@Handler(supports = { ObsFinalizationPolicy.class }, order = 50)
public class ObsFinalizationPolicyValidator implements Validator {

	@Override
	public boolean supports(Class<?> c) {
		return ObsFinalizationPolicy.class.isAssignableFrom(c);
	}

	/**
	 * Checks the policy is complete and that no other policy that is not retired covers the same agent
	 * type and concept class, so which policy applies to an obs is never in doubt.
	 * <p>
	 * <strong>Should</strong> fail if the name, agent type or action is missing<br/>
	 * <strong>Should</strong> fail if another policy covers the same agent type and concept class<br/>
	 * <strong>Should</strong> pass for a policy that differs only in concept class<br/>
	 * <strong>Should</strong> pass for a retired policy
	 *
	 * @see org.springframework.validation.Validator#validate(java.lang.Object,
	 *      org.springframework.validation.Errors)
	 */
	@Override
	public void validate(Object obj, Errors errors) {
		ObsFinalizationPolicy policy = (ObsFinalizationPolicy) obj;
		if (policy == null) {
			errors.reject("error.general");
			return;
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "error.name");
		ValidationUtils.rejectIfEmpty(errors, "agentType", "error.null");
		ValidationUtils.rejectIfEmpty(errors, "action", "error.null");
		if (!errors.hasErrors() && !policy.getRetired()) {
			try {
				// managing policies is enough to save one, without the privilege to get them
				Context.addProxyPrivilege(PrivilegeConstants.GET_OBS_FINALIZATION_POLICIES);
				for (ObsFinalizationPolicy other : Context.getObsService().getAllObsFinalizationPolicies(false)) {
					if (other.getAgentType() == policy.getAgentType()
					        && OpenmrsUtil.nullSafeEquals(other.getConceptClass(), policy.getConceptClass())
					        && !other.getUuid().equals(policy.getUuid())) {
						errors.rejectValue("conceptClass", "ObsFinalizationPolicy.error.duplicate");
					}
				}
			} finally {
				Context.removeProxyPrivilege(PrivilegeConstants.GET_OBS_FINALIZATION_POLICIES);
			}
		}
		ValidateUtil.validateFieldLengths(errors, obj.getClass(), "name", "description", "retireReason");
	}
}
