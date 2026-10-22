/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db;

import java.util.Collection;
import java.util.List;

import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.Person;

/**
 * Database methods for {@link org.openmrs.api.AttestationService}.
 *
 * @since 3.0.0
 */
public interface AttestationDAO {

	ObsAttestation saveObsAttestation(ObsAttestation attestation);

	ObsAttestation getObsAttestationByUuid(String uuid);

	List<ObsAttestationItem> getObsAttestationItems(Obs obs);

	List<Obs> getPendingObs(Person person);

	/**
	 * Makes preliminary obs final on their own rows, in one statement that leaves out any obs that is
	 * no longer preliminary or is voided.
	 *
	 * @param obsIds the obs
	 * @return how many obs became final
	 */
	int finalizeObs(Collection<Integer> obsIds);
}
