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
import org.openmrs.Encounter;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Captures: the scanned forms, recordings and other artifacts encounters are captured in. Artifacts
 * are files in capture storage, under the global property
 * {@link org.openmrs.util.OpenmrsConstants#GLOBAL_PROPERTY_CAPTURE_DIR}; audio and video expire
 * after {@link org.openmrs.util.OpenmrsConstants#GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS} days, and
 * every other kind is kept.
 *
 * @since 3.0.0
 */
public interface CaptureService extends OpenmrsService {

	/**
	 * Stores a new capture's artifact and saves the capture. The service names the file itself, so
	 * nothing the caller sends becomes part of a path, and sets how long it is kept from its kind.
	 * <p>
	 * <strong>Should</strong> store the artifact in capture storage<br/>
	 * <strong>Should</strong> keep a scanned form and expire audio<br/>
	 * <strong>Should</strong> fail for a capture that is already saved
	 *
	 * @param capture a new capture, with its encounter, kind and media type
	 * @param artifact the artifact's content
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
	 * <strong>Should</strong> stream the stored artifact<br/>
	 * <strong>Should</strong> fail once the artifact is deleted<br/>
	 * <strong>Should</strong> fail for an artifact uri outside capture storage
	 *
	 * @param capture a capture
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
	 * @param capture a capture
	 * @return the capture
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	Capture withdrawConsent(Capture capture);

	/**
	 * Deletes the artifacts that have expired, keeping their captures; never one that is kept.
	 * <p>
	 * <strong>Should</strong> delete an expired audio artifact<br/>
	 * <strong>Should</strong> never delete a scanned form<br/>
	 * <strong>Should</strong> not delete an artifact that has not expired
	 *
	 * @return how many artifacts were deleted
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	int deleteExpiredArtifacts();

	/**
	 * @param capture the capture to void
	 * @param reason why it is voided
	 * @return the voided capture
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.EDIT_CAPTURES + "')")
	Capture voidCapture(Capture capture, String reason);
}
