/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.ObsService;
import org.openmrs.parameter.ObsSearchCriteria;
import org.openmrs.util.OpenmrsConstants.PERSON_TYPE;

/**
 * Observation-related database functions
 *
 * @see org.openmrs.api.ObsService
 */
public interface ObsDAO {

	/**
	 * @see org.openmrs.api.ObsService#saveObs(org.openmrs.Obs, String)
	 */
	public Obs saveObs(Obs obs) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObs(java.lang.Integer)
	 */
	public Obs getObs(Integer obsId) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#purgeObs(Obs)
	 */
	public void deleteObs(Obs obs) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.lang.Integer, java.util.Date, java.util.Date, boolean, java.lang.String)
	 */
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs,
	        String accessionNumber) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.util.Date, java.util.Date, boolean, java.lang.String)
	 * @see ObsService#getObservationCount(List, boolean)
	 */
	public Long getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, Integer obsGroupId,
	        Date fromDate, Date toDate, List<ConceptName> valueCodedNameAnswers, boolean includeVoidedObs,
	        String accessionNumber) throws DAOException;

	/**
	 * @param uuid
	 * @return obs or null
	 */
	public Obs getObsByUuid(String uuid);

	/**
	 * @see org.openmrs.api.ObsService#getRevisionObs(org.openmrs.Obs)
	 * @param initialObs
	 * @return Obs or null
	 */
	public Obs getRevisionObs(Obs initialObs);

	/**
	 * @param obs a saved obs
	 * @return true if a newer version of the obs has been saved, one whose previous version it is
	 * @since 3.0.0
	 */
	public boolean hasNewerVersion(Obs obs);

	/**
	 * @see org.openmrs.api.ObsService#getObsFinalizationPolicyByUuid(String)
	 */
	public ObsFinalizationPolicy getObsFinalizationPolicyByUuid(String uuid);

	/**
	 * @see org.openmrs.api.ObsService#getAllObsFinalizationPolicies(boolean)
	 */
	public List<ObsFinalizationPolicy> getAllObsFinalizationPolicies(boolean includeRetired);

	/**
	 * @see org.openmrs.api.ObsService#saveObsFinalizationPolicy(ObsFinalizationPolicy)
	 */
	public ObsFinalizationPolicy saveObsFinalizationPolicy(ObsFinalizationPolicy policy);

	/**
	 * Gets the value of status currently saved in the database for the given obs, bypassing any caches.
	 * This is used when the user updates an existing obs so we can determine whether to change its
	 * status or not.
	 *
	 * @param obs
	 * @return
	 * @since 2.1.0
	 */
	public Obs.Status getSavedStatus(Obs obs);

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.util.List,
	 *      java.lang.Integer, java.lang.Integer, java.util.Date, java.util.Date, boolean,
	 *      java.lang.String)
	 */
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sortList,
	        List<Visit> visits, Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        boolean includeVoidedObs, String accessionNumber) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObservations(ObsSearchCriteria)
	 * @since 2.8.10
	 */
	public List<Obs> getObservations(ObsSearchCriteria obsSearchCriteria) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.util.List,
	 *      java.lang.Integer, java.lang.Integer, java.util.Date, java.util.Date, boolean,
	 *      java.lang.String, java.lang.Integer, java.lang.Integer)
	 * @since 2.8.10
	 */
	@SuppressWarnings("squid:S107")
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sortList,
	        List<Visit> visits, Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        boolean includeVoidedObs, String accessionNumber, Integer startIndex, Integer maxResults) throws DAOException;

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.util.Date, java.util.Date, java.util.List, java.util.List, boolean, java.lang.String)
	 * @see ObsService#getObservationCount(List, boolean)
	 */
	public Long getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, Integer obsGroupId,
	        Date fromDate, Date toDate, List<ConceptName> valueCodedNameAnswers, List<Visit> visits,
	        boolean includeVoidedObs, String accessionNumber) throws DAOException;

	/**
	 * Runs work that queries the database without first flushing the session, so an obs being edited
	 * inside a service method is not written before the service decides what to do with it.
	 *
	 * @param work the work to run
	 * @return what the work returns
	 */
	<T> T withoutAutoFlush(Supplier<T> work);

	/**
	 * @see ObsService#getObservationsByAsserter(Provider, Integer, Integer)
	 */
	List<Obs> getObservationsByAsserter(Provider asserter, Integer startIndex, Integer limit);

	/**
	 * @see ObsService#getObservationsBySoftwareVersion(String, String, Integer, Integer)
	 */
	List<Obs> getObservationsBySoftwareVersion(String name, String version, Integer startIndex, Integer limit);
}
