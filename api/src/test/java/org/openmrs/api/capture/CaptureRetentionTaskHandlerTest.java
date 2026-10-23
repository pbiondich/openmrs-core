/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.capture;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.api.CaptureService;
import org.openmrs.api.CaptureServiceTest;
import org.openmrs.api.StorageService;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link CaptureRetentionTaskHandler}.
 */
public class CaptureRetentionTaskHandlerTest extends BaseContextSensitiveTest {

	@Autowired
	private StorageService storageService;

	@Autowired
	private CaptureRetentionTaskHandler handler;

	@Test
	public void execute_shouldDeleteExpiredArtifactsAndNotStopAtOneThatCannotBeDeleted() {
		executeDataSet(CaptureServiceTest.DATASET);
		Capture bad = expire(create());
		Context.getAdministrationService().executeSQL(
		    "update obs set value_complex = 'x|../outside' where obs_id = " + bad.getObs().getObsId(), false);
		Context.refreshEntity(bad.getObs());
		Capture good = expire(create());
		String goodKey = CaptureServiceTest.key(good);

		handler.execute(new CaptureRetentionTaskData(), null);

		Context.refreshEntity(bad);
		Context.refreshEntity(good);
		assertNull(bad.getArtifactDeletedAt());
		assertNotNull(good.getArtifactDeletedAt());
		assertFalse(storageService.exists(goodKey));
	}

	private Capture create() {
		CaptureService captureService = Context.getCaptureService();
		return captureService.createCapture(
		    new Capture(Context.getEncounterService().getEncounter(3), Kind.AUDIO, "audio/mpeg"),
		    Context.getConceptService().getConcept(8480),
		    new ByteArrayInputStream("a recording".getBytes(StandardCharsets.UTF_8)));
	}

	private Capture expire(Capture capture) {
		Context.getAdministrationService().executeSQL(
		    "update capture set expires_at = '2000-01-01 00:00:00' where capture_id = " + capture.getCaptureId(), false);
		Context.refreshEntity(capture);
		return capture;
	}
}
