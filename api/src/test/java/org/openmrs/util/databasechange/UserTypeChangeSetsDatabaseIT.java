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
 * Tests the assertion model changesets in liquibase-update-to-latest-3.0.x.xml that add
 * users.user_type, make users.person_id nullable and mark the Daemon user as SOFTWARE.
 */
public class UserTypeChangeSetsDatabaseIT extends DatabaseIT {

	private static final int USER_TYPE_CHANGE_SET_COUNT = 3;

	@Test
	public void shouldTypeExistingUsersAndRollBackCleanly() throws Exception {
		ChangeLogVersionFinder finder = new ChangeLogVersionFinder();
		String latestSnapshot = finder.getLatestSnapshotVersion().get();
		List<String> changeLogs = finder.getChangeLogCombinations().get(latestSnapshot);
		updateDatabase(changeLogs);

		try (Connection connection = getConnection()) {
			assertEquals("SOFTWARE", getUserType(connection, "daemon"));
			assertEquals("HUMAN", getUserType(connection, "admin"));
			assertTrue(isPersonIdNullable(connection));
		}

		// the three user type changesets are the last ones in the latest update file
		Liquibase liquibase = getLiquibase(changeLogs.get(changeLogs.size() - 1));
		liquibase.rollback(USER_TYPE_CHANGE_SET_COUNT, (String) null);
		liquibase.getDatabase().getConnection().commit();

		try (Connection connection = getConnection()) {
			assertFalse(hasColumn(connection, "users", "user_type"));
			assertFalse(isPersonIdNullable(connection));
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
