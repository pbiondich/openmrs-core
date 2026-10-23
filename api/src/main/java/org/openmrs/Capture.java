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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

/**
 * Something an encounter was captured in, such as a scanned form or an audio recording, from which
 * obs are derived. The artifact itself is a complex obs, so it is part of the record like any other
 * attachment and is never overwritten; the capture says what it is, whether the patient consented
 * and how long it is kept. An intermediate form, such as the transcript of a recording, is a
 * capture of its own, derived from the one it was made from. The row stays after the artifact is
 * deleted, so obs keep saying where they came from.
 * <p>
 * Only {@link org.openmrs.api.CaptureService} sets a capture's obs, checksum, expiry and deletion,
 * and once saved none of them, nor the consent, can be changed by saving the capture again: the
 * service changes consent and deletion in the database itself, and those bulk updates record
 * changed_by and date_changed but bypass Envers, so the audit tables do not show them.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "capture")
@Audited
public class Capture extends BaseOpenmrsData {

	private static final long serialVersionUID = 1L;

	/**
	 * What the capture is.
	 */
	public enum Kind {
		AUDIO,
		VIDEO,
		HANDWRITTEN,
		SCANNED_FORM,
		TYPED_TEXT,
		IMAGE
	}

	/**
	 * Whether the patient consented to the capture being kept.
	 */
	public enum ConsentStatus {
		/**
		 * Nobody recorded whether the patient consented.
		 */
		NOT_RECORDED,
		GIVEN,
		/**
		 * The patient withdrew consent, so an artifact that would expire is deleted at once.
		 */
		WITHDRAWN
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "capture_id")
	private Integer captureId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "encounter_id", updatable = false)
	private Encounter encounter;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "kind", length = 50, nullable = false, updatable = false)
	private Kind kind;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "obs_id", updatable = false)
	private Obs obs;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "derived_from_capture_id", updatable = false)
	private Capture derivedFrom;

	@Column(name = "mime_type", length = 255, nullable = false, updatable = false)
	private String mimeType;

	@Column(name = "sha256", length = 64, updatable = false)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "consent_status", length = 50, nullable = false, updatable = false)
	private ConsentStatus consentStatus = ConsentStatus.NOT_RECORDED;

	@Column(name = "expires_at", updatable = false)
	private Date expiresAt;

	@Column(name = "artifact_deleted_at", updatable = false)
	private Date artifactDeletedAt;

	public Capture() {
	}

	/**
	 * @param encounter the encounter captured
	 * @param kind what the capture is
	 * @param mimeType the artifact's media type
	 */
	public Capture(Encounter encounter, Kind kind, String mimeType) {
		this.encounter = encounter;
		this.kind = kind;
		this.mimeType = mimeType;
	}

	@Override
	public Integer getId() {
		return captureId;
	}

	@Override
	public void setId(Integer id) {
		this.captureId = id;
	}

	public Integer getCaptureId() {
		return captureId;
	}

	public void setCaptureId(Integer captureId) {
		this.captureId = captureId;
	}

	public Encounter getEncounter() {
		return encounter;
	}

	public void setEncounter(Encounter encounter) {
		this.encounter = encounter;
	}

	public Kind getKind() {
		return kind;
	}

	public void setKind(Kind kind) {
		this.kind = kind;
	}

	/**
	 * @return the complex obs holding the artifact; the service sets this
	 */
	public Obs getObs() {
		return obs;
	}

	public void setObs(Obs obs) {
		this.obs = obs;
	}

	/**
	 * @return the capture this one was made from, such as the recording a transcript was made from;
	 *         null for a raw capture
	 */
	public Capture getDerivedFrom() {
		return derivedFrom;
	}

	public void setDerivedFrom(Capture derivedFrom) {
		this.derivedFrom = derivedFrom;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	/**
	 * @return the artifact's SHA-256, in lower case hex, as it was received; the service sets this, and
	 *         it is null for a capture backfilled from an existing complex obs
	 */
	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public ConsentStatus getConsentStatus() {
		return consentStatus;
	}

	public void setConsentStatus(ConsentStatus consentStatus) {
		this.consentStatus = consentStatus;
	}

	/**
	 * @return when the artifact may be deleted, or null for one that is kept for good; the service sets
	 *         this from the kind
	 */
	public Date getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Date expiresAt) {
		this.expiresAt = expiresAt;
	}

	/**
	 * @return when the artifact was deleted, or null while it is still stored
	 */
	public Date getArtifactDeletedAt() {
		return artifactDeletedAt;
	}

	public void setArtifactDeletedAt(Date artifactDeletedAt) {
		this.artifactDeletedAt = artifactDeletedAt;
	}
}
