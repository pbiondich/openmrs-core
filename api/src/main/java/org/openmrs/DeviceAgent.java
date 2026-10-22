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

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.envers.Audited;

/**
 * Describes a {@link User} of type {@link UserType#DEVICE}, such as an analyser or a monitor that
 * writes data directly. The identifier is this implementation's own identifier for the device, the
 * one its messages carry; it names the device and never changes. The location is where the device
 * is now and changes when it moves. Unlike software, a device is not versioned: new firmware does
 * not make it a new device.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "device_agent", uniqueConstraints = @UniqueConstraint(name = "device_agent_identifier", columnNames = {
        "identifier" }))
@Audited
public class DeviceAgent implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "user_id")
	private Integer userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "identifier", nullable = false, length = 255, updatable = false)
	private String identifier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "location_id")
	private Location location;

	public DeviceAgent() {
	}

	/**
	 * @param user the device user this describes
	 * @param identifier the identifier that names the device
	 */
	public DeviceAgent(User user, String identifier) {
		this.user = user;
		this.identifier = identifier;
	}

	public Integer getUserId() {
		return userId;
	}

	public User getUser() {
		return user;
	}

	/**
	 * @return the identifier that names the device; it never changes
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * @return where the device is now, or null if that is not recorded
	 */
	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	@Override
	public String toString() {
		return identifier;
	}
}
