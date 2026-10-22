/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate;

import org.apache.commons.lang3.ArrayUtils;
import org.openmrs.Obs;
import org.springframework.stereotype.Component;

/**
 * ImmutableEntityInterceptor for Obs, it catches any edited Obs, voids and replaces it with a new
 * one. I also sets the original Obs as the previous Obs for the newly created one. The exceptions
 * are when editing an already voided Obs
 *
 * @see ImmutableEntityInterceptor
 * @since 2.0.0
 */
@Component("immutableObsInterceptor")
public class ImmutableObsInterceptor extends ImmutableEntityInterceptor {

	private static final String[] MUTABLE_PROPERTY_NAMES = new String[] { "voided", "dateVoided", "voidedBy", "voidReason",
	        "groupMembers" };

	/**
	 * @see ImmutableEntityInterceptor#getSupportedType()
	 */
	@Override
	protected Class<?> getSupportedType() {
		return Obs.class;
	}

	/**
	 * @see ImmutableEntityInterceptor#getMutablePropertyNames()
	 */
	@Override
	protected String[] getMutablePropertyNames() {
		return MUTABLE_PROPERTY_NAMES;
	}

	/**
	 * Lets the status leave {@link Obs.Status#ENTERED_IN_ERROR} when the obs is unvoided. Voiding is
	 * already allowed, since changes to voided obs are ignored.
	 *
	 * @see ImmutableEntityInterceptor#isAllowedChange(String, Object[], Object[], String[])
	 */
	@Override
	protected boolean isAllowedChange(String property, Object[] currentState, Object[] previousState,
	        String[] propertyNames) {
		if (!"status".equals(property) || previousState == null) {
			return false;
		}
		int status = ArrayUtils.indexOf(propertyNames, "status");
		int voided = ArrayUtils.indexOf(propertyNames, "voided");
		return Obs.Status.ENTERED_IN_ERROR.equals(previousState[status]) && Boolean.TRUE.equals(previousState[voided])
		        && Boolean.FALSE.equals(currentState[voided]);
	}

	/**
	 * @see ImmutableEntityInterceptor#ignoreVoidedOrRetiredObjects()
	 */
	@Override
	protected boolean ignoreVoidedOrRetiredObjects() {
		return true;
	}
}
