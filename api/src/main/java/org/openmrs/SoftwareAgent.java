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
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Immutable;
import org.hibernate.envers.Audited;

/**
 * Describes a {@link User} of type {@link UserType#SOFTWARE}. The version is part of the identity:
 * each version of a piece of software is its own user with its own SoftwareAgent, and all versions
 * of the software share its name. A SoftwareAgent is never changed after it is created. When the
 * model or configuration changes, the version must change too; the model identifier and config hash
 * describe a version but do not identify it.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "software_agent", uniqueConstraints = @UniqueConstraint(name = "software_agent_name_version", columnNames = {
        "name", "version" }))
@Immutable
@Audited
public class SoftwareAgent implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "user_id")
	private Integer userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "version", nullable = false, length = 50)
	private String version;

	@Column(name = "model_identifier", length = 255)
	private String modelIdentifier;

	@Column(name = "config_hash", length = 64)
	private String configHash;

	public SoftwareAgent() {
	}

	/**
	 * @param user the software user this describes
	 * @param name the name of the software, the same for every version
	 * @param version the version of the software
	 */
	public SoftwareAgent(User user, String name, String version) {
		this.user = user;
		this.name = name;
		this.version = version;
	}

	public Integer getUserId() {
		return userId;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * @return the identifier of the model this version runs, or null if it runs none
	 */
	public String getModelIdentifier() {
		return modelIdentifier;
	}

	public void setModelIdentifier(String modelIdentifier) {
		this.modelIdentifier = modelIdentifier;
	}

	/**
	 * @return the SHA-256 hash, in lowercase hex, of this version's prompt and configuration, or null
	 */
	public String getConfigHash() {
		return configHash;
	}

	public void setConfigHash(String configHash) {
		this.configHash = configHash;
	}

	@Override
	public String toString() {
		return name + " " + version;
	}
}
