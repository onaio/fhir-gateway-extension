/*
 * Copyright 2021-2023 Ona Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.gateway.plugin;


import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.FhirClientFactory;
import com.google.fhir.gateway.HttpFhirClient;
import org.apache.http.HttpStatus;
import com.google.fhir.gateway.TokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.practitioner.PractitionerDetails;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is an example servlet that requires a valid JWT to be present as the Bearer Authorization
 * header. Although it is not a standard FHIR query, but it uses the FHIR server to construct the
 * response. In this example, it inspects the JWT and depending on its claims, constructs the list
 * of Patient IDs that the user has access to.
 *
 * <p>The two types of tokens resemble {@link com.google.fhir.gateway.plugin.ListAccessChecker} and
 * {@link com.google.fhir.gateway.plugin.PatientAccessChecker} expected tokens. But those are just
 * picked as examples and this custom endpoint is independent of any {@link
 * com.google.fhir.gateway.interfaces.AccessChecker}.
 */
@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(PractitionerDetailEndpoint.class);
  private final TokenVerifier tokenVerifier;

  private final HttpFhirClient fhirClient;

  private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;
  public PractitionerDetailEndpoint() throws IOException {
    this.tokenVerifier = TokenVerifier.createFromEnvVars();
    this.fhirClient = FhirClientFactory.createFhirClientFromEnvVars();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Check the Bearer token to be a valid JWT with required claims.
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      throw new ServletException("No Authorization header provided!");
    }
    List<String> patientIds = new ArrayList<>();
    // Note for a more meaningful HTTP status code, we can catch AuthenticationException in:
    DecodedJWT jwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);
    String keycloakUuid = request.getParameter("keycloak-uuid");
    PractitionerDetails practitionerDetails = practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(keycloakUuid);
    response.getOutputStream().print("Your patient are: " + String.join(" ", patientIds));
    response.setStatus(HttpStatus.SC_OK);
  }
}
