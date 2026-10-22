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

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.ObsAttestationItem.Outcome;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.ProviderType;
import org.openmrs.SoftwareAgent;
import org.openmrs.User;
import org.openmrs.UserType;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AttestationService}.
 */
public class AttestationServiceTest extends BaseContextSensitiveTest {

	private AttestationService attestationService;

	private Encounter encounter;

	private Provider extractor;

	/** The admin user's own provider, a person. */
	private Provider nurse;

	@BeforeEach
	public void setUp() {
		attestationService = Context.getAttestationService();
		Context.getObsService().saveObsFinalizationPolicy(new ObsFinalizationPolicy("software needs attestation",
		        UserType.SOFTWARE, null, ObsFinalizationPolicy.Action.REQUIRE_ATTESTATION));
		encounter = Context.getEncounterService().getEncounter(3);
		extractor = newDigitalProvider("extractor", "0.1");
		nurse = Context.getProviderService().getProvider(1);
	}

	@Test
	public void attest_shouldMakeAnAcceptedObsFinalOnTheSameRow() {
		Obs obs = preliminaryObs(50d);

		ObsAttestation attestation = attest(new ObsAttestationItem(obs, Outcome.ACCEPTED));

		Obs current = reload(obs);
		assertEquals(Obs.Status.FINAL, current.getStatus());
		assertFalse(current.getVoided());
		assertNotNull(attestationService.getObsAttestationByUuid(attestation.getUuid()));
		assertEquals(Outcome.ACCEPTED, attestationService.getObsAttestationItems(current).get(0).getOutcome());
	}

	@Test
	public void attest_shouldMakeThePreliminaryMembersOfAnAcceptedGroupFinal() {
		Obs parent = newObs(null);
		parent.setConcept(Context.getConceptService().getConcept(23));
		Obs member = newObs(50d);
		parent.addGroupMember(member);
		parent.setAsserter(extractor);
		Context.getObsService().saveObs(parent, null);
		parent = reload(parent);
		assertEquals(Obs.Status.PRELIMINARY, member.getStatus());

		attest(new ObsAttestationItem(parent, Outcome.ACCEPTED));

		assertEquals(Obs.Status.FINAL, reload(parent).getGroupMembers().iterator().next().getStatus());
	}

	@Test
	public void attest_shouldReplaceAnAmendedObsWithAFinalVersionTheAttesterAsserts() {
		Obs obs = preliminaryObs(50d);
		ObsAttestationItem item = new ObsAttestationItem(obs, Outcome.AMENDED);
		Obs correction = new Obs();
		correction.setValueNumeric(55d);
		item.setSupersedingObs(correction);

		attest(item);

		Obs replaced = reload(obs);
		assertTrue(replaced.getVoided());
		assertEquals(Obs.Status.PRELIMINARY, replaced.getStatus());
		Obs amended = Context.getObsService().getRevisionObs(replaced);
		assertEquals(55d, amended.getValueNumeric());
		assertEquals(Obs.Status.FINAL, amended.getStatus());
		assertEquals(nurse, amended.getAsserter());
		assertEquals(amended, attestationService.getObsAttestationItems(replaced).get(0).getSupersedingObs());
	}

	@Test
	public void attest_shouldLeaveARejectedObsPreliminaryAndNoLongerPending() {
		Obs rejected = preliminaryObs(50d);
		Obs deferred = preliminaryObs(51d);
		Patient patient = encounter.getPatient();

		attest(new ObsAttestationItem(rejected, Outcome.REJECTED),
		    new ObsAttestationItem(deferred, Outcome.LEFT_PRELIMINARY));

		assertEquals(Obs.Status.PRELIMINARY, reload(rejected).getStatus());
		assertEquals(Outcome.REJECTED, attestationService.getObsAttestationItems(rejected).get(0).getOutcome());
		List<Obs> pending = attestationService
		        .getPendingForPatient(Context.getPatientService().getPatient(patient.getPatientId()));
		assertFalse(pending.contains(rejected));
		assertTrue(pending.contains(deferred));
	}

	@Test
	public void attest_shouldAcceptAnObsSavedInTheSameSessionWithAReferenceRange() {
		Obs obs = newObs(70d);
		obs.setConcept(Context.getConceptService().getConcept(5089));
		obs.setAsserter(extractor);
		Context.getObsService().saveObs(obs, null);

		attest(new ObsAttestationItem(obs, Outcome.ACCEPTED));

		assertEquals(Obs.Status.FINAL, reload(obs).getStatus());
	}

	@Test
	public void attest_shouldFailForACorrectionWithMembersOfItsOwn() {
		Obs obs = preliminaryObs(50d);
		ObsAttestationItem item = new ObsAttestationItem(obs, Outcome.AMENDED);
		Obs correction = new Obs();
		correction.setValueNumeric(55d);
		Obs smuggled = newObs(60d);
		smuggled.setStatus(Obs.Status.FINAL);
		correction.addGroupMember(smuggled);
		item.setSupersedingObs(correction);

		assertThrows(APIException.class, () -> attest(item));
	}

	@Test
	public void attest_shouldFailForAnAttesterWhoIsNotAPerson() {
		Obs obs = preliminaryObs(50d);

		assertThrows(APIException.class,
		    () -> attestationService.attest(encounter, extractor, List.of(new ObsAttestationItem(obs, Outcome.ACCEPTED))));
	}

	@Test
	public void attest_shouldFailForAnAttesterWhoIsNotTheAuthenticatedUser() {
		Obs obs = preliminaryObs(50d);
		Provider someoneElse = new Provider();
		someoneElse.setPerson(Context.getPersonService().getPerson(2));
		someoneElse.setIdentifier("someone-else");
		someoneElse.setProviderType(ProviderType.BIOLOGIC);
		Context.getProviderService().saveProvider(someoneElse);

		assertThrows(APIException.class,
		    () -> attestationService.attest(encounter, someoneElse, List.of(new ObsAttestationItem(obs, Outcome.ACCEPTED))));
		assertEquals(Obs.Status.PRELIMINARY, reload(obs).getStatus());
	}

	@Test
	public void attest_shouldFailForAnObsThatIsNotPreliminary() {
		Obs obs = newObs(50d);
		Context.getObsService().saveObs(obs, null);

		assertThrows(APIException.class, () -> attest(new ObsAttestationItem(reload(obs), Outcome.ACCEPTED)));
	}

	@Test
	public void attest_shouldFailForAnObsFromAnotherEncounter() {
		Obs obs = preliminaryObs(50d);

		assertThrows(APIException.class, () -> attestationService.attest(Context.getEncounterService().getEncounter(4),
		    nurse, List.of(new ObsAttestationItem(obs, Outcome.ACCEPTED))));
	}

	@Test
	public void attest_shouldFailWithoutTheAttestObsPrivilege() {
		Obs obs = preliminaryObs(50d);
		Context.logout();

		assertThrows(AccessDeniedException.class,
		    () -> attestationService.attest(encounter, nurse, List.of(new ObsAttestationItem(obs, Outcome.ACCEPTED))));
		authenticate();
	}

	@Test
	public void attest_shouldJudgeEachObsAsItIsSavedNotAsTheCallerPassedIt() {
		Obs obs = newObs(50d);
		Context.getObsService().saveObs(obs, null);
		Obs forged = new Obs(obs.getObsId());
		forged.setStatus(Obs.Status.PRELIMINARY);
		forged.setEncounter(encounter);

		assertThrows(APIException.class, () -> attest(new ObsAttestationItem(forged, Outcome.ACCEPTED)));
		assertEquals(Obs.Status.FINAL, reload(obs).getStatus());
	}

	@Test
	public void attest_shouldLeaveAMemberWithAnItemOfItsOwnToThatItem() {
		Obs parent = newObs(null);
		parent.setConcept(Context.getConceptService().getConcept(23));
		Obs kept = newObs(50d);
		Obs rejected = newObs(51d);
		parent.addGroupMember(kept);
		parent.addGroupMember(rejected);
		parent.setAsserter(extractor);
		Context.getObsService().saveObs(parent, null);
		parent = reload(parent);

		attest(new ObsAttestationItem(parent, Outcome.ACCEPTED), new ObsAttestationItem(reload(rejected), Outcome.REJECTED));

		assertEquals(Obs.Status.FINAL, reload(kept).getStatus());
		assertEquals(Obs.Status.PRELIMINARY, reload(rejected).getStatus());
	}

	@Test
	public void getPendingForPatient_shouldIncludeAnObsWhoseLatestAttestationNoLongerRejectsIt() {
		Obs obs = preliminaryObs(50d);
		attest(new ObsAttestationItem(obs, Outcome.REJECTED));
		assertFalse(pending().contains(reload(obs)));

		attest(new ObsAttestationItem(reload(obs), Outcome.LEFT_PRELIMINARY));

		assertTrue(pending().contains(reload(obs)));
	}

	@Test
	public void getPendingForPatient_shouldLeaveOutTheMembersOfARejectedGroup() {
		Obs parent = newObs(null);
		parent.setConcept(Context.getConceptService().getConcept(23));
		Obs member = newObs(50d);
		parent.addGroupMember(member);
		parent.setAsserter(extractor);
		Context.getObsService().saveObs(parent, null);

		attest(new ObsAttestationItem(reload(parent), Outcome.REJECTED));

		assertFalse(pending().contains(reload(member)));
	}

	private List<Obs> pending() {
		return attestationService
		        .getPendingForPatient(Context.getPatientService().getPatient(encounter.getPatient().getPatientId()));
	}

	private ObsAttestation attest(ObsAttestationItem... items) {
		return attestationService.attest(encounter, nurse, List.of(items));
	}

	private Obs preliminaryObs(Double value) {
		Obs obs = newObs(value);
		obs.setAsserter(extractor);
		Context.getObsService().saveObs(obs, null);
		obs = reload(obs);
		assertEquals(Obs.Status.PRELIMINARY, obs.getStatus());
		return obs;
	}

	private Obs newObs(Double value) {
		Obs obs = new Obs();
		obs.setConcept(Context.getConceptService().getConcept(3));
		obs.setPerson(encounter.getPatient());
		obs.setEncounter(encounter);
		obs.setObsDatetime(new Date());
		obs.setLocation(new Location(1));
		obs.setValueNumeric(value);
		return obs;
	}

	private Obs reload(Obs obs) {
		Context.flushSession();
		Context.clearSession();
		encounter = Context.getEncounterService().getEncounter(3);
		nurse = Context.getProviderService().getProvider(1);
		extractor = Context.getProviderService().getProvider(extractor.getProviderId());
		return Context.getObsService().getObs(obs.getObsId());
	}

	private Provider newDigitalProvider(String name, String version) {
		User user = new User();
		user.setUsername(name + "-" + version);
		Context.getUserService().createSoftwareAgent(new SoftwareAgent(user, name, version), "Openmr5xy");
		Provider provider = new Provider();
		provider.setProviderType(ProviderType.DIGITAL);
		provider.setUser(user);
		return Context.getProviderService().saveProvider(provider);
	}
}
