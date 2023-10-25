# fhir-gateway-plugin

This repository holds the custom OpenSRP logic in form of Plugins that should be
deployed alongside th FHIR Info Gateway.

The Plugins functionality added include:

- **Permissions Checker** - Authorization per FHIR Endpoint per HTTP Verb
- **Data Access Checker** - Data filtering based on user assignment (Sync
  strategy enhancements) i.e. Support for sync by Team/Organization, Location,
  Practitioner, Careteam
- **Data Requesting** - Data fetching mechanism for FHIR Resources defining
  patient data vs OpenSRP 2.0 application sync config resources
- Custom FHIR Resources REST endpoints i.e. **PractitionerDetails** and
  **LocationHierarchy**.

## Getting Started

## Pre requisites

### FHIR Server (JPA Server):

The FHIR server must be configured to accept connections from the FHIR Info
Gateway plugin.

The latest Docker image can be found here
https://hub.docker.com/r/hapiproject/hapi/tags

### Keycloak Server

The keycloak server must be configured to perform authentication via the FHIR
Info Gateway plugin.

### FHIR Info Gateway Server

The FHIR Info Gateway server must be configured as documented here
https://github.com/google/fhir-gateway

## Development setup

### Modules

#### plugins

There is a module plugins that holds custom access checkers and custom
end-points built on top of the Google's FHIR Info Gateway server and HAPI FHIR
instance.

#### exec

There is also a sample exec module which shows how all pieces can be woven
together into a single Spring Boot app,
[documention here](https://github.com/google/fhir-gateway#modules)

### Generating the Plugins JAR

To generate the plugins JAR file, execute the following command from the plugins
module:

```sh
mvn clean package
```

The generated JAR file can be found in the `/target` directory. Please note, we
are not running the plugins jar explicitly. Instead we are running an _exec
module_.

### Run project

As document on the Info Gateway modules
[section here](https://github.com/google/fhir-gateway#modules), the command to
run is:

```sh
java -Dloader.path="PATH-TO-ADDITIONAL-PLUGINGS/custom-plugins.jar" \
  -jar exec/target/exec-0.1.0.jar --server.port=8081
```

After a successful build, the built-in _Tomcat container_ will automatically
deploy your _Spring Boot application_. You can access your application in a web
browser by navigating to http://localhost:8080 (default) or the specified port
in your application's configuration.

## Documentation

- HAPI FHIR JPA Starter project
  https://github.com/hapifhir/hapi-fhir-jpaserver-starter
- FHIR Info Gateway project https://github.com/google/fhir-gateway
