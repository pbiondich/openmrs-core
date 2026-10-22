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
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
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

	@Test
	public void getCurrentDictionaryVersion_shouldReturnTheVersionTheGlobalPropertyNames() {
		DictionaryVersion version = makeCurrent(newVersion("CIEL 2026-09"));

		assertEquals(version, Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void getCurrentDictionaryVersion_shouldReturnNullWhenTheGlobalPropertyNamesNone() {
		newVersion("CIEL 2026-09");

		assertNull(Context.getConceptService().getCurrentDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldRecordTheCurrentDictionaryVersionOnANewEncounter() {
		DictionaryVersion version = makeCurrent(newVersion("CIEL 2026-09"));
		Encounter encounter = newEncounter();

		Context.getEncounterService().saveEncounter(encounter);

		assertEquals(version, encounter.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldNotStampAnEncounterEnteredBeforeVersionsWereRecorded() {
		makeCurrent(newVersion("CIEL 2026-09"));
		Encounter existing = Context.getEncounterService().getEncounter(3);

		existing.setEncounterDatetime(new Date(existing.getEncounterDatetime().getTime() + 1000));
		Context.getEncounterService().saveEncounter(existing);

		assertNull(existing.getDictionaryVersion());
	}

	@Test
	public void saveEncounter_shouldKeepAVersionTheCallerSet() {
		makeCurrent(newVersion("CIEL 2026-09"));
		DictionaryVersion older = newVersion("CIEL 2026-03");
		Encounter encounter = newEncounter();
		encounter.setDictionaryVersion(older);

		Context.getEncounterService().saveEncounter(encounter);

		assertEquals(older, encounter.getDictionaryVersion());
	}

	@Test
	public void validate_shouldFailIfAnotherVersionHasTheSameLabel() {
		newVersion("CIEL 2026-09");
		DictionaryVersion duplicate = new DictionaryVersion("ciel 2026-09");
		Errors errors = new BindException(duplicate, "version");

		new DictionaryVersionValidator().validate(duplicate, errors);

		assertTrue(errors.hasFieldErrors("name"));
	}

	private DictionaryVersion newVersion(String label) {
		DictionaryVersion version = new DictionaryVersion(label);
		version.setCielRelease("v2026-09-01");
		return Context.getConceptService().saveDictionaryVersion(version);
	}

	private DictionaryVersion makeCurrent(DictionaryVersion version) {
		Context.getAdministrationService().saveGlobalProperty(
		    new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_DICTIONARY_VERSION, version.getUuid()));
		return version;
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
