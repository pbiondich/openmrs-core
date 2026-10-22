/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.ProviderType;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.AttestationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.AttestationDAO;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AttestationService}.
 *
 * @since 3.0.0
 */
@Service("attestationService")
@Transactional
public class AttestationServiceImpl extends BaseOpenmrsService implements AttestationService {

	@Autowired
	private AttestationDAO dao;

	/**
	 * @see AttestationService#attest(Encounter, Provider, List)
	 */
	@Override
	public ObsAttestation attest(Encounter encounter, Provider attester, List<ObsAttestationItem> items) {
		attester = checkAttester(attester);
		if (items == null || items.isEmpty()) {
			throw new APIException("ObsAttestation.error.noItems", (Object[]) null);
		}
		Set<Integer> seen = new HashSet<>();
		for (ObsAttestationItem item : items) {
			checkItem(item, encounter, seen);
		}

		List<Obs> accepted = new ArrayList<>();
		for (ObsAttestationItem item : items) {
			if (item.getOutcome() == ObsAttestationItem.Outcome.ACCEPTED) {
				collectPreliminary(item.getObs(), accepted);
			} else if (item.getOutcome() == ObsAttestationItem.Outcome.AMENDED) {
				amend(item, attester);
			}
		}
		if (!accepted.isEmpty()) {
			List<Integer> ids = new ArrayList<>();
			accepted.forEach(obs -> ids.add(obs.getObsId()));
			if (dao.finalizeObs(ids) != ids.size()) {
				// someone else changed one of the obs since it was read
				throw new APIException("ObsAttestation.error.changedMeanwhile", (Object[]) null);
			}
			accepted.forEach(Context::refreshEntity);
		}

		ObsAttestation attestation = new ObsAttestation();
		attestation.setEncounter(encounter);
		attestation.setAttester(attester);
		attestation.setAttestedAt(new Date());
		items.forEach(attestation::addItem);
		return dao.saveObsAttestation(attestation);
	}

	/**
	 * A person attests, and only as themselves: the attester must be a person's provider, not retired,
	 * and the authenticated user's own.
	 */
	private Provider checkAttester(Provider attester) {
		if (attester == null) {
			throw new APIException("ObsAttestation.error.attesterRequired", (Object[]) null);
		}
		if (attester.getProviderId() != null) {
			try {
				Context.addProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
				attester = Context.getProviderService().getProvider(attester.getProviderId());
			} finally {
				Context.removeProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
			}
		}
		if (attester == null || attester.getProviderType() != ProviderType.BIOLOGIC || attester.getRetired()) {
			throw new APIException("ObsAttestation.error.attesterNotAPerson", (Object[]) null);
		}
		User user = Context.getAuthenticatedUser();
		Person person = user != null ? user.getPerson() : null;
		if (person == null || attester.getPerson() == null
		        || !OpenmrsUtil.nullSafeEquals(person.getPersonId(), attester.getPerson().getPersonId())) {
			throw new APIException("ObsAttestation.error.attesterNotYou", (Object[]) null);
		}
		return attester;
	}

	private void checkItem(ObsAttestationItem item, Encounter encounter, Set<Integer> seen) {
		Obs obs = item.getObs();
		if (item.getOutcome() == null || obs == null || obs.getObsId() == null || !seen.add(obs.getObsId())) {
			throw new APIException("ObsAttestation.error.invalidItem", (Object[]) null);
		}
		if (obs.getVoided() || obs.getStatus() != Obs.Status.PRELIMINARY) {
			throw new APIException("ObsAttestation.error.notPreliminary", new Object[] { obs.getUuid() });
		}
		Integer encounterId = encounter != null ? encounter.getEncounterId() : null;
		Integer obsEncounterId = obs.getEncounter() != null ? obs.getEncounter().getEncounterId() : null;
		if (!OpenmrsUtil.nullSafeEquals(encounterId, obsEncounterId)) {
			throw new APIException("ObsAttestation.error.otherEncounter", new Object[] { obs.getUuid() });
		}
		boolean amended = item.getOutcome() == ObsAttestationItem.Outcome.AMENDED;
		if (amended != (item.getSupersedingObs() != null)
		        || (amended && (item.getSupersedingObs().getObsId() != null || obs.isObsGrouping()))) {
			// an amendment carries one new obs, replacing a single obs; nothing else carries one
			throw new APIException("ObsAttestation.error.invalidAmendment", new Object[] { obs.getUuid() });
		}
	}

	private void collectPreliminary(Obs obs, List<Obs> accepted) {
		if (!obs.getVoided() && obs.getStatus() == Obs.Status.PRELIMINARY) {
			accepted.add(obs);
		}
		if (obs.hasGroupMembers()) {
			for (Obs member : obs.getGroupMembers()) {
				collectPreliminary(member, accepted);
			}
		}
	}

	/**
	 * The correction becomes a new version of the obs, final and asserted by the attester, who now
	 * makes the claim; the original is voided as replaced, which leaves its status as it was.
	 */
	private void amend(ObsAttestationItem item, Provider attester) {
		Obs original = item.getObs();
		Obs correction = item.getSupersedingObs();
		correction.setPreviousVersion(original);
		correction.setPerson(original.getPerson());
		correction.setEncounter(original.getEncounter());
		if (correction.getConcept() == null) {
			correction.setConcept(original.getConcept());
		}
		if (correction.getObsDatetime() == null) {
			correction.setObsDatetime(original.getObsDatetime());
		}
		if (correction.getLocation() == null) {
			correction.setLocation(original.getLocation());
		}
		correction.setAsserter(attester);
		correction.setStatus(Obs.Status.FINAL);
		Context.getObsService().saveObs(correction, null);
		Context.getObsService().voidObs(original, "Amended in attestation");
	}

	/**
	 * @see AttestationService#getObsAttestationByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public ObsAttestation getObsAttestationByUuid(String uuid) {
		return dao.getObsAttestationByUuid(uuid);
	}

	/**
	 * @see AttestationService#getObsAttestationItems(Obs)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<ObsAttestationItem> getObsAttestationItems(Obs obs) {
		return dao.getObsAttestationItems(obs);
	}

	/**
	 * @see AttestationService#getPendingForPatient(Patient)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getPendingForPatient(Patient patient) {
		return dao.getPendingObs(patient);
	}
}
