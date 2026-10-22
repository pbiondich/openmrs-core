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

import java.util.Collection;
import java.util.List;

import org.hibernate.SessionFactory;
import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.Person;
import org.openmrs.api.db.AttestationDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Hibernate implementation of {@link AttestationDAO}.
 *
 * @since 3.0.0
 */
@Repository("attestationDAO")
public class HibernateAttestationDAO implements AttestationDAO {

	private final SessionFactory sessionFactory;

	@Autowired
	public HibernateAttestationDAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public ObsAttestation saveObsAttestation(ObsAttestation attestation) {
		return HibernateUtil.saveOrUpdate(sessionFactory.getCurrentSession(), attestation);
	}

	@Override
	public ObsAttestation getObsAttestationByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, ObsAttestation.class, uuid);
	}

	@Override
	public List<ObsAttestationItem> getObsAttestationItems(Obs obs) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ObsAttestationItem where obs = :obs order by obsAttestationItemId",
		            ObsAttestationItem.class)
		        .setParameter("obs", obs).list();
	}

	@Override
	public List<Obs> getPendingObs(Person person) {
		// a rejected obs is voided, so what is preliminary and not voided is what still waits
		return sessionFactory.getCurrentSession()
		        .createQuery("from Obs o where o.person = :person and o.voided = false and o.status = :preliminary"
		                + " order by o.obsDatetime, o.obsId",
		            Obs.class)
		        .setParameter("person", person).setParameter("preliminary", Obs.Status.PRELIMINARY).list();
	}

	@Override
	public List<Object[]> getMembers(Integer groupId) {
		// read from the database, not from the group as the caller holds it
		return sessionFactory.getCurrentSession()
		        .createQuery("select o.obsId, o.status from Obs o where o.obsGroup.obsId = :groupId and o.voided = false",
		            Object[].class)
		        .setParameter("groupId", groupId).list();
	}

	@Override
	public int finalizeObs(Collection<Integer> obsIds) {
		// a bulk update, so the finalization is not an edit: no new version, and nothing the obs
		// interceptor or an obs's dirty flag would treat as one
		return sessionFactory.getCurrentSession()
		        .createMutationQuery(
		            "update Obs set status = :final where obsId in (:ids) and status = :preliminary" + " and voided = false")
		        .setParameter("final", Obs.Status.FINAL).setParameter("preliminary", Obs.Status.PRELIMINARY)
		        .setParameterList("ids", obsIds).executeUpdate();
	}
}
