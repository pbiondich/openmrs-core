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

import java.util.Date;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.envers.Audited;

/**
 * A release of the concept dictionary, named by its label and the releases of the terminologies it
 * was built from, and in use from a given time: the current version is the newest one not retired
 * whose time has come. An encounter records the version current when it was entered, so its coded
 * obs can be read against the dictionary their author saw.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "dictionary_version")
@AttributeOverride(name = "name", column = @Column(name = "label", nullable = false, length = 255))
@Audited
public class DictionaryVersion extends BaseOpenmrsMetadata {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "dictionary_version_id")
	private Integer dictionaryVersionId;

	@Column(name = "in_use_from", nullable = false)
	private Date inUseFrom;

	@Column(name = "ciel_release", length = 50)
	private String cielRelease;

	@Column(name = "snomed_release", length = 50)
	private String snomedRelease;

	@Column(name = "loinc_release", length = 50)
	private String loincRelease;

	public DictionaryVersion() {
	}

	/**
	 * @param label how the version is known, such as "CIEL 2026-09"
	 * @param inUseFrom when the version came into use
	 */
	public DictionaryVersion(String label, Date inUseFrom) {
		setName(label);
		this.inUseFrom = inUseFrom;
	}

	@Override
	public Integer getId() {
		return dictionaryVersionId;
	}

	@Override
	public void setId(Integer id) {
		this.dictionaryVersionId = id;
	}

	public Integer getDictionaryVersionId() {
		return dictionaryVersionId;
	}

	public void setDictionaryVersionId(Integer dictionaryVersionId) {
		this.dictionaryVersionId = dictionaryVersionId;
	}

	/**
	 * @return how the version is known; the same as {@link #getName()}, stored as label
	 */
	public String getLabel() {
		return getName();
	}

	public void setLabel(String label) {
		setName(label);
	}

	/**
	 * @return when the version came into use
	 */
	public Date getInUseFrom() {
		return inUseFrom;
	}

	public void setInUseFrom(Date inUseFrom) {
		this.inUseFrom = inUseFrom;
	}

	public String getCielRelease() {
		return cielRelease;
	}

	public void setCielRelease(String cielRelease) {
		this.cielRelease = cielRelease;
	}

	public String getSnomedRelease() {
		return snomedRelease;
	}

	public void setSnomedRelease(String snomedRelease) {
		this.snomedRelease = snomedRelease;
	}

	public String getLoincRelease() {
		return loincRelease;
	}

	public void setLoincRelease(String loincRelease) {
		this.loincRelease = loincRelease;
	}
}
