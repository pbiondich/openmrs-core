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

import org.openmrs.Capture;
import org.openmrs.api.CaptureService;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.TaskContext;
import org.openmrs.scheduler.TaskHandler;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes the capture artifacts that have expired, keeping their captures. Each is deleted in its
 * own transaction, so one that cannot be deleted is logged, tried again on the next run, and does
 * not stop the rest.
 *
 * @see org.openmrs.api.CaptureService#deleteExpiredArtifact(Capture)
 * @since 3.0.0
 */
@Component
public class CaptureRetentionTaskHandler implements TaskHandler<CaptureRetentionTaskData> {

	private static final Logger log = LoggerFactory.getLogger(CaptureRetentionTaskHandler.class);

	@Override
	public void execute(CaptureRetentionTaskData taskData, TaskContext taskContext) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.EDIT_CAPTURES);
			CaptureService captureService = Context.getCaptureService();
			int deleted = 0;
			int failed = 0;
			for (Capture capture : captureService.getCapturesWithArtifactsToDelete()) {
				try {
					if (captureService.deleteExpiredArtifact(capture)) {
						deleted++;
					}
				} catch (RuntimeException e) {
					failed++;
					log.error("Could not delete the artifact of capture {}; trying again on the next run", capture.getUuid(),
					    e);
				}
			}
			log.info("Deleted {} expired capture artifacts; {} could not be deleted", deleted, failed);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.EDIT_CAPTURES);
		}
	}
}
