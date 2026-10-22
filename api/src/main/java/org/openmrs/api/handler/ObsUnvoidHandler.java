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

import org.openmrs.Obs;
import org.openmrs.User;
import org.openmrs.annotation.Handler;
import org.openmrs.aop.RequiredDataAdvice;

/**
 * Takes an obs out of {@link Obs.Status#ENTERED_IN_ERROR} when it is unvoided, along with the
 * members of a group unvoided with it. The status it had before is not kept, so the obs goes back
 * to {@link Obs.Status#FINAL}, the status a new obs is given. It runs before
 * {@link BaseUnvoidHandler}, which it mirrors in acting only on obs voided at the same time as the
 * parent.
 *
 * @see RequiredDataAdvice
 * @see org.openmrs.api.ObsService#voidObs(Obs, String)
 * @since 3.0.0
 */
@Handler(supports = Obs.class, order = 5)
public class ObsUnvoidHandler implements UnvoidHandler<Obs> {

	/**
	 * <strong>Should</strong> set an entered in error obs back to final<br/>
	 * <strong>Should</strong> not act on an obs with a different dateVoided
	 *
	 * @see org.openmrs.api.handler.RequiredDataHandler#handle(org.openmrs.OpenmrsObject,
	 *      org.openmrs.User, java.util.Date, java.lang.String)
	 */
	@Override
	public void handle(Obs obs, User voidingUser, Date origParentVoidedDate, String unused) {
		if (obs.getVoided() && Obs.Status.ENTERED_IN_ERROR.equals(obs.getStatus())
		        && (origParentVoidedDate == null || origParentVoidedDate.equals(obs.getDateVoided()))) {
			obs.setStatus(Obs.Status.FINAL);
		}
	}
}
