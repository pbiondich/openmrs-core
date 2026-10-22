/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Capture;
import org.openmrs.Capture.Kind;
import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.CaptureService;
import org.openmrs.api.StorageService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.CaptureDAO;
import org.openmrs.api.storage.ObjectMetadata;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Default implementation of {@link CaptureService}.
 *
 * @since 3.0.0
 */
@Service("captureService")
@Transactional
public class CaptureServiceImpl extends BaseOpenmrsService implements CaptureService {

	private static final Logger log = LoggerFactory.getLogger(CaptureServiceImpl.class);

	/**
	 * The storage group capture artifacts are kept under.
	 */
	static final String STORAGE_GROUP = "captures";

	private static final int DEFAULT_RETENTION_DAYS = 90;

	private static final long DEFAULT_MAX_SIZE_MB = 100;

	private static final Pattern MEDIA_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*");

	@Autowired
	private CaptureDAO dao;

	@Autowired
	private StorageService storageService;

	/**
	 * @see CaptureService#createCapture(Capture, InputStream)
	 */
	@Override
	public Capture createCapture(Capture capture, InputStream artifact) {
		if (capture.getCaptureId() != null || capture.getEncounter() == null || capture.getKind() == null
		        || artifact == null) {
			throw new APIException("Capture.error.invalid", (Object[]) null);
		}
		if (capture.getConsentStatus() == null) {
			capture.setConsentStatus(Capture.ConsentStatus.NOT_RECORDED);
		} else if (capture.getConsentStatus() == Capture.ConsentStatus.WITHDRAWN) {
			throw new APIException("Capture.error.consentWithdrawn", (Object[]) null);
		}
		String mimeType = mediaType(capture.getMimeType());
		if (mimeType == null || !allows(capture.getKind(), mimeType)) {
			throw new APIException("Capture.error.mimeType", new Object[] { capture.getMimeType(), capture.getKind() });
		}
		capture.setMimeType(mimeType);
		// audio and video expire, every other kind is kept
		Date now = new Date();
		capture.setExpiresAt(capture.getKind() == Kind.AUDIO || capture.getKind() == Kind.VIDEO
		        ? new Date(now.getTime() + TimeUnit.DAYS.toMillis(retentionDays()))
		        : null);
		capture.setArtifactDeletedAt(null);

		MessageDigest sha256 = sha256();
		long maxBytes = maxSizeMb() * 1024 * 1024;
		String key;
		// not closed here: the caller closes the artifact
		InputStream in = new DigestInputStream(new LimitedInputStream(artifact, maxBytes), sha256);
		try {
			// storage picks the key, so nothing the caller sends becomes part of a path
			key = storageService.saveData(in, ObjectMetadata.builder().setMimeType(mimeType).build(), STORAGE_GROUP);
		} catch (TooLargeException e) {
			throw new APIException("Capture.error.tooLarge", new Object[] { maxSizeMb() }, e);
		} catch (IOException e) {
			throw new APIException("Capture.error.store", (Object[]) null, e);
		}
		purgeIfRolledBack(key);
		capture.setArtifactKey(key);
		capture.setSha256(HexFormat.of().formatHex(sha256.digest()));
		return dao.saveCapture(capture);
	}

	/**
	 * @see CaptureService#getCaptureByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Capture getCaptureByUuid(String uuid) {
		return dao.getCaptureByUuid(uuid);
	}

	/**
	 * @see CaptureService#getCapturesByEncounter(Encounter)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Capture> getCapturesByEncounter(Encounter encounter) {
		return dao.getCapturesByEncounter(encounter);
	}

	/**
	 * @see CaptureService#getArtifact(Capture)
	 */
	@Override
	@Transactional(readOnly = true)
	public InputStream getArtifact(Capture capture) {
		if (capture.getCaptureId() == null) {
			throw new APIException("Capture.error.notSaved", (Object[]) null);
		}
		// the key comes from the database, never from the object passed in
		String key = capture.getVoided() ? null : dao.getReadableArtifactKey(capture.getCaptureId());
		if (key == null) {
			throw new APIException("Capture.error.noArtifact", new Object[] { capture.getUuid() });
		}
		try {
			return storageService.getData(key);
		} catch (IOException e) {
			throw new APIException("Capture.error.read", new Object[] { capture.getUuid() }, e);
		}
	}

	/**
	 * @see CaptureService#withdrawConsent(Capture)
	 */
	@Override
	public Capture withdrawConsent(Capture capture) {
		Integer captureId = reload(capture).getCaptureId();
		dao.withdrawConsent(captureId);
		// an artifact that would expire is now due for deletion; one that is kept is not
		deleteArtifact(captureId, new Date());
		return reload(capture);
	}

	/**
	 * @see CaptureService#deleteExpiredArtifacts()
	 */
	@Override
	public int deleteExpiredArtifacts() {
		Date now = new Date();
		int deleted = 0;
		for (Integer captureId : dao.getCaptureIdsWithArtifactsToDelete(now)) {
			try {
				if (deleteArtifact(captureId, now)) {
					deleted++;
				}
			} catch (APIException e) {
				log.error("Could not delete the artifact of capture {}; trying again on the next run", captureId, e);
			}
		}
		return deleted;
	}

	/**
	 * @see CaptureService#voidCapture(Capture, String)
	 */
	@Override
	public Capture voidCapture(Capture capture, String reason) {
		return dao.saveCapture(capture);
	}

	/**
	 * Deletes a capture's artifact if, going by the database, it is due for deletion.
	 *
	 * @return true if it was due and so deleted
	 */
	private boolean deleteArtifact(Integer captureId, Date now) {
		String key = dao.getArtifactKeyToDelete(captureId, now);
		if (key == null) {
			return false;
		}
		try {
			// an artifact already gone, say after a rolled back run, still counts as deleted
			storageService.purgeData(key);
		} catch (IOException e) {
			throw new APIException("Capture.error.delete", new Object[] { captureId }, e);
		}
		dao.markArtifactDeleted(captureId, now);
		return true;
	}

	private Capture reload(Capture capture) {
		Capture saved = capture.getCaptureId() == null ? null : dao.reload(capture);
		if (saved == null) {
			throw new APIException("Capture.error.notSaved", (Object[]) null);
		}
		return saved;
	}

	/**
	 * Deletes a just stored artifact if the transaction saving its capture rolls back, so storage never
	 * keeps an artifact no capture points to.
	 */
	private void purgeIfRolledBack(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						storageService.purgeData(key);
					} catch (IOException | RuntimeException e) {
						log.error("Could not delete the artifact {} of a capture that was not saved", key, e);
					}
				}
			}
		});
	}

	/**
	 * @return the media type without parameters, in lower case, or null if it is not one
	 */
	static String mediaType(String mimeType) {
		if (StringUtils.isBlank(mimeType)) {
			return null;
		}
		String type = StringUtils.substringBefore(mimeType, ";").trim().toLowerCase(Locale.ROOT);
		return MEDIA_TYPE.matcher(type).matches() ? type : null;
	}

	/**
	 * Which media types each kind may be. Nothing a browser would run, such as HTML or SVG, is allowed.
	 */
	static boolean allows(Kind kind, String mimeType) {
		boolean image = mimeType.startsWith("image/") && !mimeType.equals("image/svg+xml");
		boolean pdf = mimeType.equals("application/pdf");
		switch (kind) {
			case AUDIO:
				return mimeType.startsWith("audio/");
			case VIDEO:
				return mimeType.startsWith("video/");
			case IMAGE:
				return image;
			case SCANNED_FORM:
			case HANDWRITTEN:
				return image || pdf;
			case TYPED_TEXT:
				return mimeType.equals("text/plain") || pdf;
			default:
				return false;
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private int retentionDays() {
		return (int) positiveNumber(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
	}

	private long maxSizeMb() {
		return positiveNumber(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_MAX_SIZE_MB, DEFAULT_MAX_SIZE_MB);
	}

	private long positiveNumber(String property, long defaultValue) {
		String value;
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			value = Context.getAdministrationService().getGlobalProperty(property, String.valueOf(defaultValue));
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		try {
			long number = Long.parseLong(value.trim());
			if (number > 0 && number <= Integer.MAX_VALUE) {
				return number;
			}
		} catch (NumberFormatException e) {
			// falls through to the default
		}
		log.warn("{} is not a positive whole number: {}; using {}", property, value, defaultValue);
		return defaultValue;
	}

	private static class TooLargeException extends IOException {

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Fails a read once more than the limit has been read, so an artifact over the size limit is never
	 * stored whole.
	 */
	private static class LimitedInputStream extends FilterInputStream {

		private final long limit;

		private long count;

		LimitedInputStream(InputStream in, long limit) {
			super(in);
			this.limit = limit;
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b != -1) {
				count(1);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			if (n > 0) {
				count(n);
			}
			return n;
		}

		@Override
		public long skip(long n) throws IOException {
			throw new IOException("skip is not supported");
		}

		@Override
		public boolean markSupported() {
			return false;
		}

		private void count(long n) throws TooLargeException {
			count += n;
			if (count > limit) {
				throw new TooLargeException();
			}
		}
	}
}
