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

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.Capture;
import org.openmrs.Derivation;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.CaptureDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Hibernate implementation of {@link CaptureDAO}. Consent and deletion are changed with bulk
 * updates, because the mapping makes those columns read-only once a capture is saved.
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
	public Capture reload(Capture capture) {
		Session session = sessionFactory.getCurrentSession();
		Capture managed = session.contains(capture) ? capture : session.get(Capture.class, capture.getCaptureId());
		if (managed != null) {
			// flush first so a refresh does not lose a pending void; refresh, since bulk updates bypass the
			// session
			session.flush();
			session.refresh(managed);
		}
		return managed;
	}

	@Override
	public Obs getReadableArtifactObs(Integer captureId) {
		return sessionFactory.getCurrentSession().createQuery(
		    "select c.obs from Capture c where c.captureId = :id and c.voided = false" + " and c.artifactDeletedAt is null",
		    Obs.class).setParameter("id", captureId).uniqueResult();
	}

	@Override
	public void voidArtifactObs(String valueComplex, String reason, Date voidedAt) {
		// through the session, so Envers and the obs interceptors see the void
		for (Obs obs : sessionFactory.getCurrentSession()
		        .createQuery("from Obs where valueComplex = :value and voided = false", Obs.class)
		        .setParameter("value", valueComplex).list()) {
			obs.setVoided(true);
			obs.setVoidedBy(Context.getAuthenticatedUser());
			obs.setDateVoided(voidedAt);
			obs.setVoidReason(reason);
		}
	}

	@Override
	public void finalizeArtifactObs(Obs obs) {
		sessionFactory.getCurrentSession().createMutationQuery("update Obs set status = :final where obsId = :id")
		        .setParameter("final", Obs.Status.FINAL).setParameter("id", obs.getObsId()).executeUpdate();
		sessionFactory.getCurrentSession().refresh(obs);
	}

	@Override
	public boolean hasNewerVersion(Obs obs) {
		return !sessionFactory.getCurrentSession()
		        .createQuery("select o.obsId from Obs o where o.previousVersion = :obs", Integer.class)
		        .setParameter("obs", obs).setMaxResults(1).list().isEmpty();
	}

	@Override
	public List<Capture> getCapturesWithArtifactsToDelete(Date now) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Capture where artifactDeletedAt is null and expiresAt is not null"
		                + " and (expiresAt <= :now or consentStatus = :withdrawn) order by captureId",
		            Capture.class)
		        .setParameter("now", now).setParameter("withdrawn", Capture.ConsentStatus.WITHDRAWN).list();
	}

	@Override
	public void withdrawConsent(Integer captureId) {
		sessionFactory.getCurrentSession()
		        .createMutationQuery("update Capture set consentStatus = :withdrawn, changedBy = :user,"
		                + " dateChanged = :now where captureId = :id")
		        .setParameter("withdrawn", Capture.ConsentStatus.WITHDRAWN)
		        .setParameter("user", Context.getAuthenticatedUser()).setParameter("now", new Date())
		        .setParameter("id", captureId).executeUpdate();
	}

	@Override
	public void markArtifactDeleted(Integer captureId, Date deletedAt) {
		sessionFactory.getCurrentSession()
		        .createMutationQuery("update Capture set artifactDeletedAt = :deletedAt, changedBy = :user,"
		                + " dateChanged = :deletedAt where captureId = :id and artifactDeletedAt is null")
		        .setParameter("deletedAt", deletedAt).setParameter("user", Context.getAuthenticatedUser())
		        .setParameter("id", captureId).executeUpdate();
	}

	@Override
	public Derivation saveDerivation(Derivation derivation) {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), derivation);
	}

	@Override
	public Derivation getDerivationByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, Derivation.class, uuid);
	}

	@Override
	public List<Derivation> getDerivationsByCapture(Capture capture) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Derivation where capture = :capture and voided = false order by derivationId",
		            Derivation.class)
		        .setParameter("capture", capture).list();
	}
}
