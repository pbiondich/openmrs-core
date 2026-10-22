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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openmrs.liquibase.ChangeLogVersionFinder;
import org.openmrs.util.DatabaseIT;

import liquibase.Liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the assertion model phase 1 changesets in liquibase-update-to-latest-3.0.x.xml:
 * users.user_type, a nullable users.person_id, the Daemon user as SOFTWARE, and the software_agent
 * table with a row for the Daemon user, and the phase 2 obs finalization policies and attestations.
 */
public class TypedPrincipalsChangeSetsDatabaseIT extends DatabaseIT {

	private static final int ASSERTION_MODEL_CHANGE_SET_COUNT = 19;

	@Test
	public void shouldTypeExistingUsersAndRollBackCleanly() throws Exception {
		startFromEmptyDatabase();
		ChangeLogVersionFinder finder = new ChangeLogVersionFinder();
		String latestSnapshot = finder.getLatestSnapshotVersion().get();
		List<String> changeLogs = finder.getChangeLogCombinations().get(latestSnapshot);
		// the snapshots, then rows for the asserter backfill to fill, then the updates
		String latestUpdates = changeLogs.get(changeLogs.size() - 1);
		updateDatabase(changeLogs.subList(0, changeLogs.size() - 1));
		insertEncountersWithOneTwoAndNoProviders();
		updateDatabase(latestUpdates);

		try (Connection connection = getConnection()) {
			assertEquals(Integer.valueOf(1), getAsserterId(connection, 1));
			assertEquals(null, getAsserterId(connection, 2));
			assertEquals(null, getAsserterId(connection, 3));
		}

		try (Connection connection = getConnection()) {
			assertEquals("SOFTWARE", getUserType(connection, "daemon"));
			assertEquals(null, getPersonId(connection, "daemon"));
			assertEquals("HUMAN", getUserType(connection, "admin"));
			assertTrue(isPersonIdNullable(connection));
			assertTrue(hasColumn(connection, "software_agent", "user_id"));
			assertTrue(hasColumn(connection, "device_agent", "user_id"));
			assertTrue(hasColumn(connection, "provider", "user_id"));
			assertTrue(hasColumn(connection, "provider", "provider_type"));
			assertEquals("AUTO_FINAL", getFinalizationAction(connection, "DEVICE"));
			assertEquals("REQUIRE_ATTESTATION", getFinalizationAction(connection, "SOFTWARE"));
			assertTrue(hasColumn(connection, "obs_attestation_item", "outcome"));
			assertTrue(hasColumn(connection, "encounter", "dictionary_version_id"));
			assertTrue(hasColumn(connection, "capture", "artifact_key"));
			assertTrue(hasColumn(connection, "derivation", "agent_user_id"));
			assertTrue(hasColumn(connection, "obs", "span"));
		}

		// the assertion model changesets are the last ones in the latest update file
		Liquibase liquibase = getLiquibase(changeLogs.get(changeLogs.size() - 1));
		liquibase.rollback(ASSERTION_MODEL_CHANGE_SET_COUNT, (String) null);
		liquibase.getDatabase().getConnection().commit();

		try (Connection connection = getConnection()) {
			assertFalse(hasColumn(connection, "users", "user_type"));
			assertFalse(hasColumn(connection, "device_agent", "user_id"));
			assertFalse(hasColumn(connection, "provider", "user_id"));
			assertFalse(hasColumn(connection, "provider", "provider_type"));
			assertFalse(hasColumn(connection, "obs", "asserter_id"));
			assertFalse(hasColumn(connection, "software_agent", "user_id"));
			assertFalse(hasColumn(connection, "obs_finalization_policy", "action"));
			assertFalse(hasColumn(connection, "obs_attestation_item", "outcome"));
			assertFalse(hasColumn(connection, "obs_attestation", "attester_id"));
			assertFalse(hasColumn(connection, "encounter", "dictionary_version_id"));
			assertFalse(hasColumn(connection, "dictionary_version", "label"));
			assertFalse(hasColumn(connection, "capture", "artifact_key"));
			assertFalse(hasColumn(connection, "derivation", "agent_user_id"));
			assertFalse(hasColumn(connection, "obs", "capture_id"));
			assertFalse(hasColumn(connection, "obs", "span"));
			assertFalse(hasColumn(connection, "obs", "derivation_id"));
			assertFalse(isPersonIdNullable(connection));
			assertEquals(Integer.valueOf(1), getPersonId(connection, "daemon"));
		}
	}

	/**
	 * A container database arrives with the schema and updates already applied but no core data, so the
	 * Daemon user would not exist when the changesets run. PostgreSQL also keeps the uuid() function
	 * that the 3.0.x updates create, which dropping the tables leaves behind.
	 */
	private String getFinalizationAction(Connection connection, String agentType) throws SQLException {
		try (Statement statement = connection.createStatement();
		        ResultSet rs = statement.executeQuery("SELECT action FROM obs_finalization_policy WHERE agent_type = '"
		                + agentType + "' AND concept_class_id IS NULL")) {
			assertTrue(rs.next(), "no policy for " + agentType);
			return rs.getString(1);
		}
	}

	private void startFromEmptyDatabase() throws SQLException {
		dropAllDatabaseObjects();
		if ("postgres".equalsIgnoreCase(System.getProperty("database"))) {
			try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
				statement.execute("DROP FUNCTION IF EXISTS uuid()");
				connection.commit();
			}
		}
	}

	private void insertEncountersWithOneTwoAndNoProviders() throws SQLException {
		String[] inserts = {
		        "INSERT INTO patient (patient_id, creator, date_created, voided) VALUES (1, 1, '2026-01-01', FALSE)",
		        "INSERT INTO encounter_type (encounter_type_id, name, creator, date_created, retired, uuid) "
		                + "VALUES (1, 'Visit', 1, '2026-01-01', FALSE, 'et-1')",
		        "INSERT INTO provider (provider_id, person_id, identifier, creator, date_created, retired, uuid) "
		                + "VALUES (1, 1, 'P1', 1, '2026-01-01', FALSE, 'p-1')",
		        "INSERT INTO provider (provider_id, person_id, identifier, creator, date_created, retired, uuid) "
		                + "VALUES (2, 1, 'P2', 1, '2026-01-01', FALSE, 'p-2')" };
		try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
			for (String insert : inserts) {
				statement.execute(insert);
			}
			// encounter 1 has one provider, encounter 2 has two, encounter 3 has none; each has one obs
			for (int encounter = 1; encounter <= 3; encounter++) {
				statement.execute("INSERT INTO encounter (encounter_id, encounter_type, patient_id, encounter_datetime, "
				        + "creator, date_created, voided, uuid) VALUES (" + encounter + ", 1, 1, '2026-01-01', 1, "
				        + "'2026-01-01', FALSE, 'e-" + encounter + "')");
				statement.execute("INSERT INTO obs (obs_id, person_id, concept_id, encounter_id, obs_datetime, creator, "
				        + "date_created, voided, uuid) VALUES (" + encounter + ", 1, 1, " + encounter + ", '2026-01-01', 1, "
				        + "'2026-01-01', FALSE, 'o-" + encounter + "')");
			}
			statement.execute("INSERT INTO encounter_provider (encounter_id, provider_id, encounter_role_id, creator, "
			        + "date_created, voided, uuid) VALUES (1, 1, 1, 1, '2026-01-01', FALSE, 'ep-1')");
			statement.execute("INSERT INTO encounter_provider (encounter_id, provider_id, encounter_role_id, creator, "
			        + "date_created, voided, uuid) VALUES (2, 1, 1, 1, '2026-01-01', FALSE, 'ep-2')");
			statement.execute("INSERT INTO encounter_provider (encounter_id, provider_id, encounter_role_id, creator, "
			        + "date_created, voided, uuid) VALUES (2, 2, 1, 1, '2026-01-01', FALSE, 'ep-3')");
			connection.commit();
		}
	}

	private Integer getPersonId(Connection connection, String systemId) throws SQLException {
		try (Statement statement = connection.createStatement();
		        ResultSet rs = statement.executeQuery("SELECT person_id FROM users WHERE system_id = '" + systemId + "'")) {
			assertTrue(rs.next(), "no user with system_id " + systemId);
			int personId = rs.getInt(1);
			return rs.wasNull() ? null : personId;
		}
	}

	private Integer getAsserterId(Connection connection, int obsId) throws SQLException {
		try (Statement statement = connection.createStatement();
		        ResultSet rs = statement.executeQuery("SELECT asserter_id FROM obs WHERE obs_id = " + obsId)) {
			assertTrue(rs.next(), "no obs " + obsId);
			int asserterId = rs.getInt(1);
			return rs.wasNull() ? null : asserterId;
		}
	}

	private String getUserType(Connection connection, String systemId) throws SQLException {
		try (Statement statement = connection.createStatement();
		        ResultSet rs = statement.executeQuery("SELECT user_type FROM users WHERE system_id = '" + systemId + "'")) {
			assertTrue(rs.next(), "no user with system_id " + systemId);
			return rs.getString(1);
		}
	}

	private boolean isPersonIdNullable(Connection connection) throws SQLException {
		try (ResultSet rs = findColumn(connection.getMetaData(), "users", "person_id")) {
			assertTrue(rs.next(), "users.person_id not found");
			return rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
		}
	}

	private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
		try (ResultSet rs = findColumn(connection.getMetaData(), table, column)) {
			return rs.next();
		}
	}

	private ResultSet findColumn(DatabaseMetaData metaData, String table, String column) throws SQLException {
		// identifier case differs between H2 (upper), MySQL and PostgreSQL (lower)
		String catalog = metaData.getConnection().getCatalog();
		ResultSet rs = metaData.getColumns(catalog, null, table, column);
		if (rs.isBeforeFirst()) {
			return rs;
		}
		rs.close();
		return metaData.getColumns(catalog, null, table.toUpperCase(), column.toUpperCase());
	}
}
