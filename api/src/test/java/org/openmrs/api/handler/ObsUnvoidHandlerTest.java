/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.handler;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.openmrs.Obs;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the {@link ObsUnvoidHandler} class.
 */
public class ObsUnvoidHandlerTest {

	/**
	 * @see ObsUnvoidHandler#handle(Obs, org.openmrs.User, Date, String)
	 */
	@Test
	public void handle_shouldSetAnEnteredInErrorObsBackToFinal() {
		Date dateVoided = new Date();
		Obs obs = voidedObs(dateVoided);

		new ObsUnvoidHandler().handle(obs, null, dateVoided, null);

		assertEquals(Obs.Status.FINAL, obs.getStatus());
	}

	/**
	 * @see ObsUnvoidHandler#handle(Obs, org.openmrs.User, Date, String)
	 */
	@Test
	public void handle_shouldNotActOnAnObsWithADifferentDateVoided() {
		Obs obs = voidedObs(new Date(0));

		new ObsUnvoidHandler().handle(obs, null, new Date(), null);

		assertEquals(Obs.Status.ENTERED_IN_ERROR, obs.getStatus());
	}

	private Obs voidedObs(Date dateVoided) {
		Obs obs = new Obs(1);
		obs.setVoided(true);
		obs.setDateVoided(dateVoided);
		obs.setStatus(Obs.Status.ENTERED_IN_ERROR);
		return obs;
	}
}
