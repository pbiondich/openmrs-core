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
 * table with a row for the Daemon user.
 */
public class TypedPrincipalsChangeSetsDatabaseIT extends DatabaseIT {

	private static final int TYPED_PRINCIPALS_CHANGE_SET_COUNT = 4;

	@Test
	public void shouldTypeExistingUsersAndRollBackCleanly() throws Exception {
		startFromEmptyDatabase();
		ChangeLogVersionFinder finder = new ChangeLogVersionFinder();
		String latestSnapshot = finder.getLatestSnapshotVersion().get();
		List<String> changeLogs = finder.getChangeLogCombinations().get(latestSnapshot);
		updateDatabase(changeLogs);

		try (Connection connection = getConnection()) {
			assertEquals("SOFTWARE", getUserType(connection, "daemon"));
			assertEquals("HUMAN", getUserType(connection, "admin"));
			assertTrue(isPersonIdNullable(connection));
			assertTrue(hasColumn(connection, "software_agent", "user_id"));
		}

		// the typed principals changesets are the last ones in the latest update file
		Liquibase liquibase = getLiquibase(changeLogs.get(changeLogs.size() - 1));
		liquibase.rollback(TYPED_PRINCIPALS_CHANGE_SET_COUNT, (String) null);
		liquibase.getDatabase().getConnection().commit();

		try (Connection connection = getConnection()) {
			assertFalse(hasColumn(connection, "users", "user_type"));
			assertFalse(hasColumn(connection, "software_agent", "user_id"));
			assertFalse(isPersonIdNullable(connection));
		}
	}

	/**
	 * A container database arrives with the schema and updates already applied but no core data, so the
	 * Daemon user would not exist when the changesets run. PostgreSQL also keeps the uuid() function
	 * that the 3.0.x updates create, which dropping the tables leaves behind.
	 */
	private void startFromEmptyDatabase() throws SQLException {
		dropAllDatabaseObjects();
		if ("postgres".equalsIgnoreCase(System.getProperty("database"))) {
			try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
				statement.execute("DROP FUNCTION IF EXISTS uuid()");
				connection.commit();
			}
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
