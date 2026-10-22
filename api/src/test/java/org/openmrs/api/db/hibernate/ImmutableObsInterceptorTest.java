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

import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.api.UnchangeableObjectException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the {@link ImmutableObsInterceptor} class.
 */
public class ImmutableObsInterceptorTest {

	private static final String[] PROPERTIES = { "status", "voided" };

	private final ImmutableObsInterceptor interceptor = new ImmutableObsInterceptor();

	/**
	 * @see ImmutableObsInterceptor#isAllowedChange(String, Object[], Object[], String[])
	 */
	@Test
	public void onFlushDirty_shouldAllowAnEnteredInErrorObsToGoBackToFinalAsItIsUnvoided() {
		assertFalse(flush(Obs.Status.ENTERED_IN_ERROR, true, Obs.Status.FINAL, false));
	}

	/**
	 * @see ImmutableObsInterceptor#isAllowedChange(String, Object[], Object[], String[])
	 */
	@Test
	public void onFlushDirty_shouldAllowAnEnteredInErrorObsToGoBackToPreliminaryAsItIsUnvoided() {
		assertFalse(flush(Obs.Status.ENTERED_IN_ERROR, true, Obs.Status.PRELIMINARY, false));
	}

	/**
	 * @see ImmutableObsInterceptor#isAllowedChange(String, Object[], Object[], String[])
	 */
	@Test
	public void onFlushDirty_shouldNotAllowAnEnteredInErrorObsToTakeAnotherStatus() {
		assertThrows(UnchangeableObjectException.class,
		    () -> flush(Obs.Status.ENTERED_IN_ERROR, true, Obs.Status.AMENDED, false));
	}

	/**
	 * @see ImmutableObsInterceptor#isAllowedChange(String, Object[], Object[], String[])
	 */
	@Test
	public void onFlushDirty_shouldNotAllowTheStatusOfAnObsThatWasNotVoidedToChange() {
		assertThrows(UnchangeableObjectException.class,
		    () -> flush(Obs.Status.FINAL, false, Obs.Status.ENTERED_IN_ERROR, false));
	}

	private boolean flush(Obs.Status previousStatus, boolean previouslyVoided, Obs.Status status, boolean voided) {
		Obs obs = new Obs(1);
		obs.setVoided(voided);
		return interceptor.onFlushDirty(obs, 1, new Object[] { status, voided },
		    new Object[] { previousStatus, previouslyVoided }, PROPERTIES, null);
	}
}
