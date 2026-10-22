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

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import org.openmrs.UserType;
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
		// judge the obs as they are saved, not as the caller passed them
		Set<Integer> itemObsIds = new HashSet<>();
		Integer personId = null;
		for (ObsAttestationItem item : items) {
			Obs obs = reload(item);
			checkItem(item, encounter, itemObsIds);
			if (personId != null && !personId.equals(obs.getPerson().getPersonId())) {
				throw new APIException("ObsAttestation.error.otherPatient", new Object[] { obs.getUuid() });
			}
			personId = obs.getPerson().getPersonId();
		}

		Set<Integer> accepted = new LinkedHashSet<>();
		for (ObsAttestationItem item : items) {
			if (item.getOutcome() == ObsAttestationItem.Outcome.ACCEPTED) {
				accepted.add(item.getObs().getObsId());
				// an accepted group's preliminary members are accepted with it, unless they have an item of their own
				collectPreliminaryMembers(item.getObs().getObsId(), itemObsIds, accepted);
			} else if (item.getOutcome() == ObsAttestationItem.Outcome.AMENDED) {
				amend(item, attester);
			}
		}
		if (!accepted.isEmpty()) {
			if (dao.finalizeObs(accepted) != accepted.size()) {
				// someone else changed one of the obs since it was read
				throw new APIException("ObsAttestation.error.changedMeanwhile", (Object[]) null);
			}
			for (ObsAttestationItem item : items) {
				Context.refreshEntity(item.getObs());
			}
		}

		ObsAttestation attestation = new ObsAttestation();
		attestation.setEncounter(items.get(0).getObs().getEncounter());
		attestation.setAttester(attester);
		attestation.setAttestedAt(new Date());
		items.forEach(attestation::addItem);
		return dao.saveObsAttestation(attestation);
	}

	/**
	 * A person attests, and only as themselves: the authenticated user must be a person, and the
	 * attester a person's provider, not retired, that is the user's own.
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
		Person person = user != null && user.getUserType() == UserType.HUMAN ? user.getPerson() : null;
		if (person == null || attester.getPerson() == null
		        || !OpenmrsUtil.nullSafeEquals(person.getPersonId(), attester.getPerson().getPersonId())) {
			throw new APIException("ObsAttestation.error.attesterNotYou", (Object[]) null);
		}
		return attester;
	}

	/**
	 * Replaces the item's obs with the obs as it is saved, dropping any changes the caller made to it.
	 */
	private Obs reload(ObsAttestationItem item) {
		Obs obs = item.getObs();
		if (obs == null || obs.getObsId() == null) {
			throw new APIException("ObsAttestation.error.invalidItem", (Object[]) null);
		}
		// write out anything pending first: evicting an obs cascades to its reference range, which may
		// still be waiting to be inserted
		Context.flushSession();
		Context.evictFromSession(obs);
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_OBS);
			obs = Context.getObsService().getObs(obs.getObsId());
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_OBS);
		}
		if (obs == null) {
			throw new APIException("ObsAttestation.error.invalidItem", (Object[]) null);
		}
		item.setObs(obs);
		return obs;
	}

	private void checkItem(ObsAttestationItem item, Encounter encounter, Set<Integer> seen) {
		Obs obs = item.getObs();
		if (item.getOutcome() == null || !seen.add(obs.getObsId())) {
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

	private void collectPreliminaryMembers(Integer groupId, Set<Integer> itemObsIds, Set<Integer> accepted) {
		for (Object[] member : dao.getMembers(groupId)) {
			Integer memberId = (Integer) member[0];
			if (itemObsIds.contains(memberId)) {
				continue;
			}
			if (member[1] == Obs.Status.PRELIMINARY) {
				accepted.add(memberId);
			}
			collectPreliminaryMembers(memberId, itemObsIds, accepted);
		}
	}

	/**
	 * The correction becomes a new version of the obs, final and asserted by the attester, who now
	 * makes the claim, in the obs's place for its patient, encounter and group; the original is voided
	 * as replaced, which leaves its status as it was. Attesting is the privilege this takes.
	 */
	private void amend(ObsAttestationItem item, Provider attester) {
		Obs original = item.getObs();
		Obs correction = item.getSupersedingObs();
		correction.setPreviousVersion(original);
		correction.setPerson(original.getPerson());
		correction.setEncounter(original.getEncounter());
		correction.setObsGroup(original.getObsGroup());
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
		try {
			Context.addProxyPrivilege(PrivilegeConstants.ADD_OBS);
			Context.addProxyPrivilege(PrivilegeConstants.EDIT_OBS);
			Context.addProxyPrivilege(PrivilegeConstants.DELETE_OBS);
			Context.getObsService().saveObs(correction, null);
			Context.getObsService().voidObs(original, "Amended in attestation");
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.DELETE_OBS);
			Context.removeProxyPrivilege(PrivilegeConstants.EDIT_OBS);
			Context.removeProxyPrivilege(PrivilegeConstants.ADD_OBS);
		}
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
