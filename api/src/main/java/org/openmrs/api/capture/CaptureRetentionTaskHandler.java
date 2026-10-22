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

import org.openmrs.api.context.Context;
import org.openmrs.scheduler.TaskContext;
import org.openmrs.scheduler.TaskHandler;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes the capture artifacts that have expired, keeping their captures.
 *
 * @see org.openmrs.api.CaptureService#deleteExpiredArtifacts()
 * @since 3.0.0
 */
@Component
public class CaptureRetentionTaskHandler implements TaskHandler<CaptureRetentionTaskData> {

	private static final Logger log = LoggerFactory.getLogger(CaptureRetentionTaskHandler.class);

	@Override
	public void execute(CaptureRetentionTaskData taskData, TaskContext taskContext) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.EDIT_CAPTURES);
			int deleted = Context.getCaptureService().deleteExpiredArtifacts();
			log.info("Deleted {} expired capture artifacts", deleted);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.EDIT_CAPTURES);
		}
	}
}
