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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Capture;
import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.CaptureService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.CaptureDAO;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CaptureService}.
 *
 * @since 3.0.0
 */
@Service("captureService")
@Transactional
public class CaptureServiceImpl extends BaseOpenmrsService implements CaptureService {

	private static final Logger log = LoggerFactory.getLogger(CaptureServiceImpl.class);

	private static final EnumSet<Capture.Kind> EXPIRING_KINDS = EnumSet.of(Capture.Kind.AUDIO, Capture.Kind.VIDEO);

	private static final int DEFAULT_RETENTION_DAYS = 90;

	private static final Map<String, String> EXTENSIONS = Map.of("application/pdf", ".pdf", "image/jpeg", ".jpg",
	    "image/png", ".png", "image/tiff", ".tif", "audio/mpeg", ".mp3", "audio/wav", ".wav", "audio/ogg", ".ogg",
	    "video/mp4", ".mp4", "text/plain", ".txt");

	@Autowired
	private CaptureDAO dao;

	/**
	 * @see CaptureService#createCapture(Capture, InputStream)
	 */
	@Override
	public Capture createCapture(Capture capture, InputStream artifact) {
		if (capture.getCaptureId() != null || capture.getEncounter() == null || capture.getKind() == null
		        || StringUtils.isBlank(capture.getMimeType()) || artifact == null) {
			throw new APIException("Capture.error.invalid", (Object[]) null);
		}
		Date now = new Date();
		if (EXPIRING_KINDS.contains(capture.getKind())) {
			capture.setRetentionClass(Capture.RetentionClass.EXPIRE);
			capture.setExpiresAt(new Date(now.getTime() + TimeUnit.DAYS.toMillis(retentionDays())));
		} else {
			capture.setRetentionClass(Capture.RetentionClass.KEEP);
			capture.setExpiresAt(null);
		}
		capture.setArtifactDeletedAt(null);
		// the file is named from the capture's uuid, so nothing the caller sends becomes part of a path
		String extension = EXTENSIONS.getOrDefault(capture.getMimeType().toLowerCase(Locale.ROOT), ".bin");
		String uri = new SimpleDateFormat("yyyy/MM").format(now) + "/" + capture.getUuid() + extension;
		Path file = resolve(uri);
		try {
			Files.createDirectories(file.getParent());
			Files.copy(artifact, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new APIException("Capture.error.store", new Object[] { uri }, e);
		}
		capture.setArtifactUri(uri);
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
		if (capture.getArtifactDeletedAt() != null) {
			throw new APIException("Capture.error.artifactDeleted", new Object[] { capture.getUuid() });
		}
		try {
			return Files.newInputStream(resolve(capture.getArtifactUri()));
		} catch (IOException e) {
			throw new APIException("Capture.error.read", new Object[] { capture.getUuid() }, e);
		}
	}

	/**
	 * @see CaptureService#withdrawConsent(Capture)
	 */
	@Override
	public Capture withdrawConsent(Capture capture) {
		capture.setConsentStatus(Capture.ConsentStatus.WITHDRAWN);
		if (capture.getRetentionClass() == Capture.RetentionClass.EXPIRE) {
			deleteArtifact(capture);
		}
		return dao.saveCapture(capture);
	}

	/**
	 * @see CaptureService#deleteExpiredArtifacts()
	 */
	@Override
	public int deleteExpiredArtifacts() {
		List<Capture> captures = dao.getCapturesWithArtifactsToDelete(new Date());
		for (Capture capture : captures) {
			deleteArtifact(capture);
			dao.saveCapture(capture);
		}
		return captures.size();
	}

	/**
	 * @see CaptureService#voidCapture(Capture, String)
	 */
	@Override
	public Capture voidCapture(Capture capture, String reason) {
		return dao.saveCapture(capture);
	}

	private void deleteArtifact(Capture capture) {
		if (capture.getArtifactDeletedAt() != null) {
			return;
		}
		try {
			Files.deleteIfExists(resolve(capture.getArtifactUri()));
		} catch (IOException e) {
			throw new APIException("Capture.error.delete", new Object[] { capture.getUuid() }, e);
		}
		capture.setArtifactDeletedAt(new Date());
	}

	/**
	 * The file an artifact uri names, refusing any that would fall outside capture storage.
	 */
	private Path resolve(String uri) {
		Path storage = storageDirectory().toPath().toAbsolutePath().normalize();
		Path file = storage.resolve(uri).normalize();
		if (!file.startsWith(storage)) {
			throw new APIException("Capture.error.outsideStorage", new Object[] { uri });
		}
		return file;
	}

	private File storageDirectory() {
		return OpenmrsUtil.getDirectoryInApplicationDataDirectory(
		    globalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_DIR, "captures"));
	}

	private int retentionDays() {
		String days = globalProperty(OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS,
		    String.valueOf(DEFAULT_RETENTION_DAYS));
		try {
			return Integer.parseInt(days.trim());
		} catch (NumberFormatException e) {
			log.warn("{} is not a number of days: {}; using {}", OpenmrsConstants.GLOBAL_PROPERTY_CAPTURE_RETENTION_DAYS,
			    days, DEFAULT_RETENTION_DAYS);
			return DEFAULT_RETENTION_DAYS;
		}
	}

	private String globalProperty(String name, String defaultValue) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			return Context.getAdministrationService().getGlobalProperty(name, defaultValue);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
	}
}
