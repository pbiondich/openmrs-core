/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate;

import java.util.Date;
import java.util.List;

import org.hibernate.SessionFactory;
import org.openmrs.Capture;
import org.openmrs.Encounter;
import org.openmrs.api.db.CaptureDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Hibernate implementation of {@link CaptureDAO}.
 *
 * @since 3.0.0
 */
@Repository("captureDAO")
public class HibernateCaptureDAO implements CaptureDAO {

	private final SessionFactory sessionFactory;

	@Autowired
	public HibernateCaptureDAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Capture saveCapture(Capture capture) {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), capture);
	}

	@Override
	public Capture getCaptureByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, Capture.class, uuid);
	}

	@Override
	public List<Capture> getCapturesByEncounter(Encounter encounter) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Capture where encounter = :encounter and voided = false order by captureId",
		            Capture.class)
		        .setParameter("encounter", encounter).list();
	}

	@Override
	public List<Capture> getCapturesWithArtifactsToDelete(Date now) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Capture where retentionClass = :expire and artifactDeletedAt is null"
		                + " and (expiresAt <= :now or consentStatus = :withdrawn) order by captureId",
		            Capture.class)
		        .setParameter("expire", Capture.RetentionClass.EXPIRE).setParameter("now", now)
		        .setParameter("withdrawn", Capture.ConsentStatus.WITHDRAWN).list();
	}
}
