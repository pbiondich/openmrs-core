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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.envers.Audited;

/**
 * A person's review of preliminary obs, usually those software asserted in one encounter: who
 * attested, when, and what became of each obs, one {@link ObsAttestationItem} per obs.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "obs_attestation")
@Audited
public class ObsAttestation extends BaseOpenmrsData {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "obs_attestation_id")
	private Integer obsAttestationId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "encounter_id")
	private Encounter encounter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attester_id")
	private Provider attester;

	@Column(name = "attested_at", nullable = false)
	private Date attestedAt;

	@OneToMany(mappedBy = "attestation", cascade = CascadeType.ALL)
	@OrderBy("obsAttestationItemId")
	private List<ObsAttestationItem> items = new ArrayList<>();

	public ObsAttestation() {
	}

	@Override
	public Integer getId() {
		return obsAttestationId;
	}

	@Override
	public void setId(Integer id) {
		this.obsAttestationId = id;
	}

	public Integer getObsAttestationId() {
		return obsAttestationId;
	}

	public void setObsAttestationId(Integer obsAttestationId) {
		this.obsAttestationId = obsAttestationId;
	}

	/**
	 * @return the encounter whose obs were attested, or null for obs outside an encounter
	 */
	public Encounter getEncounter() {
		return encounter;
	}

	public void setEncounter(Encounter encounter) {
		this.encounter = encounter;
	}

	/**
	 * @return the provider, always a person, who attested
	 */
	public Provider getAttester() {
		return attester;
	}

	public void setAttester(Provider attester) {
		this.attester = attester;
	}

	public Date getAttestedAt() {
		return attestedAt;
	}

	public void setAttestedAt(Date attestedAt) {
		this.attestedAt = attestedAt;
	}

	public List<ObsAttestationItem> getItems() {
		return items;
	}

	public void setItems(List<ObsAttestationItem> items) {
		this.items = items;
	}

	/**
	 * @param item what became of one obs, added to this attestation
	 */
	public void addItem(ObsAttestationItem item) {
		item.setAttestation(this);
		items.add(item);
	}
}
