/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.util.databasechange;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.api.CaptureServiceTest;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;

import liquibase.database.jvm.JdbcConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link ComplexObsCaptureBackfillChangeSet}.
 */
public class ComplexObsCaptureBackfillChangeSetTest extends BaseContextSensitiveTest {

	private static final String DATASET = "org/openmrs/util/databasechange/ComplexObsCaptureBackfillChangeSetTest.xml";

	@BeforeEach
	public void setUp() {
		executeDataSet(CaptureServiceTest.DATASET);
		executeDataSet(DATASET);
	}

	@Test
	public void backfill_shouldGiveEachFittingComplexObsOfAnEncounterACapture() throws Exception {
		ComplexObsCaptureBackfillChangeSet changeSet = new ComplexObsCaptureBackfillChangeSet();

		changeSet.backfill(new JdbcConnection(getConnection()));

		Map<Integer, Capture> byObs = captures();
		assertEquals(List.of(9001, 9002, 9003), List.copyOf(byObs.keySet()));
		assertEquals(Capture.Kind.SCANNED_FORM, byObs.get(9001).getKind());
		assertEquals("application/pdf", byObs.get(9001).getMimeType());
		assertEquals(Capture.Kind.IMAGE, byObs.get(9002).getKind());
		assertEquals("image/jpeg", byObs.get(9002).getMimeType());
		assertEquals(Capture.Kind.AUDIO, byObs.get(9003).getKind());
		assertEquals("Backfilled 3 captures from complex obs; skipped 1 whose file type fits no capture kind",
		    changeSet.getConfirmationMessage());
	}

	@Test
	public void backfill_shouldKeepEveryBackfilledArtifactWithNoChecksumAndConsentNotRecorded() throws Exception {
		new ComplexObsCaptureBackfillChangeSet().backfill(new JdbcConnection(getConnection()));

		for (Capture capture : captures().values()) {
			assertNull(capture.getExpiresAt());
			assertNull(capture.getSha256());
			assertEquals(Capture.ConsentStatus.NOT_RECORDED, capture.getConsentStatus());
			assertEquals(capture.getObs().getEncounter(), capture.getEncounter());
		}
	}

	@Test
	public void backfill_shouldNotGiveAComplexObsASecondCapture() throws Exception {
		new ComplexObsCaptureBackfillChangeSet().backfill(new JdbcConnection(getConnection()));
		ComplexObsCaptureBackfillChangeSet again = new ComplexObsCaptureBackfillChangeSet();

		again.backfill(new JdbcConnection(getConnection()));

		assertEquals(3, captures().size());
		assertEquals("Backfilled 0 captures from complex obs; skipped 1 whose file type fits no capture kind",
		    again.getConfirmationMessage());
	}

	@Test
	public void mediaType_shouldReadTheFilesOwnExtensionBeforeTheTitles() {
		assertEquals("application/pdf", ComplexObsCaptureBackfillChangeSet.mediaType("notes.txt|complex_obs/x-scan.pdf"));
		assertEquals("audio/mpeg", ComplexObsCaptureBackfillChangeSet.mediaType("visit.mp3 file |complex_obs/abc"));
		assertNull(ComplexObsCaptureBackfillChangeSet.mediaType("letter|letter.docx"));
		assertNull(ComplexObsCaptureBackfillChangeSet.mediaType("no extension at all"));
	}

	private Map<Integer, Capture> captures() {
		return Context.getCaptureService().getCapturesByEncounter(Context.getEncounterService().getEncounter(3)).stream()
		        .collect(Collectors.toMap(c -> c.getObs().getObsId(), c -> c, (a, b) -> a, java.util.TreeMap::new));
	}
}
