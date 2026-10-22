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

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a person who may provide care to a patient during an encounter
 *
 * @since 1.9
 */
@Entity
@Table(name = "provider", uniqueConstraints = @UniqueConstraint(name = "provider_user", columnNames = { "user_id" }))
@Audited
@AttributeOverrides({ @AttributeOverride(name = "name", column = @Column(name = "name", nullable = true)) })
public class Provider extends BaseCustomizableMetadata<ProviderAttribute> {

	private static final Logger log = LoggerFactory.getLogger(Provider.class);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "provider_id")
	private Integer providerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(name = "identifier", length = 255)
	private String identifier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "role_id")
	private Concept role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "speciality_id")
	private Concept speciality;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "provider_role_id")
	private ProviderRole providerRole;

	// the user behind a DIGITAL provider; it never changes, so the provider's past assertions stay its own
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", updatable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "provider_type", length = 50, nullable = false, updatable = false)
	// Liquibase owns the default in real databases; this gives the test schema generated from the mappings the same default
	@ColumnDefault("'BIOLOGIC'")
	private ProviderType providerType = ProviderType.BIOLOGIC;

	public Provider() {
	}

	public Provider(Integer providerId) {
		this.providerId = providerId;
	}

	/**
	 * @see org.openmrs.OpenmrsObject#getId()
	 */
	@Override
	public Integer getId() {
		return getProviderId();
	}

	/**
	 * @see org.openmrs.OpenmrsObject#setId(java.lang.Integer)
	 */
	@Override
	public void setId(Integer id) {
		setProviderId(id);
	}

	/**
	 * @param providerId the providerId to set
	 */
	public void setProviderId(Integer providerId) {
		this.providerId = providerId;
	}

	/**
	 * @return the providerId
	 */
	public Integer getProviderId() {
		return providerId;
	}

	/**
	 * @param person the person to set
	 */
	public void setPerson(Person person) {
		this.person = person;
	}

	/**
	 * @return the person
	 */
	public Person getPerson() {
		return person;
	}

	/**
	 * @param identifier the identifier to set
	 */
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	/**
	 * @return the identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * Sets the role concept
	 *
	 * @since 2.2
	 * @param role the role to set
	 */
	public void setRole(Concept role) {
		this.role = role;
	}

	/**
	 * Gets the role concept
	 *
	 * @since 2.2
	 * @return the role
	 */
	public Concept getRole() {
		return role;
	}

	/**
	 * Sets the speciality concept
	 *
	 * @since 2.2
	 * @param speciality the speciality to set
	 */
	public void setSpeciality(Concept speciality) {
		this.speciality = speciality;
	}

	/**
	 * Gets the speciality concept
	 *
	 * @since 2.2
	 * @return the speciality
	 */
	public Concept getSpeciality() {
		return speciality;
	}

	/**
	 * Gets the provider role
	 *
	 * @since 2.8.0
	 * @return the provider role
	 */
	public ProviderRole getProviderRole() {
		return providerRole;
	}

	/**
	 * Sets the provider role
	 *
	 * @since 2.8.0
	 * @param providerRole the provider role to set
	 */
	public void setProviderRole(ProviderRole providerRole) {
		this.providerRole = providerRole;
	}

	/**
	 * @return the software or device user behind a {@link ProviderType#DIGITAL} provider, or null for a
	 *         {@link ProviderType#BIOLOGIC} one
	 * @since 3.0.0
	 */
	public User getUser() {
		return user;
	}

	/**
	 * @param user the software or device user behind a digital provider; it cannot be changed once the
	 *            provider is saved
	 * @since 3.0.0
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * @return whether this provider is a person or software or a device
	 * @since 3.0.0
	 */
	public ProviderType getProviderType() {
		return providerType;
	}

	/**
	 * @param providerType whether this provider is a person or software or a device; it cannot be
	 *            changed once the provider is saved
	 * @since 3.0.0
	 */
	public void setProviderType(ProviderType providerType) {
		this.providerType = providerType;
	}

	@Override
	public String toString() {
		String provider = String.valueOf(providerId) + " providerName:"
		        + ((person != null) ? person.getNames() : (user != null ? user.toString() : ""));
		return "[Provider: providerId:" + provider + " ]";
	}

	/**
	 * <p>
	 * <strong>Should</strong> return person full name if person is not null<br/>
	 * <strong>Should</strong> return the user's username or system id if there is no person but a
	 * user<br/>
	 * <strong>Should</strong> return null if there is neither a person nor a user
	 *
	 * @see org.openmrs.BaseOpenmrsMetadata#getName()
	 */

	@Override
	public String getName() {
		if (getPerson() != null && getPerson().getPersonName() != null) {
			return getPerson().getPersonName().getFullName();
		} else if (getUser() != null) {
			return getUser().toString();
		} else {
			log.warn("We no longer support providers who are not linked to person. Set the name on the linked person");
			return null;
		}
	}
}
