package org.smartregister.fhir.gateway.plugins;/// *
// * Copyright 2021-2023 Google LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *       http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package org.smartregister.fhir.gateway;
//
// import ca.uhn.fhir.context.FhirContext;
// import ca.uhn.fhir.parser.IParser;
// import ca.uhn.fhir.rest.client.api.IGenericClient;
// import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
// import com.auth0.jwt.interfaces.DecodedJWT;
// import com.google.fhir.gateway.ExceptionUtil;
// import com.google.fhir.gateway.FhirClientFactory;
// import com.google.fhir.gateway.HttpFhirClient;
// import com.google.fhir.gateway.TokenVerifier;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;
// import javax.servlet.annotation.WebServlet;
// import javax.servlet.http.HttpServlet;
// import javax.servlet.http.HttpServletRequest;
// import javax.servlet.http.HttpServletResponse;
// import org.apache.http.HttpStatus;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.smartregister.model.location.LocationHierarchy;
//
// @WebServlet("/LocationHierarchy")
// public class LocationHierarchyEndpoint extends HttpServlet {
//
//  private static final Logger logger = LoggerFactory.getLogger(LocationHierarchyEndpoint.class);
//  private final TokenVerifier tokenVerifier;
//
//  private final HttpFhirClient fhirClient;
//  String PROXY_TO_ENV = "PROXY_TO";
//
//  private FhirContext fhirR4Context = FhirContext.forR4();
//  private IGenericClient r4FhirClient =
//      fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));
//
//  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);
//
//  private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;
//
//  public LocationHierarchyEndpoint() throws IOException {
//    this.tokenVerifier = TokenVerifier.createFromEnvVars();
//    this.fhirClient = FhirClientFactory.createFhirClientFromEnvVars();
//    this.locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper();
//  }
//
//  @Override
//  protected void doGet(HttpServletRequest request, HttpServletResponse response)
//      throws IOException {
//    // Check the Bearer token to be a valid JWT with required claims.
//    try {
//
//      String authHeader = request.getHeader("Authorization");
//      if (authHeader == null) {
//        ExceptionUtil.throwRuntimeExceptionAndLog(
//            logger, "No Authorization header provided!", new AuthenticationException());
//      }
//      List<String> patientIds = new ArrayList<>();
//      // Note for a more meaningful HTTP status code, we can catch AuthenticationException in:
//      DecodedJWT jwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);
//      String identifier = request.getParameter("identifier");
//
//      LocationHierarchy locationHierarchy =
//          locationHierarchyEndpointHelper.getLocationHierarchy(identifier);
//      String resultContent = fhirR4JsonParser.encodeResourceToString(locationHierarchy);
//      response.setContentType("application/json");
//      response.getOutputStream().print(resultContent);
//      response.setStatus(HttpStatus.SC_OK);
//    } catch (AuthenticationException authenticationException) {
//      response.setContentType("application/json");
//      response.getOutputStream().print(authenticationException.getMessage());
//      response.setStatus(authenticationException.getStatusCode());
//    } catch (Exception exception) {
//      response.setContentType("application/json");
//      response.getOutputStream().print(exception.getMessage());
//      response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
//    }
//  }
//
//  private IGenericClient getFhirClientForR4() {
//    return r4FhirClient;
//  }
// }
