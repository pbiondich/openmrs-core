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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

/**
 * What an attestation made of one preliminary obs. The outcome is the only record that an obs was
 * rejected: a rejected obs stays preliminary and is otherwise unchanged.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "obs_attestation_item", uniqueConstraints = @UniqueConstraint(name = "obs_attestation_item_obs", columnNames = {
        "obs_attestation_id", "obs_id" }))
@Audited
public class ObsAttestationItem extends BaseOpenmrsObject {

	private static final long serialVersionUID = 1L;

	/**
	 * What the attester made of an obs.
	 */
	public enum Outcome {
		/**
		 * The obs is right as it stands and becomes final.
		 */
		ACCEPTED,
		/**
		 * The obs is wrong; it stays preliminary, with this outcome as the record.
		 */
		REJECTED,
		/**
		 * The obs was corrected: a new version, asserted by the attester, replaces it and is final.
		 */
		AMENDED,
		/**
		 * The attester looked at the obs and left it preliminary, for later.
		 */
		LEFT_PRELIMINARY
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "obs_attestation_item_id")
	private Integer obsAttestationItemId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "obs_attestation_id")
	private ObsAttestation attestation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "obs_id")
	private Obs obs;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "outcome", length = 50, nullable = false)
	private Outcome outcome;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "superseding_obs_id")
	private Obs supersedingObs;

	public ObsAttestationItem() {
	}

	/**
	 * @param obs the preliminary obs
	 * @param outcome what the attester made of it
	 */
	public ObsAttestationItem(Obs obs, Outcome outcome) {
		this.obs = obs;
		this.outcome = outcome;
	}

	@Override
	public Integer getId() {
		return obsAttestationItemId;
	}

	@Override
	public void setId(Integer id) {
		this.obsAttestationItemId = id;
	}

	public Integer getObsAttestationItemId() {
		return obsAttestationItemId;
	}

	public void setObsAttestationItemId(Integer obsAttestationItemId) {
		this.obsAttestationItemId = obsAttestationItemId;
	}

	public ObsAttestation getAttestation() {
		return attestation;
	}

	public void setAttestation(ObsAttestation attestation) {
		this.attestation = attestation;
	}

	public Obs getObs() {
		return obs;
	}

	public void setObs(Obs obs) {
		this.obs = obs;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public void setOutcome(Outcome outcome) {
		this.outcome = outcome;
	}

	/**
	 * @return for an amended obs, the corrected version that replaces it; otherwise null. When the
	 *         attestation is made this is a new, unsaved obs carrying the correction.
	 */
	public Obs getSupersedingObs() {
		return supersedingObs;
	}

	public void setSupersedingObs(Obs supersedingObs) {
		this.supersedingObs = supersedingObs;
	}
}
