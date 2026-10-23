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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.envers.Audited;

/**
 * One run of an agent, such as an extraction model, over a capture: who ran, against which
 * dictionary version, when, and with what parameters. Obs the run produced point to it, so each can
 * be traced to the run and the capture it came from. A derivation is recorded once the run is done
 * and is not changed after.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "derivation")
@Audited
public class Derivation extends BaseOpenmrsData {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "derivation_id")
	private Integer derivationId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "capture_id", updatable = false)
	private Capture capture;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "agent_user_id", updatable = false)
	private User agent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dictionary_version_id", updatable = false)
	private DictionaryVersion dictionaryVersion;

	@Column(name = "started", nullable = false, updatable = false)
	private Date started;

	@Column(name = "finished", nullable = false, updatable = false)
	private Date finished;

	@Column(name = "params_hash", length = 255, updatable = false)
	private String paramsHash;

	public Derivation() {
	}

	/**
	 * @param capture the capture the agent ran over
	 * @param started when the run started
	 * @param finished when the run finished
	 */
	public Derivation(Capture capture, Date started, Date finished) {
		this.capture = capture;
		this.started = started;
		this.finished = finished;
	}

	@Override
	public Integer getId() {
		return derivationId;
	}

	@Override
	public void setId(Integer id) {
		this.derivationId = id;
	}

	public Integer getDerivationId() {
		return derivationId;
	}

	public void setDerivationId(Integer derivationId) {
		this.derivationId = derivationId;
	}

	public Capture getCapture() {
		return capture;
	}

	public void setCapture(Capture capture) {
		this.capture = capture;
	}

	/**
	 * @return the user that ran, usually a software agent such as an extraction model; the service sets
	 *         the authenticated user if this is not set
	 */
	public User getAgent() {
		return agent;
	}

	public void setAgent(User agent) {
		this.agent = agent;
	}

	/**
	 * @return the dictionary version the agent coded against; the service sets the current one if this
	 *         is not set, and null means unknown
	 */
	public DictionaryVersion getDictionaryVersion() {
		return dictionaryVersion;
	}

	public void setDictionaryVersion(DictionaryVersion dictionaryVersion) {
		this.dictionaryVersion = dictionaryVersion;
	}

	public Date getStarted() {
		return started;
	}

	public void setStarted(Date started) {
		this.started = started;
	}

	public Date getFinished() {
		return finished;
	}

	public void setFinished(Date finished) {
		this.finished = finished;
	}

	/**
	 * @return a hash of the run's parameters, such as the model, prompt and settings, so runs with the
	 *         same parameters can be grouped; how it is computed is up to the agent
	 */
	public String getParamsHash() {
		return paramsHash;
	}

	public void setParamsHash(String paramsHash) {
		this.paramsHash = paramsHash;
	}
}
