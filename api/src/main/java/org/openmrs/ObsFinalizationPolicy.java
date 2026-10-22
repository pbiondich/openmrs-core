/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

/**
 * Says whether the obs a kind of agent asserts are final when they are saved or wait, preliminary,
 * for a person to attest them. A policy for a concept class applies to obs of that class and takes
 * precedence over the agent type's policy for every class, which has no concept class. Where no
 * policy applies, an obs keeps the status it is saved with.
 *
 * @since 3.0.0
 */
@Entity
@Table(name = "obs_finalization_policy")
@Audited
public class ObsFinalizationPolicy extends BaseOpenmrsMetadata {

	private static final long serialVersionUID = 1L;

	/**
	 * What happens to an obs a policy applies to when it is saved.
	 */
	public enum Action {
		/**
		 * The obs keeps the status it is saved with, final unless the caller says otherwise.
		 */
		AUTO_FINAL,
		/**
		 * The obs is saved preliminary, whatever status the caller gave it, until a person attests it.
		 */
		REQUIRE_ATTESTATION
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "obs_finalization_policy_id")
	private Integer obsFinalizationPolicyId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "agent_type", length = 50, nullable = false)
	private UserType agentType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concept_class_id")
	private ConceptClass conceptClass;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "action", length = 50, nullable = false)
	private Action action;

	public ObsFinalizationPolicy() {
	}

	/**
	 * @param name a name for the policy
	 * @param agentType the kind of agent whose obs it covers
	 * @param conceptClass the concept class it covers, or null for every class
	 * @param action what happens to the obs it covers
	 */
	public ObsFinalizationPolicy(String name, UserType agentType, ConceptClass conceptClass, Action action) {
		setName(name);
		this.agentType = agentType;
		this.conceptClass = conceptClass;
		this.action = action;
	}

	@Override
	public Integer getId() {
		return obsFinalizationPolicyId;
	}

	@Override
	public void setId(Integer id) {
		this.obsFinalizationPolicyId = id;
	}

	public Integer getObsFinalizationPolicyId() {
		return obsFinalizationPolicyId;
	}

	public void setObsFinalizationPolicyId(Integer obsFinalizationPolicyId) {
		this.obsFinalizationPolicyId = obsFinalizationPolicyId;
	}

	/**
	 * @return the kind of agent whose obs this policy covers: the asserter's, or the creator's when the
	 *         asserter is unknown
	 */
	public UserType getAgentType() {
		return agentType;
	}

	public void setAgentType(UserType agentType) {
		this.agentType = agentType;
	}

	/**
	 * @return the concept class this policy covers, or null if it covers every class
	 */
	public ConceptClass getConceptClass() {
		return conceptClass;
	}

	public void setConceptClass(ConceptClass conceptClass) {
		this.conceptClass = conceptClass;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}
}
