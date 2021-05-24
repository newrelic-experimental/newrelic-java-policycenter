package com.newrelic.guidewire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.NewField;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

@Weave(originalName = "javax.servlet.http.HttpServlet", type = MatchType.BaseClass)
public abstract class HttpServlet_instrumentation {

	@NewField
	private static Map<String, List<String>> selectedJsonParametersMap = null;
	
	@NewField
	private final static String PARAM_EVENT_SOURCE = "eventSource";
	
	@NewField
	private final static String PARAM_EVENT_PARAM = "eventParam";
	
	@NewField
	private final static String ROOT_ATTRIBUTES_KEY = "rootAttributes";

	@NewField
	private static JsonFactory jsonFactory = new JsonFactory();

	public HttpServlet_instrumentation() {
		initJsonParametersMap();
	}

	@Trace(dispatcher=true)
	protected void service(HttpServletRequest request, HttpServletResponse response) {
		Weaver.callOriginal();
		try {
			String wizardName = null;
			if (request != null) {
				String eventSource = request.getParameter(PARAM_EVENT_SOURCE);
				if ((eventSource != null)) {
					NewRelic.addCustomParameter(PARAM_EVENT_SOURCE, eventSource);
					String eventParam = request.getParameter(PARAM_EVENT_PARAM);
					if(eventParam != null && !eventParam.isEmpty())
					{
						NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, PARAM_EVENT_PARAM, eventParam);
						NewRelic.addCustomParameter(PARAM_EVENT_PARAM, eventParam);
					}
					else
					{
						System.out.println("Setting transaciton name to " + eventSource);
						NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "Custom", eventSource.replace("-", "_"));
					}
					
					if (eventSource.endsWith("_act")) {
						int lengthWizardName = eventSource.indexOf("-");
						if (lengthWizardName > 1) {
							wizardName = eventSource.substring(0, lengthWizardName);
						}
					}
				}

				Map<String, String[]> parametersMap = request.getParameterMap();
				for (String paramName : parametersMap.keySet()) {
					/*
					String paramDisplayName = selectedParametersMap.get(paramName);

					if (paramDisplayName != null) {
						String[] pValue = request.getParameterValues(paramName);
						if ((pValue != null) && (pValue.length > 0)) {
							String pValueString = String.join(" ", pValue);
							if (!pValueString.isEmpty()) {
								NewRelic.addCustomParameter(paramDisplayName, pValueString);
							}
						}
					}
					*/

					if ((wizardName != null) && (paramName.startsWith(wizardName))) {
						String jsonContent = request.getParameter(paramName);
						if ((jsonContent != null) && (!jsonContent.isEmpty())) {
							try (JsonParser jsonParser = jsonFactory.createParser(jsonContent)) {
								while (jsonParser.nextToken() != null) {
									JsonToken token = jsonParser.getCurrentToken();
									if (token.equals(JsonToken.FIELD_NAME)) {
										String fieldName = jsonParser.getCurrentName();
										jsonParser.nextToken();
										if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
											List<String> listAttributes = selectedJsonParametersMap.get(fieldName);
											int depth = 0;
											while ((jsonParser.nextToken() != JsonToken.END_OBJECT) || (depth > 0)) {
												JsonToken nestedToken = jsonParser.currentToken();
												if (nestedToken.equals(JsonToken.START_OBJECT)) {
													depth++;
												}
												if (nestedToken.equals(JsonToken.END_OBJECT)) {
													depth--;
												}

												if (listAttributes != null) {
													if (nestedToken.equals(JsonToken.FIELD_NAME)) {
														String name = jsonParser.getCurrentName();
														jsonParser.nextToken();
														String value = jsonParser.getValueAsString();
														if (listAttributes.contains(name)) {
															NewRelic.addCustomParameter(name, value);
														}
													}
												}
											}
										}
										List<String> rootAttributes = selectedJsonParametersMap.get(ROOT_ATTRIBUTES_KEY);
										if (rootAttributes.contains(fieldName)) {
											String fieldValue = jsonParser.getValueAsString();
											NewRelic.addCustomParameter(fieldName, fieldValue);
										}
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			NewRelic.getAgent().getLogger().log(Level.SEVERE,
					"Exception in NewRelic Guidewire extension " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void initJsonParametersMap() {
		if (selectedJsonParametersMap != null) {
			// must have initialized in another thread
			NewRelic.getAgent().getLogger().log(Level.INFO,
					"GUIDEWIRE extension JSON parameter map already initialized");
			return;
		}

		selectedJsonParametersMap = new HashMap<String, List<String>>();

		List<String> rootAttributes = new ArrayList<String>();
		rootAttributes.add("jobNumber");
		rootAttributes.add("currentLocation");
		rootAttributes.add("serviceName");
		rootAttributes.add("newBuildingNumber");
		rootAttributes.add("moduleNumber");
		selectedJsonParametersMap.put(ROOT_ATTRIBUTES_KEY, rootAttributes);

		List<String> piAttributes = new ArrayList<String>();
		piAttributes.add("productCode");
		piAttributes.add("jobType");
		piAttributes.add("quoteType");
		piAttributes.add("policyType");
		selectedJsonParametersMap.put("productInformation", piAttributes);
	}

}
