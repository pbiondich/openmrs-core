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
 * Whether a {@link Provider} is a person or a piece of software or a device.
 *
 * @since 3.0.0
 */
public enum ProviderType {
	/**
	 * A person who provides care. Every existing provider is BIOLOGIC.
	 */
	BIOLOGIC,

	/**
	 * Software or a device that makes clinical assertions, backed by a {@link User} of type
	 * {@link UserType#SOFTWARE} or {@link UserType#DEVICE} instead of a person.
	 */
	DIGITAL
}
