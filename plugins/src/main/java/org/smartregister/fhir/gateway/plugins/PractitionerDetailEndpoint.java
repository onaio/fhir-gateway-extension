/*
 * Copyright 2021-2023 Google LLC
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
package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.PROXY_TO_ENV;
import static org.smartregister.utils.Constants.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.TokenVerifier;
import java.io.IOException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.practitioner.PractitionerDetails;

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
  private FhirContext fhirR4Context = FhirContext.forR4();
  private IGenericClient r4FhirClient =
      fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));

  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

  private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

  public PractitionerDetailEndpoint() throws IOException {
    this.tokenVerifier = TokenVerifier.createFromEnvVars();
    this.practitionerDetailsEndpointHelper = new PractitionerDetailsEndpointHelper(r4FhirClient);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      String authHeader = request.getHeader("Authorization");
      if (authHeader == null) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "No Authorization header provided!", new AuthenticationException());
      }
      List<String> patientIds = new ArrayList<>();
      // Note for a more meaningful HTTP status code, we can catch AuthenticationException in:
      DecodedJWT jwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);
      String keycloakUuid = request.getParameter("keycloak-uuid");
      PractitionerDetails practitionerDetails =
          practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(keycloakUuid);
      String resultContent = fhirR4JsonParser.encodeResourceToString(practitionerDetails);
      response.setContentType("application/json");
      response.getOutputStream().print(resultContent);
      response.setStatus(HttpStatus.SC_OK);
    } catch (AuthenticationException authenticationException) {
      response.setContentType("application/json");
      response.getOutputStream().print(authenticationException.getMessage());
      response.setStatus(authenticationException.getStatusCode());
    } catch (Exception exception) {
      response.setContentType("application/json");
      response.getOutputStream().print(exception.getMessage());
      response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private Practitioner getPractitionerByIdentifier(String identifier) {
    Bundle resultBundle =
        getFhirClientForR4()
            .search()
            .forResource(Practitioner.class)
            .where(Practitioner.IDENTIFIER.exactly().identifier(identifier))
            .returnBundle(Bundle.class)
            .execute();

    return resultBundle != null
        ? (Practitioner) resultBundle.getEntryFirstRep().getResource()
        : null;
  }

  private IGenericClient getFhirClientForR4() {
    return r4FhirClient;
  }
}
