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

import org.junit.jupiter.api.Test;
import org.openmrs.DictionaryVersion;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.openmrs.validator.DictionaryVersionValidator;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for dictionary versions, and the version a new encounter records.
 */
public class DictionaryVersionTest extends BaseContextSensitiveTest {

	private static final long DAY = 24L * 60 * 60 * 1000;

	@Test
	public void getCurrentDictionaryVersion_shouldReturnTheNewestVersionInUse() {
		newVersion("CIEL 2026-03", daysAgo(200));
		DictionaryVersion newest = newVersion("CIEL 2026-09", daysAgo(10));

		assertEquals(newest, Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void getCurrentDictionaryVersion_shouldNotReturnAVersionWhoseTimeHasNotCome() {
		DictionaryVersion inUse = newVersion("CIEL 2026-09", daysAgo(10));
		newVersion("CIEL 2027-03", daysAgo(-30));

		assertEquals(inUse, Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void getCurrentDictionaryVersion_shouldNotReturnARetiredVersion() {
		DictionaryVersion older = newVersion("CIEL 2026-03", daysAgo(200));
		DictionaryVersion newest = newVersion("CIEL 2026-09", daysAgo(10));

		Context.getConceptService().retireDictionaryVersion(newest, "withdrawn");

		assertEquals(older, Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void getCurrentDictionaryVersion_shouldReturnNullWhenNoVersionIsInUse() {
		newVersion("CIEL 2027-03", daysAgo(-30));

		assertNull(Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void getDictionaryVersionByLabel_shouldIgnoreCase() {
		DictionaryVersion version = newVersion("CIEL 2026-09", daysAgo(10));

		assertEquals(version, Context.getConceptService().getDictionaryVersionByLabel("ciel 2026-09"));
	}

	@Test
	public void saveEncounter_shouldRecordTheCurrentDictionaryVersionOnANewEncounter() {
		DictionaryVersion version = newVersion("CIEL 2026-09", daysAgo(10));
		Encounter encounter = newEncounter();

		Context.getEncounterService().saveEncounter(encounter);

		assertEquals(version, encounter.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldNotStampAnEncounterEnteredBeforeVersionsWereRecorded() {
		newVersion("CIEL 2026-09", daysAgo(10));
		Encounter existing = Context.getEncounterService().getEncounter(3);

		existing.setEncounterDatetime(new Date(existing.getEncounterDatetime().getTime() + 1000));
		Context.getEncounterService().saveEncounter(existing);

		assertNull(existing.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldNotRecordAVersionOnACopyOfAnEarlierEncounter() {
		newVersion("CIEL 2026-09", daysAgo(10));
		Encounter earlier = Context.getEncounterService().getEncounter(3);

		Encounter copy = Context.getEncounterService().transferEncounter(earlier, Context.getPatientService().getPatient(2));

		assertNull(copy.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldKeepTheVersionOfTheEncounterACopyIsMadeFrom() {
		DictionaryVersion older = newVersion("CIEL 2026-03", daysAgo(200));
		newVersion("CIEL 2026-09", daysAgo(10));
		Encounter original = newEncounter();
		original.setDictionaryVersion(older);
		Context.getEncounterService().saveEncounter(original);

		Encounter copy = Context.getEncounterService().transferEncounter(original,
		    Context.getPatientService().getPatient(2));

		assertEquals(older, copy.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldKeepAVersionTheCallerSet() {
		newVersion("CIEL 2026-09", daysAgo(10));
		DictionaryVersion older = newVersion("CIEL 2026-03", daysAgo(200));
		Encounter encounter = newEncounter();
		encounter.setDictionaryVersion(older);

		Context.getEncounterService().saveEncounter(encounter);

		assertEquals(older, encounter.getDictionaryVersion());
	}

	@Test
	public void validate_shouldFailIfAnotherVersionHasTheSameLabel() {
		newVersion("CIEL 2026-09", daysAgo(10));
		DictionaryVersion duplicate = new DictionaryVersion("ciel 2026-09", new Date());

		assertTrue(validate(duplicate).hasFieldErrors("name"));
	}

	@Test
	public void validate_shouldFailIfTheLabelOrInUseFromTimeIsMissing() {
		Errors errors = validate(new DictionaryVersion());

		assertTrue(errors.hasFieldErrors("name"));
		assertTrue(errors.hasFieldErrors("inUseFrom"));
	}

	private Errors validate(DictionaryVersion version) {
		Errors errors = new BindException(version, "version");
		new DictionaryVersionValidator().validate(version, errors);
		return errors;
	}

	private DictionaryVersion newVersion(String label, Date inUseFrom) {
		DictionaryVersion version = new DictionaryVersion(label, inUseFrom);
		version.setCielRelease(label.substring(5));
		return Context.getConceptService().saveDictionaryVersion(version);
	}

	private Date daysAgo(int days) {
		return new Date(System.currentTimeMillis() - days * DAY);
	}

	private Encounter newEncounter() {
		Encounter encounter = new Encounter();
		encounter.setPatient(Context.getPatientService().getPatient(7));
		encounter.setEncounterType(Context.getEncounterService().getEncounterType(1));
		encounter.setLocation(Context.getLocationService().getLocation(1));
		encounter.setEncounterDatetime(new Date());
		return encounter;
	}
}
