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
 * obs are derived. The artifact itself is a file in capture storage, never in the database; this
 * row stays after the file is deleted, so obs keep saying where they came from.
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

	/**
	 * How long the artifact is kept.
	 */
	public enum RetentionClass {
		/**
		 * Kept for good, as part of the record.
		 */
		KEEP,
		/**
		 * Deleted once it expires, or once consent is withdrawn.
		 */
		EXPIRE
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

	@Column(name = "artifact_uri", length = 1024, nullable = false, updatable = false)
	private String artifactUri;

	@Column(name = "mime_type", length = 255, nullable = false, updatable = false)
	private String mimeType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "consent_status", length = 50, nullable = false)
	private ConsentStatus consentStatus = ConsentStatus.NOT_RECORDED;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "retention_class", length = 50, nullable = false, updatable = false)
	private RetentionClass retentionClass;

	@Column(name = "expires_at")
	private Date expiresAt;

	@Column(name = "artifact_deleted_at")
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
	 * @return where the artifact is in capture storage, relative to it; the service sets this
	 */
	public String getArtifactUri() {
		return artifactUri;
	}

	public void setArtifactUri(String artifactUri) {
		this.artifactUri = artifactUri;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public ConsentStatus getConsentStatus() {
		return consentStatus;
	}

	public void setConsentStatus(ConsentStatus consentStatus) {
		this.consentStatus = consentStatus;
	}

	/**
	 * @return how long the artifact is kept; the service sets this from the kind
	 */
	public RetentionClass getRetentionClass() {
		return retentionClass;
	}

	public void setRetentionClass(RetentionClass retentionClass) {
		this.retentionClass = retentionClass;
	}

	/**
	 * @return when an artifact that expires may be deleted, or null for one that is kept
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
