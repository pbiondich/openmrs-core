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

import org.openmrs.api.context.Context;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Schedules the daily deletion of expired capture artifacts once at startup; the scheduler skips it
 * if it is already scheduled.
 *
 * @since 3.0.0
 */
@Component
public class CaptureRetentionTaskScheduler implements SmartInitializingSingleton {

	public static final String TASK_NAME = "Capture Artifact Retention";

	public static final String TASK_UUID = "6f0b8a52-3c1e-4d47-9a0e-2b5d7c4e9f13";

	private final SchedulerService schedulerService;

	public CaptureRetentionTaskScheduler(SchedulerService schedulerService) {
		this.schedulerService = schedulerService;
	}

	@Override
	public void afterSingletonsInstantiated() {
		boolean opened = !Context.isSessionOpen();
		if (opened) {
			Context.openSession();
		}
		try {
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_SCHEDULER);
			schedulerService.scheduleRecurrently(TASK_UUID, new CaptureRetentionTaskData(), Duration.ofDays(1), TASK_NAME);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_SCHEDULER);
			if (opened) {
				Context.closeSession();
			}
		}
	}
}
