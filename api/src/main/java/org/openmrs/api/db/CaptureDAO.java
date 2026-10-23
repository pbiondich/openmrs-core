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

import java.util.Date;
import java.util.List;

import org.openmrs.Capture;
import org.openmrs.Derivation;
import org.openmrs.Encounter;
import org.openmrs.Obs;

/**
 * Database methods for {@link org.openmrs.api.CaptureService}.
 *
 * @since 3.0.0
 */
public interface CaptureDAO {

	Capture saveCapture(Capture capture);

	Capture getCaptureByUuid(String uuid);

	List<Capture> getCapturesByEncounter(Encounter encounter);

	/**
	 * @param capture a saved capture
	 * @return the capture as it is in the database, after saving any pending changes the mapping lets
	 *         through; null if it is not in the database
	 */
	Capture reload(Capture capture);

	/**
	 * @param captureId a capture
	 * @return the complex obs holding its artifact, if the capture is not voided and its artifact is
	 *         still stored; otherwise null
	 */
	Obs getReadableArtifactObs(Integer captureId);

	/**
	 * Voids the complex obs whose artifact was deleted, in the database, keeping its status.
	 */
	void voidArtifactObs(Integer obsId, String reason, Date voidedAt);

	/**
	 * @param now the time to judge expiry by
	 * @return the captures whose artifact expires and is still stored, and that have expired or whose
	 *         consent was withdrawn
	 */
	List<Capture> getCapturesWithArtifactsToDelete(Date now);

	/**
	 * Records consent as withdrawn, in the database.
	 */
	void withdrawConsent(Integer captureId);

	/**
	 * Records the artifact as deleted, in the database, unless it already is.
	 */
	void markArtifactDeleted(Integer captureId, Date deletedAt);

	Derivation saveDerivation(Derivation derivation);

	Derivation getDerivationByUuid(String uuid);

	List<Derivation> getDerivationsByCapture(Capture capture);
}
