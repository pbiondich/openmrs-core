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

import java.util.List;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Attestation: a person reviewing the preliminary obs that, under their finalization policy, wait
 * for one, and deciding what becomes of each.
 *
 * @since 3.0.0
 */
public interface AttestationService extends OpenmrsService {

	/**
	 * Records a person's attestation of preliminary obs and carries out each outcome: an accepted obs
	 * becomes final on the same row, together with the preliminary members of its group; an amended obs
	 * is replaced by the item's superseding obs, saved final with the attester as its asserter; a
	 * rejected obs, or one left preliminary, is unchanged, the item being the only record.
	 * <p>
	 * <strong>Should</strong> make an accepted obs final on the same row<br/>
	 * <strong>Should</strong> make the preliminary members of an accepted group final<br/>
	 * <strong>Should</strong> replace an amended obs with a final version the attester asserts<br/>
	 * <strong>Should</strong> leave a rejected obs preliminary<br/>
	 * <strong>Should</strong> fail for an attester who is not a person<br/>
	 * <strong>Should</strong> fail for an attester who is not the authenticated user<br/>
	 * <strong>Should</strong> fail for an obs that is not preliminary<br/>
	 * <strong>Should</strong> fail for an obs from another encounter<br/>
	 * <strong>Should</strong> judge each obs as it is saved, not as the caller passed it<br/>
	 * <strong>Should</strong> leave a member with an item of its own to that item<br/>
	 * <strong>Should</strong> fail without the Attest Obs privilege
	 *
	 * @param encounter the encounter the obs belong to, or null for obs outside an encounter
	 * @param attester the provider attesting: a person, and the authenticated user's own provider
	 * @param items an outcome for each obs; an amended obs's item carries its correction as a new obs
	 *            in {@link ObsAttestationItem#getSupersedingObs()}
	 * @return the saved attestation
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.ATTEST_OBS + "')")
	ObsAttestation attest(Encounter encounter, Provider attester, List<ObsAttestationItem> items);

	/**
	 * @param uuid the uuid of an attestation
	 * @return the attestation, or null if there is none with that uuid
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_OBS + "')")
	ObsAttestation getObsAttestationByUuid(String uuid);

	/**
	 * @param obs an obs
	 * @return what attestations made of it, oldest first
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_OBS + "')")
	List<ObsAttestationItem> getObsAttestationItems(Obs obs);

	/**
	 * The obs about a patient that wait for attestation: preliminary, not voided, and not rejected by
	 * the latest attestation of the obs or of its group. A rejected obs can still be attested again.
	 * <p>
	 * <strong>Should</strong> return preliminary obs that no attestation rejected<br/>
	 * <strong>Should</strong> include obs left preliminary<br/>
	 * <strong>Should</strong> include an obs whose latest attestation no longer rejects it<br/>
	 * <strong>Should</strong> leave out the members of a rejected group
	 *
	 * @param patient a patient
	 * @return the obs, oldest first
	 * @throws org.springframework.security.access.AccessDeniedException if the current user lacks
	 *             permission
	 */
	@PreAuthorize("hasAuthority('" + PrivilegeConstants.GET_OBS + "')")
	List<Obs> getPendingForPatient(Patient patient);
}
