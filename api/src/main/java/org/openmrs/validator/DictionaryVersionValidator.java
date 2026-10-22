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

import org.openmrs.DictionaryVersion;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validates {@link DictionaryVersion} objects.
 *
 * @since 3.0.0
 */
@Handler(supports = { DictionaryVersion.class }, order = 50)
public class DictionaryVersionValidator implements Validator {

	@Override
	public boolean supports(Class<?> c) {
		return DictionaryVersion.class.isAssignableFrom(c);
	}

	/**
	 * <strong>Should</strong> fail if the label or in-use-from time is missing<br/>
	 * <strong>Should</strong> fail if another version has the same label
	 *
	 * @see org.springframework.validation.Validator#validate(java.lang.Object,
	 *      org.springframework.validation.Errors)
	 */
	@Override
	public void validate(Object obj, Errors errors) {
		DictionaryVersion version = (DictionaryVersion) obj;
		if (version == null) {
			errors.reject("error.general");
			return;
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "error.name");
		ValidationUtils.rejectIfEmpty(errors, "inUseFrom", "error.null");
		if (!errors.hasErrors()) {
			for (DictionaryVersion other : Context.getConceptService().getAllDictionaryVersions(true)) {
				if (other.getName().equalsIgnoreCase(version.getName()) && !other.getUuid().equals(version.getUuid())) {
					errors.rejectValue("name", "DictionaryVersion.error.duplicateLabel");
				}
			}
		}
		ValidateUtil.validateFieldLengths(errors, obj.getClass(), "name", "description", "retireReason", "cielRelease",
		    "snomedRelease", "loincRelease");
	}
}
