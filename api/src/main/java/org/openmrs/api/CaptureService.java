/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api;

import java.io.InputStream;
import java.util.List;

import org.openmrs.Capture;
import org.openmrs.Derivation;
import org.openmrs.Encounter;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Captures: the scanned forms, recordings and other artifacts encounters are captured in, and the
 * derivations, runs of an agent such as an extraction model, that took obs from them. Artifacts are
 * kept by the {@link StorageService}, never in the database. Audio and video expire after
 * {@link org.openmrs.util.OpenmrsConstants#GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS} days, and every
 * other kind is kept; the capture row always stays.
 *
 * @since 3.0.0
 */
public interface CaptureService extends OpenmrsService {

	/**
	 * Stores a new capture's artifact and saves the capture. Storage picks the artifact's key, so
	 * nothing the caller sends becomes part of a path, and the service sets the checksum and how long
	 * the artifact is kept from its kind. If the transaction rolls back, the stored artifact is
	 * deleted.
	 * <p>
	 * <strong>Should</strong> store the artifact with its checksum<br/>
	 * <strong>Should</strong> keep a scanned form and expire audio<br/>
	 * <strong>Should</strong> ignore an expiry, key or checksum the caller set<br/>
	 * <strong>Should</strong> fail for a capture that is already saved<br/>
	 * <strong>Should</strong> fail for a capture whose consent is already withdrawn<br/>
	 * <strong>Should</strong> keep the consent the caller recorded<br/>
	 * <strong>Should</strong> expire audio after the configured number of days<br/>
	 * <strong>Should</strong> fail for a media type the kind does not allow<br/>
	 * <strong>Should</strong> fail for an artifact over the size limit<br/>
	 * <strong>Should</strong> delete the artifact if the transaction rolls back
	 *
	 * @param capture a new capture, with its encounter, kind and media type, and optionally whether the
	 *            patient gave consent
	 * @param artifact the artifact's content, which the caller closes
	 * @return the saved capture
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.ADD_CAPTURES + "')")
	Capture createCapture(Capture capture, InputStream artifact);

	/**
	 * @param uuid the uuid of a capture
	 * @return the capture, or null if there is none with that uuid
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_CAPTURES + "')")
	Capture getCaptureByUuid(String uuid);

	/**
	 * @param encounter an encounter
	 * @return its captures that are not voided, oldest first
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_CAPTURES + "')")
	List<Capture> getCapturesByEncounter(Encounter encounter);

	/**
	 * Reads the artifact of a saved capture, going by what is in the database rather than the object
	 * passed in.
	 * <p>
	 * <strong>Should</strong> stream the stored artifact<br/>
	 * <strong>Should</strong> fail once the artifact is deleted<br/>
	 * <strong>Should</strong> fail for a voided capture<br/>
	 * <strong>Should</strong> read the key from the database, not the object passed in
	 *
	 * @param capture a saved capture
	 * @return its artifact's content, which the caller closes
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_CAPTURES + "')")
	InputStream getArtifact(Capture capture);

	/**
	 * Records that the patient withdrew consent. An artifact that would expire is deleted at once; one
	 * that is kept as part of the record is not.
	 * <p>
	 * <strong>Should</strong> delete an expiring artifact at once<br/>
	 * <strong>Should</strong> keep a scanned form
	 *
	 * @param capture a saved capture
	 * @return the capture, as it now is in the database
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	Capture withdrawConsent(Capture capture);

	/**
	 * <strong>Should</strong> return audio that has expired or whose consent was withdrawn<br/>
	 * <strong>Should</strong> never return a scanned form<br/>
	 * <strong>Should</strong> not return an artifact that has not expired
	 *
	 * @return the captures whose artifact expires and is still stored, and that have expired or whose
	 *         consent was withdrawn, oldest first
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	List<Capture> getCapturesWithArtifactsToDelete();

	/**
	 * Deletes a capture's artifact if, going by the database, it has expired or would expire and its
	 * consent was withdrawn; never one that is kept. The capture stays. The retention task calls this
	 * once per capture, each in its own transaction, so one failure does not stop the rest.
	 * <p>
	 * <strong>Should</strong> delete an expired audio artifact<br/>
	 * <strong>Should</strong> never delete a scanned form<br/>
	 * <strong>Should</strong> not delete an artifact that has not expired<br/>
	 * <strong>Should</strong> fail if storage still has the artifact after deleting it<br/>
	 * <strong>Should</strong> never delete a key outside capture storage
	 *
	 * @param capture a saved capture
	 * @return true if the artifact was due and so deleted
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	boolean deleteExpiredArtifact(Capture capture);

	/**
	 * Voids a saved capture; its artifact is no longer served.
	 * <p>
	 * <strong>Should</strong> void a saved capture<br/>
	 * <strong>Should</strong> fail for a capture that is not saved
	 *
	 * @param capture the capture to void
	 * @param reason why it is voided
	 * @return the voided capture
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	Capture voidCapture(Capture capture, String reason);

	/**
	 * Records an agent's finished run over a capture. The agent defaults to the authenticated user, and
	 * the dictionary version to the current one.
	 * <p>
	 * <strong>Should</strong> default the agent and dictionary version<br/>
	 * <strong>Should</strong> fail for a run that finished before it started<br/>
	 * <strong>Should</strong> fail for a voided capture<br/>
	 * <strong>Should</strong> fail for a derivation that is already saved
	 *
	 * @param derivation a new derivation, with its capture and when it started and finished
	 * @return the saved derivation
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.ADD_DERIVATIONS + "')")
	Derivation createDerivation(Derivation derivation);

	/**
	 * @param uuid the uuid of a derivation
	 * @return the derivation, or null if there is none with that uuid
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_CAPTURES + "')")
	Derivation getDerivationByUuid(String uuid);

	/**
	 * @param capture a capture
	 * @return the derivations over it that are not voided, oldest first
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_CAPTURES + "')")
	List<Derivation> getDerivationsByCapture(Capture capture);

	/**
	 * <strong>Should</strong> fail for a derivation that is not saved
	 *
	 * @param derivation the derivation to void
	 * @param reason why it is voided
	 * @return the voided derivation
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	Derivation voidDerivation(Derivation derivation, String reason);
}
