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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.UserType;
import org.openmrs.Visit;
import org.openmrs.aop.RequiredDataAdvice;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.RefByUuid;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.api.db.ObsDAO;
import org.openmrs.api.handler.SaveHandler;
import org.openmrs.obs.ComplexData;
import org.openmrs.obs.ComplexObsHandler;
import org.openmrs.parameter.ObsSearchCriteria;
import org.openmrs.parameter.ObsSearchCriteriaBuilder;
import org.openmrs.util.OpenmrsClassLoader;
import org.openmrs.util.OpenmrsConstants.PERSON_TYPE;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of the Observation Service
 *
 * @see org.openmrs.api.ObsService
 */
@Service("obsService")
@Transactional
public class ObsServiceImpl extends BaseOpenmrsService implements ObsService, RefByUuid {

	private static final Logger log = LoggerFactory.getLogger(ObsServiceImpl.class);

	/**
	 * The data access object for the obs service
	 */
	@Autowired
	protected ObsDAO dao;

	/**
	 * Report handlers that have been registered. This is filled via {@link #setHandlers(Map)} and
	 * spring's applicationContext-service.xml object
	 */
	@Autowired
	@Qualifier("handlers")
	private Map<String, ComplexObsHandler> handlers;

	/**
	 * Default empty constructor for this obs service
	 */
	public ObsServiceImpl() {
	}

	/**
	 * @see org.openmrs.api.ObsService#setObsDAO(org.openmrs.api.db.ObsDAO)
	 */
	@Override
	public void setObsDAO(ObsDAO dao) {
		this.dao = dao;
	}

	/**
	 * Clean up after this class. Set the static var to null so that the classloader can reclaim the
	 * space.
	 *
	 * @see org.openmrs.api.impl.BaseOpenmrsService#onShutdown()
	 */
	@Override
	public void onShutdown() {
		setHandlers(null);
	}

	/**
	 * @see org.openmrs.api.ObsService#saveObs(org.openmrs.Obs, String)
	 */
	@Override
	public Obs saveObs(Obs obs, String changeMessage) throws APIException {
		if (obs == null) {
			throw new APIException("Obs.error.cannot.be.null", (Object[]) null);
		}

		if (obs.getId() != null && changeMessage == null) {
			throw new APIException("Obs.error.ChangeMessage.required", (Object[]) null);
		}

		ensureRequirePrivilege(obs);

		// an obs that is not voided was not entered in error, whether it was unvoided through unvoidObs
		// or by clearing its voided flag
		reenterUnvoided(obs);

		//Should allow updating a voided Obs, it seems to be pointless to restrict it,
		//otherwise operations like merge patients won't be possible when to moving voided obs
		if (obs.getObsId() == null || obs.getVoided()) {
			return saveNewOrVoidedObs(obs, changeMessage);
		} else if (!obs.isDirty()) {
			setPersonFromEncounter(obs);
			return saveObsNotDirty(obs, changeMessage);
		} else {
			setPersonFromEncounter(obs);
			return saveExistingObs(obs, changeMessage);
		}
	}

	private void setPersonFromEncounter(Obs obs) {
		Encounter encounter = obs.getEncounter();
		if (encounter != null) {
			obs.setPerson(encounter.getPatient());
		}
	}

	private void voidExistingObs(Obs obs, String changeMessage, Obs newObs) {
		// void out the original observation to keep it around for
		// historical purposes
		try {
			Context.addProxyPrivilege(PrivilegeConstants.DELETE_OBS);

			// fetch a clean copy of this obs from the database so that
			// we don't write the changes to the database when we save
			// the fact that the obs is now voided
			evictObsAndChildren(obs);
			obs = Context.getObsService().getObs(obs.getObsId());
			// delete the previous file from the appdata/complex_obs folder, unless the new version kept it or
			// a capture holds it
			Obs previous = newObs.getPreviousVersion();
			if (newObs.hasPreviousVersion() && previous.isComplex()
			        && !Objects.equals(previous.getValueComplex(), newObs.getValueComplex())
			        && !dao.isComplexDataCaptured(previous.getValueComplex())) {
				ComplexObsHandler handler = getHandler(newObs.getPreviousVersion());
				handler.purgeComplexData(newObs.getPreviousVersion());
			}
			// calling this via the service so that AOP hooks are called
			Context.getObsService().voidObs(obs, changeMessage);

		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.DELETE_OBS);
		}
	}

	private Obs saveExistingObs(Obs obs, String changeMessage) {
		// get a copy of the passed in obs and save it to the
		// database. This allows us to create a new row and new obs_id
		// this method doesn't copy the obs_id
		Obs newObs = Obs.newInstance(obs);

		unsetVoidedAndCreationProperties(newObs, obs);
		// the new row keeps the claim's asserter (Obs.newInstance copies it) unless the caller set another:
		// moving an encounter or merging patients re-saves obs without anyone re-asserting them
		handleObsWithComplexConcept(newObs);
		Obs.Status originalStatus = dao.getSavedStatus(obs);
		updateStatusIfNecessary(newObs, originalStatus);
		// an edit by an agent whose obs need attestation needs it too, and editing an obs that still
		// waits for attestation does not finalize it: only attestation does
		if (requiresAttestation(newObs, Obs.Status.PRELIMINARY.equals(originalStatus))) {
			newObs.setStatus(Obs.Status.PRELIMINARY);
		}

		RequiredDataAdvice.recursivelyHandle(SaveHandler.class, newObs, changeMessage);

		// save the new row to the database with the changes that
		// have been made to it
		dao.saveObs(newObs);

		saveObsGroup(newObs, null);

		voidExistingObs(obs, changeMessage, newObs);

		return newObs;

	}

	private void updateStatusIfNecessary(Obs newObs, Obs.Status originalStatus) {
		if (Obs.Status.FINAL.equals(originalStatus)) {
			newObs.setStatus(Obs.Status.AMENDED);
		}
	}

	private void unsetVoidedAndCreationProperties(Obs newObs, Obs obs) {
		newObs.setVoided(false);
		newObs.setVoidReason(null);
		newObs.setDateVoided(null);
		newObs.setVoidedBy(null);
		newObs.setCreator(null);
		newObs.setDateCreated(null);
		newObs.setPreviousVersion(obs);
	}

	private Obs saveObsNotDirty(Obs obs, String changeMessage) {
		if (!obs.isObsGrouping()) {
			return obs;
		}

		ObsService os = Context.getObsService();
		boolean refreshNeeded = false;
		for (Obs o : obs.getGroupMembers(true)) {
			if (o.getId() == null) {
				os.saveObs(o, null);
			} else {
				Obs newObs = os.saveObs(o, changeMessage);
				refreshNeeded = !newObs.equals(o) || refreshNeeded;
			}
		}

		if (refreshNeeded) {
			Context.flushSession();
			Context.refreshEntity(obs);
		}
		return obs;
	}

	private Obs saveNewOrVoidedObs(Obs obs, String changeMessage) {
		if (obs.getObsId() == null && obs.getAsserter() == null) {
			if (obs.getObsGroup() != null && obs.getObsGroup().getAsserter() != null) {
				// a group's members carry the claim its parent makes
				obs.setAsserter(obs.getObsGroup().getAsserter());
			} else {
				User creator = obs.getCreator() != null ? obs.getCreator() : Context.getAuthenticatedUser();
				obs.setAsserter(dao.withoutAutoFlush(() -> asserterFor(creator)));
			}
		}
		if (obs.getObsId() == null) {
			if (requiresAttestation(obs, true)) {
				obs.setStatus(Obs.Status.PRELIMINARY);
			}
		} else {
			// a voided obs is out of the obs interceptor's sight, so its status cannot change while it is
			// voided and carry over when it is unvoided. Entered in error is the exception: voidObs marks it,
			// and unvoiding gives the obs its status afresh
			if (!Obs.Status.ENTERED_IN_ERROR.equals(obs.getStatus())) {
				Obs.Status savedStatus = dao.getSavedStatus(obs);
				if (savedStatus != obs.getStatus()) {
					obs.setStatus(savedStatus);
				}
			}
		}
		handleObsWithComplexConcept(obs);
		Obs ret = dao.saveObs(obs);
		saveObsGroup(ret, changeMessage);
		return ret;
	}

	/**
	 * The provider who asserts what a user enters: a software or device user's digital provider, or a
	 * person's provider when the person has exactly one that is not retired. Anything else is unknown,
	 * and this returns null rather than guess.
	 */
	private Provider asserterFor(User user) {
		user = loadUser(user);
		if (user == null) {
			return null;
		}
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
			ProviderService providerService = Context.getProviderService();
			if (user.getUserType() != UserType.HUMAN) {
				return providerService.getProviderByUser(user);
			}
			if (user.getPerson() == null) {
				return null;
			}
			Collection<Provider> providers = providerService.getProvidersByPerson(user.getPerson(), false);
			return providers.size() == 1 ? providers.iterator().next() : null;
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
		}
	}

	private User loadUser(User user) {
		if (user == null || user.getUserId() == null) {
			return user;
		}
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_USERS);
			// callers such as the HL7 handler pass a user that carries only its id
			return Context.getUserService().getUser(user.getUserId());
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_USERS);
		}
	}

	/**
	 * Whether an obs must wait, preliminary, for a person to attest it: when the finalization policy
	 * for the authenticated user's kind of agent requires it, or, where the claim's asserter counts,
	 * the policy for the asserter's kind. The user making the call always counts, since a caller can
	 * name any asserter or creator. The Daemon saves obs for the system, from HL7 and scheduled tasks,
	 * and is no agent of its own.
	 */
	private boolean requiresAttestation(Obs obs, boolean asserterCounts) {
		return dao.withoutAutoFlush(() -> {
			ConceptClass conceptClass = obs.getConcept() != null ? obs.getConcept().getConceptClass() : null;
			User user = Context.getAuthenticatedUser();
			if (user != null && !Daemon.isDaemonUser(user) && requiresAttestation(user.getUserType(), conceptClass)) {
				return true;
			}
			return asserterCounts && requiresAttestation(agentTypeOf(obs.getAsserter()), conceptClass);
		});
	}

	private boolean requiresAttestation(UserType agentType, ConceptClass conceptClass) {
		ObsFinalizationPolicy policy = agentType != null ? getObsFinalizationPolicy(agentType, conceptClass) : null;
		return policy != null && policy.getAction() == ObsFinalizationPolicy.Action.REQUIRE_ATTESTATION;
	}

	/**
	 * The kind of agent a provider is: its user's type, or a person's for a provider without a user.
	 */
	private UserType agentTypeOf(Provider asserter) {
		if (asserter == null) {
			return null;
		}
		if (asserter.getProviderId() != null) {
			try {
				Context.addProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
				// a caller may set an asserter that carries only its id
				asserter = Context.getProviderService().getProvider(asserter.getProviderId());
			} finally {
				Context.removeProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
			}
		}
		User user = asserter != null ? asserter.getUser() : null;
		return user != null ? user.getUserType() : UserType.HUMAN;
	}

	/**
	 * Takes an obs, and the members of its group, out of {@link Obs.Status#ENTERED_IN_ERROR} once it is
	 * no longer voided. The status it had before is not kept: it gets the one a new obs would, final,
	 * or preliminary if its finalization policy requires attestation.
	 */
	private void reenterUnvoided(Obs obs) {
		if (obs.getVoided()) {
			return;
		}
		if (Obs.Status.ENTERED_IN_ERROR.equals(obs.getStatus())) {
			obs.setStatus(requiresAttestation(obs, true) ? Obs.Status.PRELIMINARY : Obs.Status.FINAL);
		}
		if (obs.hasGroupMembers()) {
			for (Obs member : obs.getGroupMembers()) {
				reenterUnvoided(member);
			}
		}
	}

	private void evictObsAndChildren(Obs obs) {
		Context.evictFromSession(obs);
		if (obs.hasGroupMembers()) {
			for (Obs member : obs.getGroupMembers()) {
				evictObsAndChildren(member);
			}
		}
	}

	private void ensureRequirePrivilege(Obs obs) {
		if (obs.getObsId() == null) {
			Context.requirePrivilege(PrivilegeConstants.ADD_OBS);
		} else {
			Context.requirePrivilege(PrivilegeConstants.EDIT_OBS);
		}
	}

	private void saveObsGroup(Obs obs, String changeMessage) {
		if (obs.isObsGrouping()) {
			for (Obs o : obs.getGroupMembers(true)) {
				Context.getObsService().saveObs(o, changeMessage);
			}
		}
	}

	private void handleObsWithComplexConcept(Obs obs) {
		ComplexData complexData = obs.getComplexData();
		Concept concept = obs.getConcept();
		if (null != concept && concept.isComplex() && null != complexData && null != complexData.getData()) {
			// save or update complexData object on this obs
			// this is done before the database save so that the obs.valueComplex
			// can be filled in by the handler.
			ComplexObsHandler handler = getHandler(obs);
			if (null != handler) {
				handler.saveObs(obs);
			} else {
				throw new APIException("unknown.handler", new Object[] { concept });
			}
		}
	}

	/**
	 * Loads a complex obs's data. A voided complex obs may no longer have its file, as when an edit
	 * replaced it or a capture's artifact was deleted, and is then returned without its data rather
	 * than failing.
	 */
	private Obs withComplexData(Obs obs) {
		try {
			return getHandler(obs).getObs(obs, ComplexObsHandler.RAW_VIEW);
		} catch (RuntimeException e) {
			if (!obs.getVoided()) {
				throw e;
			}
			log.debug("The file of voided complex obs {} could not be read", obs.getUuid(), e);
			return obs;
		}
	}

	/**
	 * @see org.openmrs.api.ObsService#getObs(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public Obs getObs(Integer obsId) throws APIException {
		Obs obs = dao.getObs(obsId);
		if (obs != null && obs.isComplex()) {
			return withComplexData(obs);
		}
		return obs;
	}

	/**
	 * Voids an Obs If the Obs argument is an obsGroup, all group members will be voided. The obs, and
	 * the members voided with it, are marked {@link Obs.Status#ENTERED_IN_ERROR} unless a newer version
	 * replaced it.
	 *
	 * @see org.openmrs.api.ObsService#voidObs(org.openmrs.Obs, java.lang.String)
	 * @param obs the Obs to void
	 * @param reason the void reason
	 * @throws APIException
	 */
	@Override
	public Obs voidObs(Obs obs, String reason) throws APIException {
		// voiding a version that a newer one replaced is how an edit is saved, not an error. Obs ids
		// are generated by the database, so the newer version is already inserted when it is saved
		// and the lookup needs no flush, which would trip over obs still being edited in the session
		if (obs.getObsId() == null || !dao.withoutAutoFlush(() -> dao.hasNewerVersion(obs))) {
			markEnteredInError(obs, obs.getDateVoided());
		}
		return dao.saveObs(obs);
	}

	private void markEnteredInError(Obs obs, Date dateVoided) {
		obs.setStatus(Obs.Status.ENTERED_IN_ERROR);
		if (obs.hasGroupMembers(true)) {
			for (Obs member : obs.getGroupMembers(true)) {
				// members voided earlier, on their own, keep the status they were voided with
				if (member.getVoided() && OpenmrsUtil.nullSafeEquals(dateVoided, member.getDateVoided())) {
					markEnteredInError(member, dateVoided);
				}
			}
		}
	}

	/**
	 * Unvoids an Obs
	 * <p>
	 * If the Obs argument is an obsGroup, all group members with the same dateVoided will also be
	 * unvoided.
	 *
	 * @see org.openmrs.api.ObsService#unvoidObs(org.openmrs.Obs)
	 * @param obs the Obs to unvoid
	 * @return the unvoided Obs
	 * @throws APIException
	 */
	@Override
	public Obs unvoidObs(Obs obs) throws APIException {
		return Context.getObsService().saveObs(obs, "unvoid obs");
	}

	/**
	 * @see org.openmrs.api.ObsService#purgeObs(org.openmrs.Obs, boolean)
	 */
	@Override
	public void purgeObs(Obs obs, boolean cascade) throws APIException {
		// a capture's artifact is part of the record: its obs, and any version sharing its file, stay
		if (obs != null && obs.isComplex() && dao.isComplexDataCaptured(obs.getValueComplex())) {
			throw new APIException("Obs.error.captured", new Object[] { obs.getUuid() });
		}
		if (!purgeComplexData(obs)) {
			// Log a warning instead of throwing an error.
			// This allows purging the obs row even if the associated file is missing,
			// which matches the behavior expected by modules like Attachments.
			log.warn("purgeComplexData returned false for Obs ID: " + (obs != null ? obs.getObsId() : "null") + ". "
			        + "This may mean the file is already missing. Proceeding to purge the database row.");
		}

		if (cascade) {
			throw new APIException("Obs.error.cascading.purge.not.implemented", (Object[]) null);
			// TODO delete any related objects here before deleting the obs
			// obsGroups objects?
			// orders?
		}
		dao.deleteObs(obs);
	}

	/**
	 * @see org.openmrs.api.ObsService#purgeObs(org.openmrs.Obs)
	 */
	@Override
	public void purgeObs(Obs obs) throws APIException {
		Context.getObsService().purgeObs(obs, false);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, List, List, java.util.List, java.lang.Integer, java.lang.Integer,
	 *      java.util.Date, java.util.Date, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs)
	        throws APIException {

		return dao.getObservations(whom, encounters, questions, answers, personTypes, locations, sort, mostRecentN,
		    obsGroupId, fromDate, toDate, includeVoidedObs, null);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, List, List, java.util.List, java.lang.Integer, java.lang.Integer,
	 *      java.util.Date, java.util.Date, boolean, java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs,
	        String accessionNumber) throws APIException {

		return this.getObservations(whom, encounters, questions, answers, personTypes, locations, sort, null, mostRecentN,
		    obsGroupId, fromDate, toDate, includeVoidedObs, accessionNumber);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, List, List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.lang.Integer, java.util.Date, java.util.Date, boolean, java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        List<Visit> visits, Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        boolean includeVoidedObs, String accessionNumber) throws APIException {

		return dao.getObservations(whom, encounters, questions, answers, personTypes, locations, sort, visits, mostRecentN,
		    obsGroupId, fromDate, toDate, includeVoidedObs, accessionNumber);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservations(ObsSearchCriteria)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservations(ObsSearchCriteria obsSearchCriteria) throws APIException {
		requireSearchCriteria(obsSearchCriteria);

		return dao.getObservations(obsSearchCriteria);
	}

	/**
	 * Every individual search parameter is optional, so passing null for the criteria object as a whole
	 * is an easy mistake to make. Caught at the service boundary it names the problem, where the DAO
	 * would only raise an NPE from inside a query.
	 *
	 * @param obsSearchCriteria the criteria to check
	 * @throws IllegalArgumentException if obsSearchCriteria is null
	 */
	private static void requireSearchCriteria(ObsSearchCriteria obsSearchCriteria) {
		if (obsSearchCriteria == null) {
			throw new IllegalArgumentException("obsSearchCriteria cannot be null");
		}
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservations(java.util.List, java.util.List, java.util.List,
	 *      java.util.List, List, List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.lang.Integer, java.util.Date, java.util.Date, boolean, java.lang.String,
	 *      java.lang.Integer, java.lang.Integer)
	 */
	@Override
	@SuppressWarnings("squid:S107")
	@Transactional(readOnly = true)
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        List<Visit> visits, Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        boolean includeVoidedObs, String accessionNumber, Integer startIndex, Integer maxResults) throws APIException {

		return this.getObservations(new ObsSearchCriteriaBuilder().setWhom(whom).setEncounters(encounters)
		        .setQuestions(questions).setAnswers(answers).setPersonTypes(personTypes).setLocations(locations)
		        .setSort(sort).setVisits(visits).setMostRecentN(mostRecentN).setObsGroupId(obsGroupId).setFromDate(fromDate)
		        .setToDate(toDate).setIncludeVoidedObs(includeVoidedObs).setAccessionNumber(accessionNumber)
		        .setStartIndex(startIndex).setMaxResults(maxResults).createObsSearchCriteria());
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.util.Date, java.util.Date, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, Integer obsGroupId,
	        Date fromDate, Date toDate, boolean includeVoidedObs) throws APIException {
		return OpenmrsUtil.convertToInteger(dao.getObservationCount(whom, encounters, questions, answers, personTypes,
		    locations, obsGroupId, fromDate, toDate, null, includeVoidedObs, null));
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.lang.Integer,
	 *      java.util.Date, java.util.Date, boolean, java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, Integer obsGroupId,
	        Date fromDate, Date toDate, boolean includeVoidedObs, String accessionNumber) throws APIException {
		return this.getObservationCount(whom, encounters, questions, answers, personTypes, locations, null, obsGroupId,
		    fromDate, toDate, includeVoidedObs, accessionNumber);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, java.util.List,
	 *      java.util.List, java.util.List, java.util.List, java.util.List, java.util.List,
	 *      java.lang.Integer, java.util.Date, java.util.Date, boolean, java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<Visit> visits,
	        Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs, String accessionNumber)
	        throws APIException {
		return OpenmrsUtil.convertToInteger(dao.getObservationCount(whom, encounters, questions, answers, personTypes,
		    locations, obsGroupId, fromDate, toDate, null, visits, includeVoidedObs, accessionNumber));
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(ObsSearchCriteria)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getObservationCount(ObsSearchCriteria obsSearchCriteria) throws APIException {
		requireSearchCriteria(obsSearchCriteria);

		// the sort and the row bounds the criteria carry say which of the matching rows come back, not
		// which rows match, so a count deliberately ignores them: a paging client counting the result set
		// it is walking wants the whole of it. valueCodedNameAnswers is passed as null because the search
		// criteria cannot express it, matching the other getObservationCount() overloads
		return OpenmrsUtil.convertToInteger(dao.getObservationCount(obsSearchCriteria.getWhom(),
		    obsSearchCriteria.getEncounters(), obsSearchCriteria.getQuestions(), obsSearchCriteria.getAnswers(),
		    obsSearchCriteria.getPersonTypes(), obsSearchCriteria.getLocations(), obsSearchCriteria.getObsGroupId(),
		    obsSearchCriteria.getFromDate(), obsSearchCriteria.getToDate(), null, obsSearchCriteria.getVisits(),
		    obsSearchCriteria.isIncludeVoidedObs(), obsSearchCriteria.getAccessionNumber()));
	}

	/**
	 * This implementation queries the obs table comparing the given <code>searchString</code> with the
	 * patient's identifier, encounterId, and obsId
	 *
	 * @see org.openmrs.api.ObsService#getObservations(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservations(String searchString) {

		// search on patient identifier
		PatientService ps = Context.getPatientService();
		List<Patient> patients = ps.getPatients(searchString);
		List<Person> persons = new ArrayList<>(patients);

		// try to search on encounterId
		EncounterService es = Context.getEncounterService();
		List<Encounter> encounters = new ArrayList<>();
		try {
			Encounter e = es.getEncounter(Integer.valueOf(searchString));
			if (e != null) {
				encounters.add(e);
			}
		} catch (NumberFormatException e) {
			// pass
		}

		List<Obs> returnList = new ArrayList<>();

		if (!encounters.isEmpty() || !persons.isEmpty()) {
			returnList = Context.getObsService().getObservations(persons, encounters, null, null, null, null, null, null,
			    null, null, null, false);
		}

		// try to search on obsId
		try {
			Obs o = getObs(Integer.valueOf(searchString));
			if (o != null) {
				returnList.add(o);
			}
		} catch (NumberFormatException e) {
			// pass
		}

		return returnList;
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationsByPerson(org.openmrs.Person)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservationsByPerson(Person who) {
		List<Person> whom = new ArrayList<>();
		whom.add(who);
		return Context.getObsService().getObservations(whom, null, null, null, null, null, null, null, null, null, null,
		    false);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationsByPersonAndConcept(org.openmrs.Person,
	 *      org.openmrs.Concept)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservationsByPersonAndConcept(Person who, Concept question) throws APIException {
		List<Person> whom = new ArrayList<>();
		if (who != null && who.getPersonId() != null) {
			whom.add(who);
		}
		List<Concept> questions = new ArrayList<>();
		questions.add(question);

		return Context.getObsService().getObservations(whom, null, questions, null, null, null, null, null, null, null, null,
		    false);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObsByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Obs getObsByUuid(String uuid) throws APIException {
		Obs obsByUuid = dao.getObsByUuid(uuid);
		if (obsByUuid != null && obsByUuid.isComplex()) {
			return withComplexData(obsByUuid);
		}
		return obsByUuid;
	}

	/**
	 * @see org.openmrs.api.ObsService#getRevisionObs(org.openmrs.Obs)
	 */
	@Transactional(readOnly = true)
	public Obs getRevisionObs(Obs initialObs) {
		return dao.getRevisionObs(initialObs);
	}

	/**
	 * @see org.openmrs.api.ObsService#getComplexObs(Integer, String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Obs getComplexObs(Integer obsId, String view) throws APIException {
		Obs obs = dao.getObs(obsId);

		if (obs != null && obs.isComplex()) {
			return getHandler(obs).getObs(obs, view);
		}

		return obs;
	}

	/**
	 * Internal method to remove ComplexData when an Obs is purged.
	 */
	protected boolean purgeComplexData(Obs obs) throws APIException {
		if (obs.isComplex()) {
			ComplexObsHandler handler = getHandler(obs);
			if (null != handler) {
				return handler.purgeComplexData(obs);
			}
		}

		return true;
	}

	/**
	 * @see org.openmrs.api.ObsService#getHandler(org.openmrs.Obs)
	 */
	@Override
	@Transactional(readOnly = true)
	public ComplexObsHandler getHandler(Obs obs) throws APIException {
		if (obs.getConcept().isComplex()) {
			// Get the ConceptComplex from the ConceptService then return its
			// handler.
			if (obs.getConcept() == null) {
				throw new APIException("Obs.error.unable.get.handler", new Object[] { obs });
			}

			String handlerString = Context.getConceptService().getConceptComplex(obs.getConcept().getConceptId())
			        .getHandler();

			if (handlerString == null) {
				throw new APIException("Obs.error.unable.get.handler.and.concept", new Object[] { obs, obs.getConcept() });
			}

			return this.getHandler(handlerString);
		}

		return null;
	}

	/**
	 * @see org.openmrs.api.ObsService#getHandler(java.lang.String)
	 */
	@Override
	public ComplexObsHandler getHandler(String key) {
		return handlers.get(key);
	}

	/**
	 * @see org.openmrs.api.ObsService#setHandlers(Map)
	 * @see #registerHandler(String, ComplexObsHandler)
	 */
	@Override
	public void setHandlers(Map<String, ComplexObsHandler> newHandlers) throws APIException {
		if (newHandlers == null) {
			this.handlers = null;
			return;
		}
		for (Map.Entry<String, ComplexObsHandler> entry : newHandlers.entrySet()) {
			registerHandler(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * @see org.openmrs.api.ObsService#getHandlers()
	 */
	@Override
	@Transactional(readOnly = true)
	public Map<String, ComplexObsHandler> getHandlers() throws APIException {
		if (handlers == null) {
			handlers = new LinkedHashMap<>();
		}

		return handlers;
	}

	/**
	 * @see org.openmrs.api.ObsService#registerHandler(String, ComplexObsHandler)
	 */
	@Override
	public void registerHandler(String key, ComplexObsHandler handler) throws APIException {
		getHandlers().put(key, handler);
	}

	/**
	 * @see org.openmrs.api.ObsService#registerHandler(String, String)
	 */
	@Override
	public void registerHandler(String key, String handlerClass) throws APIException {
		try {
			Class<?> loadedClass = OpenmrsClassLoader.getInstance().loadClass(handlerClass);
			registerHandler(key, (ComplexObsHandler) loadedClass.newInstance());

		} catch (Exception e) {
			throw new APIException("unable.load.and.instantiate.handler", null, e);
		}
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationCount(java.util.List, boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public Integer getObservationCount(List<ConceptName> conceptNames, boolean includeVoided) {
		return OpenmrsUtil.convertToInteger(
		    dao.getObservationCount(null, null, null, null, null, null, null, null, null, conceptNames, true, null));
	}

	/**
	 * @see org.openmrs.api.ObsService#removeHandler(java.lang.String)
	 */
	@Override
	public void removeHandler(String key) {
		handlers.remove(key);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getRefByUuid(Class<T> type, String uuid) {
		if (Obs.class.equals(type)) {
			return (T) getObsByUuid(uuid);
		}
		if (ObsFinalizationPolicy.class.equals(type)) {
			return (T) getObsFinalizationPolicyByUuid(uuid);
		}
		throw new APIException("Unsupported type for getRefByUuid: " + type != null ? type.getName() : "null");
	}

	@Override
	public List<Class<?>> getRefTypes() {
		return Arrays.asList(Obs.class, ObsFinalizationPolicy.class);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationsByAsserter(Provider, Integer, Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservationsByAsserter(Provider asserter, Integer startIndex, Integer limit) {
		return dao.getObservationsByAsserter(asserter, startIndex, limit);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObservationsBySoftwareVersion(String, String, Integer,
	 *      Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Obs> getObservationsBySoftwareVersion(String name, String version, Integer startIndex, Integer limit) {
		return dao.getObservationsBySoftwareVersion(name, version, startIndex, limit);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObsFinalizationPolicyByUuid(String)
	 */
	@Override
	@Transactional(readOnly = true)
	public ObsFinalizationPolicy getObsFinalizationPolicyByUuid(String uuid) {
		return dao.getObsFinalizationPolicyByUuid(uuid);
	}

	/**
	 * @see org.openmrs.api.ObsService#getAllObsFinalizationPolicies(boolean)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<ObsFinalizationPolicy> getAllObsFinalizationPolicies(boolean includeRetired) {
		return dao.getAllObsFinalizationPolicies(includeRetired);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObsFinalizationPolicy(UserType, ConceptClass)
	 */
	@Override
	@Transactional(readOnly = true)
	public ObsFinalizationPolicy getObsFinalizationPolicy(UserType agentType, ConceptClass conceptClass) {
		// the handful of policies come from the query cache, so saving an obs costs no query for them
		ObsFinalizationPolicy forEveryClass = null;
		for (ObsFinalizationPolicy policy : dao.getAllObsFinalizationPolicies(false)) {
			if (policy.getAgentType() == agentType) {
				if (policy.getConceptClass() == null) {
					forEveryClass = policy;
				} else if (policy.getConceptClass().equals(conceptClass)) {
					return policy;
				}
			}
		}
		return forEveryClass;
	}

	/**
	 * @see org.openmrs.api.ObsService#saveObsFinalizationPolicy(ObsFinalizationPolicy)
	 */
	@Override
	public ObsFinalizationPolicy saveObsFinalizationPolicy(ObsFinalizationPolicy policy) {
		return dao.saveObsFinalizationPolicy(policy);
	}

	/**
	 * @see org.openmrs.api.ObsService#retireObsFinalizationPolicy(ObsFinalizationPolicy, String)
	 */
	@Override
	public ObsFinalizationPolicy retireObsFinalizationPolicy(ObsFinalizationPolicy policy, String reason) {
		return dao.saveObsFinalizationPolicy(policy);
	}

	/**
	 * @see org.openmrs.api.ObsService#unretireObsFinalizationPolicy(ObsFinalizationPolicy)
	 */
	@Override
	public ObsFinalizationPolicy unretireObsFinalizationPolicy(ObsFinalizationPolicy policy) {
		return dao.saveObsFinalizationPolicy(policy);
	}
}
