package uk.co.mayfieldis.dao;

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.hl7.fhir.instance.formats.JsonParser;

import org.hl7.fhir.instance.formats.XmlParser;
import org.hl7.fhir.instance.model.Bundle;
import org.hl7.fhir.instance.model.Organization;
import org.hl7.fhir.instance.model.Practitioner;
import org.hl7.fhir.instance.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnrichwithUpdateType implements AggregationStrategy  {

	private static final Logger log = LoggerFactory.getLogger(uk.co.mayfieldis.dao.EnrichwithUpdateType.class);
	
	
	private Boolean practitionerCompare(Practitioner oldPractitioner, Practitioner newPractitioner)
	{
		Boolean same = true;
		
		if (!oldPractitioner.getName().getFamily().get(0).getValue().equals(oldPractitioner.getName().getFamily().get(0).getValue()))
		{
			same = false;
		}
		return same;
	}
	
	private Boolean organisationCompare(Organization oldOrganisation, Organization newOrganisation)
	{
		Boolean same = true;
		
		if (!oldOrganisation.getName().equals(newOrganisation.getName()))
		{
			same = false;
			log.info("Name "+oldOrganisation.getName()+" "+newOrganisation.getName());
		}
		if (oldOrganisation.getActive() != newOrganisation.getActive())
		{
			same = false;
			log.info("Active "+oldOrganisation.getActive() + " " + newOrganisation.getActive());
		}
		if (!oldOrganisation.getTelecom().get(0).getValue().equals(newOrganisation.getTelecom().get(0).getValue()))
		{
			same = false;
			log.info("Telecom "+oldOrganisation.getTelecom().get(0).getValue()+" "+newOrganisation.getTelecom().get(0).getValue());
		}
		if (!oldOrganisation.getAddress().get(0).getLine().get(0).getValue().equals(newOrganisation.getAddress().get(0).getLine().get(0).getValue()))
		{
			same = false;
			log.info("Line 1 "+oldOrganisation.getAddress().get(0).getLine().get(0).getValue()+" "+newOrganisation.getAddress().get(0).getLine().get(0).getValue());;
		}
		if (!oldOrganisation.getAddress().get(0).getPostalCode().equals(newOrganisation.getAddress().get(0).getPostalCode()))
		{
			same = false;
			log.info("PostCode "+oldOrganisation.getAddress().get(0).getPostalCode()+" "+newOrganisation.getAddress().get(0).getPostalCode());;
		}
		return same;
	}
	
	@Override
	public Exchange aggregate(Exchange exchange, Exchange enrichment) 
	{
		exchange.getIn().setHeader(Exchange.HTTP_METHOD,"GET");
		//log.info(enrichment.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE).toString());
		if (enrichment.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE).toString().equals("200"))
		{
			//log.info("Oh yes I was a 200");
			ByteArrayInputStream xmlContentBytes = new ByteArrayInputStream ((byte[]) enrichment.getIn().getBody(byte[].class));
			Bundle bundle = null;
			
			if (enrichment.getIn().getHeader(Exchange.CONTENT_TYPE).toString().contains("json"))
			{
				JsonParser composer = new JsonParser();
				try
				{
					bundle = (Bundle) composer.parse(xmlContentBytes);
				}
				catch(Exception ex)
				{
					log.error("JSON Parse failed "+ex.getMessage());
				}
			}
			else
			{
				XmlParser composer = new XmlParser();
				try
				{
					bundle = (Bundle) composer.parse(xmlContentBytes);
				}
				catch(Exception ex)
				{
					log.error("XML Parse failed "+ex.getMessage());
				}
			}
			if (bundle!=null)
			{
				log.info("Bundle entry count = "+bundle.getEntry().size());
			}
			else
			{
				log.info("Bundle entry count = 0 No Bundle");
			}	
			if (bundle!=null && bundle.getEntry().size()==0)
			{
				// No resource found go ahead
				exchange.getIn().setHeader(Exchange.HTTP_METHOD,"POST");	
				if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
				{
					exchange.getIn().setHeader("FHIRResource","Organization");
				}
				if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
				{
					exchange.getIn().setHeader("FHIRResource","Practitioner");
				}
			}
			
			if (bundle!=null && bundle.getEntry().size()>0)
			{
				
				// This is bit over complex. It converts incoming data into generic FHIR Resource and the converts them to JSON for comparison
				
				Resource oldResource=bundle.getEntry().get(0).getResource();
				Organization oldOrganisation = null;
				Practitioner oldPractitioner = null;
				Organization newOrganisation = null;
				Practitioner newPractitioner = null;
				if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
				{
					oldOrganisation = (Organization) bundle.getEntry().get(0).getResource();
				}
				
				if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
				{
					oldPractitioner = (Practitioner) bundle.getEntry().get(0).getResource();
				}
				
				ByteArrayInputStream xmlNewContentBytes = new ByteArrayInputStream ((byte[]) exchange.getIn().getBody(byte[].class));
				if (exchange.getIn().getHeader(Exchange.CONTENT_TYPE).toString().contains("json"))
				{
					JsonParser composer = new JsonParser();
					try
					{
						
						if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
						{
							newPractitioner = (Practitioner) composer.parse(xmlNewContentBytes);
						}
						if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
						{
							newOrganisation = (Organization) composer.parse(xmlNewContentBytes);
						}
					}
					catch(Exception ex)
					{
						log.error("JSON Parse failed 2 "+ex.getMessage());
					}
				}
				else
				{
					XmlParser composer = new XmlParser();
					try
					{
						if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
						{
							newPractitioner = (Practitioner) composer.parse(xmlNewContentBytes);
						}
						if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
						{
							newOrganisation = (Organization) composer.parse(xmlNewContentBytes);
						}
					}
					catch(Exception ex)
					{
						log.error("XML Parse failed 2 "+ex.getMessage());
					}
				}
				Boolean sameResource = false;
				if (oldOrganisation !=null)
				{
					sameResource = organisationCompare(oldOrganisation, newOrganisation);
				}
				if (oldPractitioner !=null)
				{
					sameResource = practitionerCompare(oldPractitioner,newPractitioner);
				}
				
				if (!sameResource)
				{
					// Record is different so update it
					exchange.getIn().setHeader(Exchange.HTTP_METHOD,"PUT");
					
					if (enrichment.getIn().getHeader(Exchange.CONTENT_TYPE).equals("application/json"))
					{
						//JsonParser composer = new JsonParser();
						try
						{
							if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
							{
								
								exchange.getIn().setHeader("FHIRResource","Organization/"+oldResource.getId());
							}
							if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
							{
								
								exchange.getIn().setHeader("FHIRResource","Practitioner/"+oldResource.getId());
							}
						}
						catch(Exception ex)
						{
						}
					}
					else
					{
						//XmlParser composer = new XmlParser();
						try
						{
							if (exchange.getIn().getHeader("FHIRResource").toString().contains("Organization"))
							{
								  
								exchange.getIn().setHeader("FHIRResource","Organization/"+exchange.getIn().getHeader("OrganisationCode"));
							}
							if (exchange.getIn().getHeader("FHIRResource").toString().contains("Practitioner"))
							{
								
								exchange.getIn().setHeader("FHIRResource","Practitioner/"+exchange.getIn().getHeader("OrganisationCode"));
							}
						}
						catch(Exception ex)
						{
						}
					}
				}
			}
			exchange.getIn().setHeader("FHIRQuery","");
			exchange.getIn().setHeader(Exchange.CONTENT_TYPE,"application/json+fhir");
		}
		return exchange;
	}
}

