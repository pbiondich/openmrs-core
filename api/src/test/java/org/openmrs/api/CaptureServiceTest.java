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
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CaptureService}.
 */
public class CaptureServiceTest extends BaseContextSensitiveTest {

	private static final byte[] CONTENT = "a scanned IMCI sheet".getBytes(StandardCharsets.UTF_8);

	@Autowired
	private StorageService storageService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private CaptureService captureService;

	private Encounter encounter;

	@BeforeEach
	public void setUp() {
		captureService = Context.getCaptureService();
		encounter = Context.getEncounterService().getEncounter(3);
	}

	@Test
	public void createCapture_shouldStoreTheArtifactWithItsChecksum() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "Application/PDF; charset=binary");

		assertEquals("application/pdf", capture.getMimeType());
		assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT)), capture.getSha256());
		assertTrue(storageService.exists(capture.getArtifactKey()));
		assertEquals(capture, captureService.getCapturesByEncounter(encounter).get(0));
	}

	@Test
	public void createCapture_shouldKeepAScannedFormAndExpireAudio() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");
		Capture audio = create(Kind.AUDIO, "audio/mpeg");

		assertNull(form.getExpiresAt());
		assertTrue(audio.getExpiresAt().after(new Date()));
	}

	@Test
	public void createCapture_shouldIgnoreAnExpiryKeyOrChecksumTheCallerSet() {
		Capture capture = new Capture(encounter, Kind.SCANNED_FORM, "application/pdf");
		capture.setExpiresAt(new Date());
		capture.setArtifactKey("modules/other/secret.pdf");
		capture.setSha256("0".repeat(64));
		capture.setArtifactDeletedAt(new Date());

		captureService.createCapture(capture, new ByteArrayInputStream(CONTENT));

		assertNull(capture.getExpiresAt());
		assertNull(capture.getArtifactDeletedAt());
		assertNotEquals("modules/other/secret.pdf", capture.getArtifactKey());
		assertNotEquals("0".repeat(64), capture.getSha256());
	}

	@Test
	public void createCapture_shouldFailForACaptureThatIsAlreadySaved() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertThrows(APIException.class, () -> captureService.createCapture(capture, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void createCapture_shouldFailForAMediaTypeTheKindDoesNotAllow() {
		assertThrows(APIException.class, () -> create(Kind.TYPED_TEXT, "text/html"));
		assertThrows(APIException.class, () -> create(Kind.IMAGE, "image/svg+xml"));
		assertThrows(APIException.class, () -> create(Kind.AUDIO, "application/pdf"));
		assertThrows(APIException.class, () -> create(Kind.SCANNED_FORM, "../pdf"));
		assertThrows(APIException.class, () -> create(Kind.SCANNED_FORM, null));
	}

	@Test
	public void createCapture_shouldFailForAnArtifactOverTheSizeLimit() {
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_MAX_SIZE_MB, "1"));
		byte[] tooLarge = new byte[1024 * 1024 + 1];

		assertThrows(APIException.class,
		    () -> captureService.createCapture(new Capture(encounter, Kind.SCANNED_FORM, "application/pdf"),
		        new ByteArrayInputStream(tooLarge)));
		assertTrue(captureService.getCapturesByEncounter(encounter).isEmpty());
	}

	@Test
	public void createCapture_shouldDeleteTheArtifactIfTheTransactionRollsBack() {
		TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
		newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		String key = newTransaction.execute(status -> {
			Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
			assertTrue(storageService.exists(capture.getArtifactKey()));
			status.setRollbackOnly();
			return capture.getArtifactKey();
		});

		assertFalse(storageService.exists(key));
	}

	@Test
	public void getArtifact_shouldStreamTheStoredArtifact() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		try (InputStream artifact = captureService.getArtifact(capture)) {
			assertArrayEquals(CONTENT, artifact.readAllBytes());
		}
	}

	@Test
	public void getArtifact_shouldFailOnceTheArtifactIsDeleted() {
		Capture audio = captureService.withdrawConsent(create(Kind.AUDIO, "audio/mpeg"));

		assertThrows(APIException.class, () -> captureService.getArtifact(audio));
	}

	@Test
	public void getArtifact_shouldFailForAVoidedCapture() {
		Capture capture = captureService.voidCapture(create(Kind.SCANNED_FORM, "application/pdf"), "wrong patient");

		assertThrows(APIException.class, () -> captureService.getArtifact(capture));
	}

	@Test
	public void getArtifact_shouldReadTheKeyFromTheDatabaseNotTheObjectPassedIn() throws Exception {
		Capture other = captureService.createCapture(new Capture(encounter, Kind.TYPED_TEXT, "text/plain"),
		    new ByteArrayInputStream("another note".getBytes(StandardCharsets.UTF_8)));
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		capture.setArtifactKey(other.getArtifactKey());

		try (InputStream artifact = captureService.getArtifact(capture)) {
			assertArrayEquals(CONTENT, artifact.readAllBytes());
		}
	}

	@Test
	public void withdrawConsent_shouldDeleteAnExpiringArtifactAtOnce() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");
		String key = audio.getArtifactKey();

		captureService.withdrawConsent(audio);

		assertEquals(Capture.ConsentStatus.WITHDRAWN, audio.getConsentStatus());
		assertNotNull(audio.getArtifactDeletedAt());
		assertFalse(storageService.exists(key));
	}

	@Test
	public void withdrawConsent_shouldKeepAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");

		captureService.withdrawConsent(form);

		assertEquals(Capture.ConsentStatus.WITHDRAWN, form.getConsentStatus());
		assertNull(form.getArtifactDeletedAt());
		assertTrue(storageService.exists(form.getArtifactKey()));
	}

	@Test
	public void deleteExpiredArtifacts_shouldDeleteAnExpiredAudioArtifact() {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));

		assertEquals(1, captureService.deleteExpiredArtifacts());

		Context.refreshEntity(expired);
		assertNotNull(expired.getArtifactDeletedAt());
		assertFalse(storageService.exists(expired.getArtifactKey()));
	}

	@Test
	public void deleteExpiredArtifacts_shouldNeverDeleteAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");

		assertEquals(0, captureService.deleteExpiredArtifacts());

		assertTrue(storageService.exists(form.getArtifactKey()));
	}

	@Test
	public void deleteExpiredArtifacts_shouldNotDeleteAnArtifactThatHasNotExpired() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");

		assertEquals(0, captureService.deleteExpiredArtifacts());

		assertTrue(storageService.exists(audio.getArtifactKey()));
	}

	private Capture create(Kind kind, String mimeType) {
		return captureService.createCapture(new Capture(encounter, kind, mimeType), new ByteArrayInputStream(CONTENT));
	}

	/**
	 * Backdates a capture's expiry in the database, since saving the capture cannot change it.
	 */
	private Capture expire(Capture capture) {
		Context.getAdministrationService().executeSQL(
		    "update capture set expires_at = '2000-01-01 00:00:00' where capture_id = " + capture.getCaptureId(), false);
		Context.refreshEntity(capture);
		return capture;
	}
}
