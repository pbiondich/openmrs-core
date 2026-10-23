/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.util.databasechange;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Gives each existing complex obs that holds a capture-like artifact (a scanned PDF, an image, a
 * recording, a video or plain text) a capture of its own, so artifacts recorded before captures
 * existed can be pointed into like new ones. Decided with Paul:
 * <ul>
 * <li>the complex obs and its file are left exactly as they are; the capture only points at the
 * obs</li>
 * <li>a file whose type fits no capture kind, such as a Word document, is skipped</li>
 * <li>a backfilled capture never expires, audio and video included, since nothing promised those
 * files would be deleted</li>
 * <li>a complex obs without an encounter is skipped, since a capture belongs to an encounter</li>
 * <li>a form entry with no file gets no capture: the obs already carries its form path</li>
 * </ul>
 * The type is read from the file name's extension, the obs being all the database knows of it;
 * backfilled captures have no checksum, since the file is not read.
 *
 * @since 3.0.0
 */
public class ComplexObsCaptureBackfillChangeSet implements CustomTaskChange {

	private static final Logger log = LoggerFactory.getLogger(ComplexObsCaptureBackfillChangeSet.class);

	private static final Map<String, String> MEDIA_TYPES = Map.ofEntries(Map.entry("pdf", "application/pdf"),
	    Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
	    Map.entry("gif", "image/gif"), Map.entry("tif", "image/tiff"), Map.entry("tiff", "image/tiff"),
	    Map.entry("bmp", "image/bmp"), Map.entry("webp", "image/webp"), Map.entry("mp3", "audio/mpeg"),
	    Map.entry("wav", "audio/wav"), Map.entry("ogg", "audio/ogg"), Map.entry("m4a", "audio/mp4"),
	    Map.entry("aac", "audio/aac"), Map.entry("amr", "audio/amr"), Map.entry("mp4", "video/mp4"),
	    Map.entry("mov", "video/quicktime"), Map.entry("webm", "video/webm"), Map.entry("3gp", "video/3gpp"),
	    Map.entry("txt", "text/plain"));

	private int backfilled;

	private int skipped;

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			backfill((JdbcConnection) database.getConnection());
		} catch (SQLException | DatabaseException e) {
			throw new CustomChangeException("Failed to backfill captures from complex obs", e);
		}
		log.info(getConfirmationMessage());
	}

	void backfill(JdbcConnection connection) throws SQLException, DatabaseException {
		Timestamp now = new Timestamp(System.currentTimeMillis());
		try (PreparedStatement select = connection
		        .prepareStatement("SELECT o.obs_id, o.encounter_id, o.value_complex, o.creator FROM obs o WHERE o.voided = ?"
		                + " AND o.value_complex IS NOT NULL AND o.encounter_id IS NOT NULL"
		                + " AND NOT EXISTS (SELECT 1 FROM capture c WHERE c.obs_id = o.obs_id) ORDER BY o.obs_id");
		        PreparedStatement insert = connection.prepareStatement("INSERT INTO capture (encounter_id, kind, obs_id,"
		                + " mime_type, consent_status, creator, date_created, voided, uuid)"
		                + " VALUES (?, ?, ?, ?, 'NOT_RECORDED', ?, ?, ?, ?)")) {
			select.setBoolean(1, false);
			try (ResultSet rs = select.executeQuery()) {
				while (rs.next()) {
					String mimeType = mediaType(rs.getString("value_complex"));
					String kind = mimeType == null ? null : kind(mimeType);
					if (kind == null) {
						skipped++;
						continue;
					}
					insert.setInt(1, rs.getInt("encounter_id"));
					insert.setString(2, kind);
					insert.setInt(3, rs.getInt("obs_id"));
					insert.setString(4, mimeType);
					insert.setInt(5, rs.getInt("creator"));
					insert.setTimestamp(6, now);
					insert.setBoolean(7, false);
					insert.setString(8, UUID.randomUUID().toString());
					insert.addBatch();
					if (++backfilled % 500 == 0) {
						insert.executeBatch();
					}
				}
			}
			insert.executeBatch();
		}
	}

	/**
	 * @param valueComplex a complex obs's value: its title, then its file's name or storage key
	 * @return the media type the file's extension names, the file's own extension before the title's;
	 *         null if neither names one a capture can be
	 */
	static String mediaType(String valueComplex) {
		String[] names = valueComplex.split("\\|");
		for (int i = names.length - 1; i >= 0; i--) {
			// BinaryDataHandler appends " file" to the title, and ImageHandler " image"
			String name = StringUtils.removeEnd(StringUtils.removeEnd(StringUtils.trim(names[i]), " file"), " image");
			String extension = FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
			if (MEDIA_TYPES.containsKey(extension)) {
				return MEDIA_TYPES.get(extension);
			}
		}
		return null;
	}

	/**
	 * A scanned PDF is a scanned form; an image is an image, since nothing says it is a scan.
	 */
	static String kind(String mimeType) {
		if (mimeType.equals("application/pdf")) {
			return "SCANNED_FORM";
		} else if (mimeType.startsWith("image/")) {
			return "IMAGE";
		} else if (mimeType.startsWith("audio/")) {
			return "AUDIO";
		} else if (mimeType.startsWith("video/")) {
			return "VIDEO";
		} else if (mimeType.equals("text/plain")) {
			return "TYPED_TEXT";
		}
		return null;
	}

	@Override
	public String getConfirmationMessage() {
		return "Backfilled " + backfilled + " captures from complex obs; skipped " + skipped
		        + " whose file type fits no capture kind";
	}

	@Override
	public void setUp() throws SetupException {
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
	}

	@Override
	public ValidationErrors validate(Database database) {
		return null;
	}
}
