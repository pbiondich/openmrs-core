/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openmrs.Capture;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptReferenceRange;
import org.openmrs.ConceptReferenceRangeContext;
import org.openmrs.Derivation;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.ObsReferenceRange;
import org.openmrs.annotation.Handler;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validator for the Obs class. This class checks for anything set on the Obs object that will cause
 * errors or is incorrect. Things checked are similar to:
 * <ul>
 * <li>all required properties are filled in on the Obs object.
 * <li>checks for no recursion in the obs grouping.
 * <li>Makes sure the obs has at least one value (if not an obs grouping)</li>
 * </ul>
 *
 * @see org.openmrs.Obs
 */
@Handler(supports = { Obs.class }, order = 50)
public class ObsValidator implements Validator {

	public static final int VALUE_TEXT_MAX_LENGTH = 65535;

	/**
	 * <p>
	 * <strong>Should</strong> support Obs class
	 *
	 * @see org.springframework.validation.Validator#supports(java.lang.Class)
	 */
	@Override
	public boolean supports(Class<?> c) {
		return Obs.class.isAssignableFrom(c);
	}

	/**
	 * <p>
	 * <strong>Should</strong> fail validation if personId is null<br/>
	 * <strong>Should</strong> fail validation if obsDatetime is null<br/>
	 * <strong>Should</strong> fail validation if concept is null<br/>
	 * <strong>Should</strong> fail validation if concept datatype is boolean and valueBoolean is
	 * null<br/>
	 * <strong>Should</strong> fail validation if concept datatype is coded and valueCoded is null<br/>
	 * <strong>Should</strong> fail validation if concept datatype is date and valueDatetime is
	 * null<br/>
	 * <strong>Should</strong> fail validation if concept datatype is numeric and valueNumeric is
	 * null<br/>
	 * <strong>Should</strong> fail validation if concept datatype is text and valueText is null<br/>
	 * <strong>Should</strong> fail validation if obs ancestors contains obs<br/>
	 * <strong>Should</strong> pass validation if all values present<br/>
	 * <strong>Should</strong> fail validation if the parent obs has values<br/>
	 * <strong>Should</strong> reject an invalid concept and drug combination<br/>
	 * <strong>Should</strong> pass if answer concept and concept of value drug match<br/>
	 * <strong>Should</strong> pass validation if field lengths are correct<br/>
	 * <strong>Should</strong> fail validation if field lengths are not correct<br/>
	 * <strong>Should</strong> not validate if obs is voided<br/>
	 * <strong>Should</strong> not validate a voided child obs<br/>
	 * <strong>Should</strong> fail for a null object<br/>
	 * <strong>Should</strong> pass a span that fits its capture's kind<br/>
	 * <strong>Should</strong> fail for a span without a capture<br/>
	 * <strong>Should</strong> fail for a span that does not follow the grammar<br/>
	 * <strong>Should</strong> fail for a span that does not fit its capture's kind<br/>
	 * <strong>Should</strong> fail for a capture of another encounter<br/>
	 * <strong>Should</strong> fail for a derivation over another capture
	 *
	 * @see org.springframework.validation.Validator#validate(java.lang.Object,
	 *      org.springframework.validation.Errors)
	 */
	@Override
	public void validate(Object obj, Errors errors) {
		Obs obs = (Obs) obj;
		if (obs == null) {
			throw new APIException("Obs can't be null");
		} else if (obs.getVoided()) {
			return;
		}
		List<Obs> ancestors = new ArrayList<>();
		validateHelper(obs, errors, ancestors, true);
		ValidateUtil.validateFieldLengths(errors, obj.getClass(), "accessionNumber", "valueModifier", "valueComplex",
		    "comment", "voidReason", "span");
	}

	/**
	 * Checks whether obs has all required values, and also checks to make sure that no obs group
	 * contains any of its ancestors
	 *
	 * @param obs
	 * @param errors
	 * @param ancestors
	 * @param atRootNode whether or not this is the obs that validate() was originally called on. If not
	 *            then we shouldn't reject fields by name.
	 */
	private void validateHelper(Obs obs, Errors errors, List<Obs> ancestors, boolean atRootNode) {
		if (obs.getPersonId() == null) {
			errors.rejectValue("person", "error.null");
		}
		if (obs.getObsDatetime() == null) {
			errors.rejectValue("obsDatetime", "error.null");
		}

		boolean isObsGroup = obs.hasGroupMembers(true);
		// if this is an obs group (i.e., parent) make sure that it has no values (other than valueGroupId) set
		if (isObsGroup) {
			if (obs.getValueCoded() != null) {
				errors.rejectValue("valueCoded", "error.not.null");
			}

			if (obs.getValueDrug() != null) {
				errors.rejectValue("valueDrug", "error.not.null");
			}

			if (obs.getValueDatetime() != null) {
				errors.rejectValue("valueDatetime", "error.not.null");
			}

			if (obs.getValueNumeric() != null) {
				errors.rejectValue("valueNumeric", "error.not.null");
			}

			if (obs.getValueModifier() != null) {
				errors.rejectValue("valueModifier", "error.not.null");
			}

			if (obs.getValueText() != null) {
				errors.rejectValue("valueText", "error.not.null");
			}

			if (obs.getValueBoolean() != null) {
				errors.rejectValue("valueBoolean", "error.not.null");
			}

			if (obs.getValueComplex() != null) {
				errors.rejectValue("valueComplex", "error.not.null");
			}

		}
		// if this is NOT an obs group, make sure that it has at least one value set (not counting obsGroupId)
		else if (obs.getValueBoolean() == null && obs.getValueCoded() == null && obs.getValueCodedName() == null
		        && obs.getValueComplex() == null && obs.getValueDatetime() == null && obs.getValueDrug() == null
		        && obs.getValueModifier() == null && obs.getValueNumeric() == null && obs.getValueText() == null
		        && obs.getComplexData() == null) {
			errors.reject("error.noValue");
		}

		// make sure there is a concept associated with the obs
		Concept c = obs.getConcept();
		if (c == null) {
			errors.rejectValue("concept", "error.null");
		}
		// if there is a concept, and this isn't a group, perform validation tests specific to the concept datatype
		else if (!isObsGroup) {
			ConceptDatatype dt = c.getDatatype();
			if (dt != null) {
				if (dt.isBoolean() && obs.getValueBoolean() == null) {
					if (atRootNode) {
						errors.rejectValue("valueBoolean", "error.null");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				} else if (dt.isCoded() && obs.getValueCoded() == null) {
					if (atRootNode) {
						errors.rejectValue("valueCoded", "error.null");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				} else if ((dt.isDateTime() || dt.isDate() || dt.isTime()) && obs.getValueDatetime() == null) {
					if (atRootNode) {
						errors.rejectValue("valueDatetime", "error.null");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				} else if (dt.isNumeric() && obs.getValueNumeric() == null) {
					if (atRootNode) {
						errors.rejectValue("valueNumeric", "error.null");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				} else if (dt.isNumeric()) {
					ConceptNumeric cn = Context.getConceptService().getConceptNumeric(c.getConceptId());
					// If the concept numeric is not precise, the value cannot be a float, so raise an error
					if (!cn.getAllowDecimal() && Math.ceil(obs.getValueNumeric()) != obs.getValueNumeric()) {
						if (atRootNode) {
							errors.rejectValue("valueNumeric", "Obs.error.precision");
						} else {
							errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
						}
					}

					validateConceptReferenceRange(obs, errors, atRootNode);
				} else if (dt.isText() && obs.getValueText() == null) {
					if (atRootNode) {
						errors.rejectValue("valueText", "error.null");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				}

				//If valueText is longer than the maxlength, raise an error as well.
				if (dt.isText() && obs.getValueText() != null && obs.getValueText().length() > VALUE_TEXT_MAX_LENGTH) {
					if (atRootNode) {
						errors.rejectValue("valueText", "error.exceededMaxLengthOfField");
					} else {
						errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
					}
				}
			} else { // dt is null
				errors.rejectValue("concept", "must have a datatype");
			}
		}

		validateProvenance(obs, errors, atRootNode);

		// If an obs fails validation, don't bother checking its children
		if (errors.hasErrors()) {
			return;
		}

		if (ancestors.contains(obs)) {
			errors.rejectValue("groupMembers", "Obs.error.groupContainsItself");
		}

		Set<Obs> groupMembers = obs.getGroupMembers();
		if (groupMembers != null && !groupMembers.isEmpty()) {
			ancestors.add(obs);
			for (Obs child : groupMembers) {
				if (!child.getVoided()) {
					validateHelper(child, errors, ancestors, false);
				}
			}
			ancestors.remove(ancestors.size() - 1);
		}

		if (obs.getValueCoded() != null && obs.getValueDrug() != null && obs.getValueDrug().getConcept() != null) {
			Concept trueConcept = Context.getConceptService().getTrueConcept();
			Concept falseConcept = Context.getConceptService().getFalseConcept();
			//Ignore if this is not a true or false response since they are stored as coded too
			if (!obs.getValueCoded().equals(trueConcept) && !obs.getValueCoded().equals(falseConcept)
			        && !obs.getValueDrug().getConcept().equals(obs.getValueCoded())) {
				errors.rejectValue("valueDrug", "Obs.error.invalidDrug");
			}
		}
	}

	/**
	 * This method validates Obs' numeric values:
	 * <ol>
	 * <li>Validates Obs in relation to criteria e.g. checks patient's age is within the valid
	 * range</li>
	 * <li>Validates if Obs' numeric value is within the valid range; i.e. >= low absolute && <= high
	 * absolute.</li>
	 * <li>Sets field errors if numeric value is outside the valid range</li>
	 * <ol/>
	 *
	 * @param obs Observation to validate
	 * @param errors Errors to record validation issues
	 */
	private void validateConceptReferenceRange(Obs obs, Errors errors, boolean atRootNode) {
		ConceptReferenceRange conceptReferenceRange = getReferenceRange(obs);

		if (conceptReferenceRange != null) {
			validateAbsoluteRanges(obs, conceptReferenceRange, errors, atRootNode);

			if (obs.getId() == null) {
				setObsReferenceRange(obs, conceptReferenceRange);
			}
		} else if (obs.getId() == null) {
			setObsReferenceRange(obs);
		}
		setObsInterpretation(obs);
	}

	/**
	 * Evaluates the criteria and return the most strict {@link ConceptReferenceRange} for a given
	 * concept and patient contained in an observation. It considers all valid ranges that match the
	 * criteria for the person.
	 *
	 * @param obs containing The concept and patient for whom the range is being evaluated
	 * @return The strictest {@link ConceptReferenceRange}, or null if no valid range is found
	 * @since 2.7.0
	 */
	public ConceptReferenceRange getReferenceRange(Obs obs) {
		if (obs == null || obs.getPerson() == null || obs.getConcept() == null) {
			return null;
		}
		return Context.getConceptService().getConceptReferenceRange(new ConceptReferenceRangeContext(obs));
	}

	/**
	 * Validates the high and low absolute values of the Obs.
	 *
	 * @param obs Observation to validate
	 * @param conceptReferenceRange ConceptReferenceRange containing the range values
	 * @param errors Errors to record validation issues
	 */
	private void validateAbsoluteRanges(Obs obs, ConceptReferenceRange conceptReferenceRange, Errors errors,
	        boolean atRootNode) {
		if (conceptReferenceRange.getHiAbsolute() != null && conceptReferenceRange.getHiAbsolute() < obs.getValueNumeric()) {
			if (atRootNode) {
				errors.rejectValue("valueNumeric", "error.value.outOfRange.high",
				    new Object[] { conceptReferenceRange.getHiAbsolute() }, null);
			} else {
				errors.rejectValue("groupMembers", "Obs.error.inGroupMember", new Object[] {}, null);
			}
		}

		if (conceptReferenceRange.getLowAbsolute() != null
		        && conceptReferenceRange.getLowAbsolute() > obs.getValueNumeric()) {
			if (atRootNode) {
				errors.rejectValue("valueNumeric", "error.value.outOfRange.low",
				    new Object[] { conceptReferenceRange.getLowAbsolute() }, null);
			} else {
				errors.rejectValue("groupMembers", "Obs.error.inGroupMember", new Object[] {}, null);
			}
		}
	}

	/**
	 * Builds and sets the ObsReferenceRange for the given Obs.
	 *
	 * @param obs Observation to set the reference range
	 * @param conceptReferenceRange ConceptReferenceRange used to build the ObsReferenceRange
	 */
	private void setObsReferenceRange(Obs obs, ConceptReferenceRange conceptReferenceRange) {
		ObsReferenceRange obsRefRange = new ObsReferenceRange();

		obsRefRange.setHiAbsolute(conceptReferenceRange.getHiAbsolute());
		obsRefRange.setHiCritical(conceptReferenceRange.getHiCritical());
		obsRefRange.setHiNormal(conceptReferenceRange.getHiNormal());
		obsRefRange.setLowAbsolute(conceptReferenceRange.getLowAbsolute());
		obsRefRange.setLowCritical(conceptReferenceRange.getLowCritical());
		obsRefRange.setLowNormal(conceptReferenceRange.getLowNormal());
		obsRefRange.setObs(obs);

		obs.setReferenceRange(obsRefRange);
	}

	/**
	 * Builds and sets the ObsReferenceRange from concept numeric values.
	 *
	 * @param obs Observation to set the reference range
	 */
	private void setObsReferenceRange(Obs obs) {
		if (obs.getConcept() == null) {
			return;
		}

		ConceptNumeric conceptNumeric = Context.getConceptService().getConceptNumeric(obs.getConcept().getId());

		if (conceptNumeric != null) {
			ObsReferenceRange obsRefRange = new ObsReferenceRange();

			obsRefRange.setHiAbsolute(conceptNumeric.getHiAbsolute());
			obsRefRange.setHiCritical(conceptNumeric.getHiCritical());
			obsRefRange.setHiNormal(conceptNumeric.getHiNormal());
			obsRefRange.setLowAbsolute(conceptNumeric.getLowAbsolute());
			obsRefRange.setLowCritical(conceptNumeric.getLowCritical());
			obsRefRange.setLowNormal(conceptNumeric.getLowNormal());
			obsRefRange.setObs(obs);

			obs.setReferenceRange(obsRefRange);
		}
	}

	/**
	 * This method sets Obs interpretation based on the current obs' numeric value.
	 *
	 * @param obs Observation to set the interpretation
	 */
	private void setObsInterpretation(Obs obs) {
		ObsReferenceRange referenceRange = obs.getReferenceRange();
		if (referenceRange == null || obs.getValueNumeric() == null) {
			return;
		}

		Double obsValue = obs.getValueNumeric();
		Double hiCritical = referenceRange.getHiCritical();
		Double lowCritical = referenceRange.getLowCritical();
		Double lowNormal = referenceRange.getLowNormal();
		Double hiNormal = referenceRange.getHiNormal();

		if (hiCritical != null && obsValue >= hiCritical) {
			obs.setInterpretation(Obs.Interpretation.CRITICALLY_HIGH);
		} else if (hiNormal != null && obsValue > hiNormal) {
			obs.setInterpretation(Obs.Interpretation.HIGH);
		} else if (lowCritical != null && obsValue <= lowCritical) {
			obs.setInterpretation(Obs.Interpretation.CRITICALLY_LOW);
		} else if (lowNormal != null && obsValue < lowNormal) {
			obs.setInterpretation(Obs.Interpretation.LOW);
		} else {
			obs.setInterpretation(Obs.Interpretation.NORMAL);
		}
	}

	/**
	 * A span is a locator, a set of <code>;</code> separated <code>name=value</code> parameters, or a
	 * locator followed by them:
	 * <ul>
	 * <li><code>t=42.0-58.0</code>: seconds into a recording</li>
	 * <li><code>xywh=412,880,60,24</code>: a region of an image in its pixels, or of a PDF page in
	 * points, from the top left</li>
	 * <li><code>c=120-184</code>: Unicode code points into text, from 0, end exclusive</li>
	 * <li>parameters such as <code>field=chest_indrawing</code>; <code>page=2</code> (from 1) is
	 * reserved for the page of a multi-page capture</li>
	 * </ul>
	 * Values are letters, digits and <code>_ . : -</code> only, so a span can be shown as is.
	 */
	private static final String PARAMETER = "(?!(?:t|xywh|c)=)[a-z][a-z0-9_]*=[A-Za-z0-9_.:-]+";

	private static final Pattern SPAN = Pattern
	        .compile("(t|xywh|c)=([0-9.,-]+)(?:;" + PARAMETER + ")*|" + PARAMETER + "(?:;" + PARAMETER + ")*");

	private static final int SPAN_LENGTH = 255;

	private static final Pattern TIME = Pattern.compile("(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)");

	private static final Pattern REGION = Pattern.compile("\\d+,\\d+,\\d+,\\d+");

	private static final Pattern CHARACTERS = Pattern.compile("(\\d+)-(\\d+)");

	/**
	 * Checks that an obs's capture, span and derivation agree: a span needs a capture and must fit its
	 * kind; the capture must be of the obs's own encounter and patient; a derivation must be over the
	 * same capture; and a new obs may not point to a voided capture or derivation.
	 */
	private void validateProvenance(Obs obs, Errors errors, boolean atRootNode) {
		Capture capture = obs.getCapture();
		Derivation derivation = obs.getDerivation();
		String span = obs.getSpan();
		if (span != null) {
			if (capture == null) {
				reject(errors, atRootNode, "span", "Obs.error.spanWithoutCapture");
			} else if (span.length() > SPAN_LENGTH || !spanFits(span, capture.getKind())) {
				reject(errors, atRootNode, "span", "Obs.error.span");
			}
		}
		if (capture != null) {
			Encounter encounter = capture.getEncounter();
			if (obs.getEncounter() == null || !obs.getEncounter().equals(encounter)
			        || !Objects.equals(obs.getPersonId(), encounter.getPatient().getPatientId())) {
				reject(errors, atRootNode, "capture", "Obs.error.captureOfAnotherEncounter");
			}
		}
		if (derivation != null && (capture == null || !capture.equals(derivation.getCapture()))) {
			reject(errors, atRootNode, "derivation", "Obs.error.derivationOfAnotherCapture");
		}
		// an existing obs keeps its links even once they are voided; only a new link is refused
		if (obs.getObsId() == null
		        && ((capture != null && capture.getVoided()) || (derivation != null && derivation.getVoided()))) {
			reject(errors, atRootNode, capture != null && capture.getVoided() ? "capture" : "derivation",
			    "Obs.error.voidedProvenance");
		}
	}

	private static void reject(Errors errors, boolean atRootNode, String field, String code) {
		if (atRootNode) {
			errors.rejectValue(field, code);
		} else {
			errors.rejectValue("groupMembers", "Obs.error.inGroupMember");
		}
	}

	/**
	 * @return whether the span follows the grammar and its locator, if any, suits the kind of capture
	 */
	static boolean spanFits(String span, Capture.Kind kind) {
		Matcher matcher = SPAN.matcher(span);
		if (!matcher.matches()) {
			return false;
		}
		String locator = matcher.group(1);
		String value = matcher.group(2);
		if (locator == null) {
			return true;
		}
		switch (locator) {
			case "t":
				return (kind == Capture.Kind.AUDIO || kind == Capture.Kind.VIDEO) && ordered(TIME.matcher(value));
			case "xywh":
				return kind != Capture.Kind.AUDIO && kind != Capture.Kind.TYPED_TEXT && REGION.matcher(value).matches();
			default:
				return kind == Capture.Kind.TYPED_TEXT && ordered(CHARACTERS.matcher(value));
		}
	}

	private static boolean ordered(Matcher range) {
		return range.matches() && Double.parseDouble(range.group(1)) <= Double.parseDouble(range.group(2));
	}
}
