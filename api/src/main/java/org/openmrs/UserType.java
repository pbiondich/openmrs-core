/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs;

/**
 * The kind of principal a {@link User} account represents.
 *
 * @since 3.0.0
 */
public enum UserType {
	/**
	 * A person who logs in. Every existing user is HUMAN.
	 */
	HUMAN,

	/**
	 * A device, such as an analyser or a monitor, that writes data directly.
	 */
	DEVICE,

	/**
	 * A piece of software acting on its own, such as the Daemon user or an extraction model.
	 */
	SOFTWARE
}
