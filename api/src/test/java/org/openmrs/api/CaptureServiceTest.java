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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CaptureService}.
 */
public class CaptureServiceTest extends BaseContextSensitiveTest {

	private static final byte[] CONTENT = "a scanned IMCI sheet".getBytes(StandardCharsets.UTF_8);

	private CaptureService captureService;

	private Encounter encounter;

	@BeforeEach
	public void setUp() {
		captureService = Context.getCaptureService();
		encounter = Context.getEncounterService().getEncounter(3);
	}

	@Test
	public void createCapture_shouldStoreTheArtifactInCaptureStorage() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertNotNull(capture.getArtifactUri());
		assertEquals(capture, captureService.getCapturesByEncounter(encounter).get(0));
		try (InputStream artifact = captureService.getArtifact(capture)) {
			assertArrayEquals(CONTENT, artifact.readAllBytes());
		}
	}

	@Test
	public void createCapture_shouldKeepAScannedFormAndExpireAudio() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");
		Capture audio = create(Kind.AUDIO, "audio/mpeg");

		assertEquals(Capture.RetentionClass.KEEP, form.getRetentionClass());
		assertNull(form.getExpiresAt());
		assertEquals(Capture.RetentionClass.EXPIRE, audio.getRetentionClass());
		assertNotNull(audio.getExpiresAt());
	}

	@Test
	public void createCapture_shouldFailForACaptureThatIsAlreadySaved() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertThrows(APIException.class, () -> captureService.createCapture(capture, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void deleteExpiredArtifacts_shouldDeleteAnExpiredAudioArtifactAndNeverAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");
		Capture expired = create(Kind.AUDIO, "audio/mpeg");
		expired.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
		Capture current = create(Kind.AUDIO, "audio/mpeg");

		assertEquals(1, captureService.deleteExpiredArtifacts());

		assertNotNull(expired.getArtifactDeletedAt());
		assertThrows(APIException.class, () -> captureService.getArtifact(expired));
		assertNull(form.getArtifactDeletedAt());
		assertNull(current.getArtifactDeletedAt());
		assertNotNull(captureService.getCaptureByUuid(expired.getUuid()));
	}

	@Test
	public void withdrawConsent_shouldDeleteAnExpiringArtifactAtOnceAndKeepAScannedForm() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");

		captureService.withdrawConsent(audio);
		captureService.withdrawConsent(form);

		assertNotNull(audio.getArtifactDeletedAt());
		assertNull(form.getArtifactDeletedAt());
		assertEquals(Capture.ConsentStatus.WITHDRAWN, form.getConsentStatus());
	}

	@Test
	public void getArtifact_shouldFailForAnArtifactUriOutsideCaptureStorage() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		capture.setArtifactUri("../../etc/passwd");

		assertThrows(APIException.class, () -> captureService.getArtifact(capture));
	}

	private Capture create(Kind kind, String mimeType) {
		return captureService.createCapture(new Capture(encounter, kind, mimeType), new ByteArrayInputStream(CONTENT));
	}
}
