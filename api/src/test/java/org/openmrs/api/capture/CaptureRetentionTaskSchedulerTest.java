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

import java.time.Duration;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CaptureRetentionTaskScheduler}. It opens its own session when there is none, so
 * the test cannot run in a transaction.
 */
@SkipBaseSetup
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CaptureRetentionTaskSchedulerTest extends BaseContextSensitiveTest {

	@Autowired
	private SessionFactory sessionFactory;

	@Test
	public void afterSingletonsInstantiated_shouldScheduleTheRetentionTaskDailyAndCloseItsSession() {
		Context.closeSession();
		SchedulerService schedulerService = mock(SchedulerService.class);

		new CaptureRetentionTaskScheduler(schedulerService).afterSingletonsInstantiated();

		verify(schedulerService).scheduleRecurrently(eq(CaptureRetentionTaskScheduler.TASK_UUID),
		    any(CaptureRetentionTaskData.class), eq(Duration.ofDays(1)), eq(CaptureRetentionTaskScheduler.TASK_NAME));
		assertFalse(Context.isSessionOpen());
		assertNull(TransactionSynchronizationManager.getResource(sessionFactory));
	}
}
