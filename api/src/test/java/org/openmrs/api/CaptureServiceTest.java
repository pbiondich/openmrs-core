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
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.Derivation;
import org.openmrs.DictionaryVersion;
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
	public void createCapture_shouldFailForAnArtifactOverTheSizeLimit() throws Exception {
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_MAX_SIZE_MB, "1"));
		byte[] tooLarge = new byte[1024 * 1024 + 1];
		long keysBefore;
		try (Stream<String> keys = storageService.getKeys(CaptureServiceImpl.STORAGE_GROUP, "")) {
			keysBefore = keys.count();
		}

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

		assertTrue(captureService.deleteExpiredArtifact(expired));

		assertNotNull(expired.getArtifactDeletedAt());
		assertFalse(storageService.exists(expired.getArtifactKey()));
		assertTrue(captureService.getCapturesWithArtifactsToDelete().isEmpty());
	}

	@Test
	public void deleteExpiredArtifact_shouldNeverDeleteAScannedForm() {
		Capture form = create(Kind.SCANNED_FORM, "application/pdf");

		assertFalse(captureService.deleteExpiredArtifact(form));

		assertTrue(storageService.exists(form.getArtifactKey()));
	}

	@Test
	public void deleteExpiredArtifact_shouldNotDeleteAnArtifactThatHasNotExpired() {
		Capture audio = create(Kind.AUDIO, "audio/mpeg");
		// what the caller's object says does not count
		audio.setExpiresAt(new Date(0));

		assertFalse(captureService.deleteExpiredArtifact(audio));

		assertTrue(storageService.exists(audio.getArtifactKey()));
	}

	@Test
	public void deleteExpiredArtifact_shouldFailIfStorageStillHasTheArtifactAfterDeletingIt() throws Exception {
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));
		StorageService stubborn = mock(StorageService.class);
		when(stubborn.purgeData(anyString())).thenReturn(true);
		when(stubborn.exists(anyString())).thenReturn(true);
		ReflectionTestUtils.setField(unproxied(), "storageService", stubborn);
		try {
			assertThrows(APIException.class, () -> captureService.deleteExpiredArtifact(expired));
		} finally {
			ReflectionTestUtils.setField(unproxied(), "storageService", storageService);
		}
		assertNull(expired.getArtifactDeletedAt());
	}

	@Test
	public void deleteExpiredArtifact_shouldNeverDeleteAKeyOutsideCaptureStorage() throws Exception {
		String other = storageService.saveData(new ByteArrayInputStream(CONTENT), null, "complex_obs");
		Capture expired = expire(create(Kind.AUDIO, "audio/mpeg"));
		Context.getAdministrationService().executeSQL(
		    "update capture set artifact_key = '" + other + "' where capture_id = " + expired.getCaptureId(), false);
		Context.refreshEntity(expired);

		assertThrows(APIException.class, () -> captureService.deleteExpiredArtifact(expired));

		assertTrue(storageService.exists(other));
		storageService.purgeData(other);
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
		capture.setArtifactKey("openmrs-runtime.properties");
		capture.setSha256("0".repeat(64));
		capture.setExpiresAt(new Date(0));

		assertThrows(APIException.class, () -> captureService.voidCapture(capture, "planting a key"));
	}

	@Test
	public void createCapture_shouldFailForACaptureWhoseConsentIsAlreadyWithdrawn() {
		Capture capture = new Capture(encounter, Kind.AUDIO, "audio/mpeg");
		capture.setConsentStatus(Capture.ConsentStatus.WITHDRAWN);

		assertThrows(APIException.class, () -> captureService.createCapture(capture, new ByteArrayInputStream(CONTENT)));
	}

	@Test
	public void createCapture_shouldKeepTheConsentTheCallerRecorded() {
		Capture capture = new Capture(encounter, Kind.AUDIO, "audio/mpeg");
		capture.setConsentStatus(Capture.ConsentStatus.GIVEN);

		captureService.createCapture(capture, new ByteArrayInputStream(CONTENT));
		Context.refreshEntity(capture);

		assertEquals(Capture.ConsentStatus.GIVEN, capture.getConsentStatus());
	}

	@Test
	public void createCapture_shouldExpireAudioAfterTheConfiguredNumberOfDays() {
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS, "2"));
		long before = System.currentTimeMillis();

		Capture audio = create(Kind.AUDIO, "audio/mpeg");

		long days = TimeUnit.MILLISECONDS.toDays(audio.getExpiresAt().getTime() - before + 1000);
		assertEquals(2, days);
	}

	private CaptureServiceImpl unproxied() throws Exception {
		return (CaptureServiceImpl) AopTestUtils.getUltimateTargetObject(captureService);
	}

	@Test
	public void createDerivation_shouldDefaultTheAgentAndDictionaryVersion() {
		DictionaryVersion current = new DictionaryVersion();
		current.setLabel("CIEL 2026-09");
		current.setInUseFrom(new Date(System.currentTimeMillis() - 1000));
		Context.getConceptService().saveDictionaryVersion(current);

		Derivation derivation = captureService.createDerivation(derivation(create(Kind.SCANNED_FORM, "application/pdf")));

		assertEquals(Context.getAuthenticatedUser(), derivation.getAgent());
		assertEquals(current, derivation.getDictionaryVersion());
		assertEquals(derivation, captureService.getDerivationsByCapture(derivation.getCapture()).get(0));
	}

	@Test
	public void createDerivation_shouldFailForARunThatFinishedBeforeItStarted() {
		Derivation derivation = derivation(create(Kind.SCANNED_FORM, "application/pdf"));
		derivation.setFinished(new Date(derivation.getStarted().getTime() - 1));

		assertThrows(APIException.class, () -> captureService.createDerivation(derivation));
	}

	@Test
	public void createDerivation_shouldFailForAVoidedCapture() {
		Capture capture = captureService.voidCapture(create(Kind.SCANNED_FORM, "application/pdf"), "wrong patient");

		assertThrows(APIException.class, () -> captureService.createDerivation(derivation(capture)));
	}

	@Test
	public void voidDerivation_shouldFailForADerivationThatIsNotSaved() {
		assertThrows(APIException.class,
		    () -> captureService.voidDerivation(derivation(create(Kind.SCANNED_FORM, "application/pdf")), "x"));
	}

	@Test
	public void createDerivation_shouldJudgeTheCaptureAsSavedNotAsPassedIn() {
		Capture voided = captureService.voidCapture(create(Kind.SCANNED_FORM, "application/pdf"), "wrong patient");
		Context.flushSession();
		Context.evictFromSession(voided);
		Capture stale = new Capture();
		stale.setCaptureId(voided.getCaptureId());

		assertThrows(APIException.class, () -> captureService.createDerivation(derivation(stale)));
	}

	@Test
	public void getDerivationByUuid_shouldGetADerivationByItsUuid() {
		Derivation derivation = captureService.createDerivation(derivation(create(Kind.SCANNED_FORM, "application/pdf")));

		assertEquals(derivation, captureService.getDerivationByUuid(derivation.getUuid()));
	}

	@Test
	public void transferEncounter_shouldMoveObsTakenFromACaptureWithoutTheirCapture() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		Obs obs = new Obs(encounter.getPatient(), Context.getConceptService().getConcept(5089), new Date(), null);
		obs.setEncounter(encounter);
		obs.setValueNumeric(50.0);
		obs.setCapture(capture);
		obs.setSpan("xywh=412,880,60,24;field=weight");
		obs.setDerivation(captureService.createDerivation(derivation(capture)));
		encounter.addObs(obs);
		Context.getObsService().saveObs(obs, null);

		Encounter moved = Context.getEncounterService().transferEncounter(encounter,
		    Context.getPatientService().getPatient(2));

		Obs copy = moved.getAllObs().stream().filter(o -> Double.valueOf(50.0).equals(o.getValueNumeric())).findFirst()
		        .orElseThrow();
		assertNull(copy.getCapture());
		assertNull(copy.getSpan());
		assertNull(copy.getDerivation());
		assertEquals(capture, Context.getObsService().getObsByUuid(obs.getUuid()).getCapture());
	}

	@Test
	public void createDerivation_shouldFailForADerivationThatIsAlreadySaved() {
		Derivation derivation = captureService.createDerivation(derivation(create(Kind.SCANNED_FORM, "application/pdf")));

		assertThrows(APIException.class, () -> captureService.createDerivation(derivation));
	}

	@Test
	public void saveObs_shouldKeepTheCaptureSpanAndDerivationAcrossAnEdit() {
		Capture capture = create(Kind.SCANNED_FORM, "application/pdf");
		Derivation derivation = captureService.createDerivation(derivation(capture));
		ObsService obsService = Context.getObsService();
		Obs obs = new Obs(encounter.getPatient(), Context.getConceptService().getConcept(5089), new Date(), null);
		obs.setEncounter(encounter);
		obs.setValueNumeric(50.0);
		obs.setCapture(capture);
		obs.setSpan("xywh=412,880,60,24;field=weight");
		obs.setDerivation(derivation);
		obs = obsService.saveObs(obs, null);

		obs.setValueNumeric(51.0);
		Obs edited = obsService.saveObs(obs, "misread");

		assertEquals(capture, edited.getCapture());
		assertEquals("xywh=412,880,60,24;field=weight", edited.getSpan());
		assertEquals(derivation, edited.getDerivation());
	}

	private Derivation derivation(Capture capture) {
		Date started = new Date(System.currentTimeMillis() - 5000);
		Derivation derivation = new Derivation(capture, started, new Date());
		derivation.setParamsHash("sha256:" + "a".repeat(64));
		return derivation;
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
