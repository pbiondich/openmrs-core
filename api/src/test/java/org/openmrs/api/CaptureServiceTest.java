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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.CaptureServiceImpl;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CaptureService}.
 */
public class CaptureServiceTest extends BaseContextSensitiveTest {

	public static final String DATASET = "org/openmrs/api/include/CaptureServiceTest.xml";

	private static final String ROLLBACK_DATASET = "org/openmrs/api/include/CaptureServiceTest-rollback.xml";

	private static final byte[] CONTENT = "a scanned IMCI sheet".getBytes(StandardCharsets.UTF_8);

	@Autowired
	private StorageService storageService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private CaptureService captureService;

	private Encounter encounter;

	private Concept concept;

	@BeforeEach
	public void setUp() {
		executeDataSet(DATASET);
		captureService = Context.getCaptureService();
		encounter = Context.getEncounterService().getEncounter(3);
		concept = Context.getConceptService().getConcept(8480);
	}

	@Test
	public void createCapture_shouldStoreTheArtifactAsAComplexObsWithItsChecksum() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "Application/PDF; charset=binary");

		assertEquals("application/pdf", capture.getMimeType());
		assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT)), capture.getSha256());
		Obs obs = capture.getObs();
		assertEquals(concept, obs.getConcept());
		assertEquals(encounter, obs.getEncounter());
		assertEquals(encounter.getPatient().getPatientId(), obs.getPersonId());
		assertTrue(obs.getValueComplex().startsWith(capture.getUuid()));
		assertTrue(storageService.exists(key(capture)));
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
	public void createCapture_shouldIgnoreAnObsExpiryOrChecksumTheCallerSet() {
		Capture capture = new Capture(encounter, Kind.SCANNED_FORM, "application/pdf");
		Obs planted = Context.getObsService().getObs(7);
		capture.setObs(planted);
		capture.setExpiresAt(new Date());
		capture.setSha256("0".repeat(64));
		capture.setArtifactDeletedAt(new Date());

		captureService.createCapture(capture, concept, new ByteArrayInputStream(CONTENT));

		assertNotEquals(planted, capture.getObs());
		assertNull(capture.getExpiresAt());
		assertNull(capture.getArtifactDeletedAt());
		assertNotEquals("0".repeat(64), capture.getSha256());
	}

	@Test
	public void createCapture_shouldFailForACaptureThatIsAlreadySaved() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertThrows(APIException.class,
		    () -> captureService.createCapture(capture, concept, new ByteArrayInputStream(CONTENT)));
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
	public void createCapture_shouldFailForAConceptWhoseHandlerWouldNotStoreTheBytesAsReceived() {
		Concept reencoding = Context.getConceptService().getConcept(8481);
		Concept numeric = Context.getConceptService().getConcept(5089);

		assertThrows(APIException.class, () -> captureService.createCapture(new Capture(encounter, Kind.IMAGE, "image/png"),
		    reencoding, new ByteArrayInputStream(CONTENT)));
		assertThrows(APIException.class, () -> captureService.createCapture(new Capture(encounter, Kind.IMAGE, "image/png"),
		    numeric, new ByteArrayInputStream(CONTENT)));
		assertThrows(APIException.class, () -> captureService.createCapture(new Capture(encounter, Kind.IMAGE, "image/png"),
		    null, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void createCapture_shouldFailForAnArtifactOverTheSizeLimit() {
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_MAX_SIZE_MB, "1"));
		byte[] tooLarge = new byte[1024 * 1024 + 1];

		APIException e = assertThrows(APIException.class,
		    () -> captureService.createCapture(new Capture(encounter, Kind.SCANNED_FORM, "application/pdf"), concept,
		        new ByteArrayInputStream(tooLarge)));
		assertEquals(Context.getMessageSourceService().getMessage("Capture.error.tooLarge", new Object[] { 1L }, null),
		    e.getMessage());
		assertTrue(captureService.getCapturesByEncounter(encounter).isEmpty());
	}

	@Test
	public void createCapture_shouldDeleteTheArtifactIfTheTransactionRollsBack() {
		TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
		newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		String key = newTransaction.execute(status -> {
			// the concept this test loaded is not visible to a new transaction, so it loads its own
			executeDataSet(ROLLBACK_DATASET);
			Capture capture = captureService.createCapture(new Capture(encounter, Kind.SCANNED_FORM, "application/pdf"),
			    Context.getConceptService().getConcept(8482), new ByteArrayInputStream(CONTENT));
			assertTrue(storageService.exists(key(capture)));
			status.setRollbackOnly();
			return key(capture);
		});

		assertFalse(storageService.exists(key));
	}

	@Test
	public void createCapture_shouldTakeACaptureDerivedFromAnotherOfTheSameEncounter() {
		Capture recording = create(Kind.AUDIO, "audio/mpeg");
		Capture transcript = new Capture(encounter, Kind.TYPED_TEXT, "text/plain");
		transcript.setDerivedFrom(recording);

		captureService.createCapture(transcript, concept, new ByteArrayInputStream(CONTENT));

		assertEquals(recording, transcript.getDerivedFrom());
		assertNull(transcript.getExpiresAt());
	}

	@Test
	public void createCapture_shouldFailForACaptureDerivedFromOneOfAnotherEncounterOrAVoidedOne() {
		Capture recording = create(Kind.AUDIO, "audio/mpeg");
		Capture other = new Capture(Context.getEncounterService().getEncounter(4), Kind.TYPED_TEXT, "text/plain");
		other.setDerivedFrom(recording);
		assertThrows(APIException.class,
		    () -> captureService.createCapture(other, concept, new ByteArrayInputStream(CONTENT)));

		captureService.voidCapture(recording, "wrong patient");
		Capture transcript = new Capture(encounter, Kind.TYPED_TEXT, "text/plain");
		transcript.setDerivedFrom(recording);
		assertThrows(APIException.class,
		    () -> captureService.createCapture(transcript, concept, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void createCapture_shouldFailForACaptureWhoseConsentIsAlreadyWithdrawn() {
		Capture capture = new Capture(encounter, Kind.AUDIO, "audio/mpeg");
		capture.setConsentStatus(Capture.ConsentStatus.WITHDRAWN);

		assertThrows(APIException.class,
		    () -> captureService.createCapture(capture, concept, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void createCapture_shouldKeepTheConsentTheCallerRecorded() {
		Capture capture = new Capture(encounter, Kind.AUDIO, "audio/mpeg");
		capture.setConsentStatus(Capture.ConsentStatus.GIVEN);

		captureService.createCapture(capture, concept, new ByteArrayInputStream(CONTENT));
		Context.refreshEntity(capture);

		assertEquals(Capture.ConsentStatus.GIVEN, capture.getConsentStatus());
	}

	@Test
	public void createCapture_shouldExpireAudioAfterTheConfiguredNumberOfDays() {
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS, "2"));
		long before = System.currentTimeMillis();

		Capture audio = create(Kind.AUDIO, "audio/mpeg");

		assertEquals(2, TimeUnit.MILLISECONDS.toDays(audio.getExpiresAt().getTime() - before + 1000));
	}

	@Test
	public void getArtifact_shouldStreamTheStoredArtifact() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertArrayEquals(CONTENT, read(capture));
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
	public void getArtifact_shouldReadTheArtifactsObsFromTheDatabaseNotTheObjectPassedIn() throws Exception {
		Capture other = captureService.createCapture(new Capture(encounter, Kind.TYPED_TEXT, "text/plain"), concept,
		    new ByteArrayInputStream("another note".getBytes(StandardCharsets.UTF_8)));
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		capture.setObs(other.getObs());

		assertArrayEquals(CONTENT, read(capture));
	}

	@Test
	public void getArtifact_shouldStillReadTheArtifactAfterItsObsWasEdited() throws Exception {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		Obs obs = capture.getObs();
		String key = key(capture);
		obs.setComment("the second page is upside down");

		Obs edited = Context.getObsService().saveObs(obs, "a comment");
		edited.setComment("and the third");
		Context.getObsService().saveObs(edited, "another comment");

		assertTrue(storageService.exists(key));
		assertArrayEquals(CONTENT, read(capture));
	}

	@Test
	public void purgeObs_shouldRefuseAnObsWhoseFileACaptureHolds() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");

		assertThrows(APIException.class, () -> Context.getObsService().purgeObs(capture.getObs()));
		assertTrue(storageService.exists(key(capture)));
	}

	@Test
	public void withdrawConsent_shouldDeleteAnExpiringArtifactAtOnce() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");
		String key = key(audio);

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
		assertTrue(storageService.exists(key(form)));
	}

	@Test
	public void getCapturesWithArtifactsToDelete_shouldReturnAudioThatHasExpiredOrWhoseConsentWasWithdrawn() {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));
		Capture withdrawn = create(Kind.AUDIO, "audio/mpeg");
		Context.getAdministrationService().executeSQL(
		    "update capture set consent_status = 'WITHDRAWN' where capture_id = " + withdrawn.getCaptureId(), false);

		assertEquals(List.of(expired, withdrawn), captureService.getCapturesWithArtifactsToDelete());
	}

	@Test
	public void getCapturesWithArtifactsToDelete_shouldNeverReturnAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");
		Context.getAdministrationService().executeSQL("update capture set consent_status = 'WITHDRAWN', expires_at = NULL"
		        + " where capture_id = " + form.getCaptureId(),
		    false);

		assertTrue(captureService.getCapturesWithArtifactsToDelete().isEmpty());
	}

	@Test
	public void getCapturesWithArtifactsToDelete_shouldNotReturnAnArtifactThatHasNotExpired() {
		create(Kind.AUDIO, "audio/mpeg");

		assertTrue(captureService.getCapturesWithArtifactsToDelete().isEmpty());
	}

	@Test
	public void deleteExpiredArtifact_shouldDeleteAnExpiredAudioArtifact() {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));
		String key = key(expired);

		assertTrue(captureService.deleteExpiredArtifact(expired));

		assertNotNull(expired.getArtifactDeletedAt());
		assertFalse(storageService.exists(key));
		assertTrue(captureService.getCapturesWithArtifactsToDelete().isEmpty());
	}

	@Test
	public void deleteExpiredArtifact_shouldVoidTheArtifactsObsKeepingItsStatus() {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));

		captureService.deleteExpiredArtifact(expired);
		Obs obs = expired.getObs();
		Context.refreshEntity(obs);

		assertTrue(obs.getVoided());
		assertEquals("Capture artifact expired", obs.getVoidReason());
		assertEquals(Obs.Status.FINAL, obs.getStatus());
	}

	@Test
	public void deleteExpiredArtifact_shouldNeverDeleteAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");

		assertFalse(captureService.deleteExpiredArtifact(form));

		assertTrue(storageService.exists(key(form)));
	}

	@Test
	public void deleteExpiredArtifact_shouldNotDeleteAnArtifactThatHasNotExpired() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");
		// what the caller's object says does not count
		audio.setExpiresAt(new Date(0));

		assertFalse(captureService.deleteExpiredArtifact(audio));

		assertTrue(storageService.exists(key(audio)));
	}

	@Test
	public void deleteExpiredArtifact_shouldFailIfStorageStillHasTheArtifactAfterDeletingIt() throws Exception {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));
		StorageService stubborn = mock(StorageService.class);
		when(stubborn.exists(anyString())).thenReturn(true);
		when(stubborn.purgeData(anyString())).thenReturn(true);
		ReflectionTestUtils.setField(unproxied(), "storageService", stubborn);
		try {
			assertThrows(APIException.class, () -> captureService.deleteExpiredArtifact(expired));
		} finally {
			ReflectionTestUtils.setField(unproxied(), "storageService", storageService);
		}
		assertNull(expired.getArtifactDeletedAt());
	}

	@Test
	public void voidCapture_shouldVoidASavedCapture() {
		Capture capture = captureService.voidCapture(create(Kind.SCANNED_FORM, "application/pdf"), "wrong patient");

		assertTrue(capture.getVoided());
		assertTrue(captureService.getCapturesByEncounter(encounter).isEmpty());
	}

	@Test
	public void voidCapture_shouldFailForACaptureThatIsNotSaved() {
		Capture capture = new Capture(encounter, Kind.AUDIO, "audio/mpeg");
		capture.setObs(Context.getObsService().getObs(7));
		capture.setExpiresAt(new Date(0));

		assertThrows(APIException.class, () -> captureService.voidCapture(capture, "planting an obs"));
	}

	private byte[] read(Capture capture) throws Exception {
		try (InputStream artifact = captureService.getArtifact(capture)) {
			return artifact.readAllBytes();
		}
	}

	private CaptureServiceImpl unproxied() {
		return (CaptureServiceImpl) AopTestUtils.getUltimateTargetObject(captureService);
	}

	private Capture create(Kind kind, String mimeType) {
		return captureService.createCapture(new Capture(encounter, kind, mimeType), concept,
		    new ByteArrayInputStream(CONTENT));
	}

	/**
	 * The storage key of a capture's artifact, from its obs's value.
	 */
	public static String key(Capture capture) {
		String value = capture.getObs().getValueComplex();
		return value.substring(value.lastIndexOf('|') + 1).trim();
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
