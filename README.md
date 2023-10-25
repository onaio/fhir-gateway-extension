# fhir-gateway-plugin

This repo holds the OpenSRP permissions checker and data access checker along
with custom Rest Endpoints i.e. PractitionerDetails and LocationHierarchy.

## Getting Started

## Pre requisites

### FHIR Server (JPA Server):

The FHIR server must be configured to accept connections from the gateway
plugin.

### Keycloak Server

The keycloak server must be configured to perform authentication via the gateway
plugin.

## Development setup

### Modules

#### plugins

There is a module plugins that holds custom access checkers and custom
end-points built on top of the Google's Gateway server and HAPI FHIR instance.

#### exec

There is also a sample exec module which shows how all pieces can be woven
together into a single Spring Boot app.

### Configuration parameters

The configuration parameters are provided through environment variables:

`PROXY_TO`: The base url of the FHIR store. `TOKEN_ISSUER`: The URL of the
access token issuer. `ACCESS_CHECKER`: The access-checker to use. Each
access-checker has a name and this variable should be set to the name of the
plugin to use. `ALLOWED_QUERIES_FILE`: A list of URL requests that should bypass
the access checker and always be allowed. AllowedQueriesChecker compares the
incoming request with a configured set of allowed-queries. The intended use of
this checker is to override all other access-checkers for certain user-defined
criteria. The user defines their criteria in a config file and if the URL query
matches an entry in the config file, access is granted. AllowedQueriesConfig
provides all the supported configurations. `BACKEND_TYPE:` The type of backend,
either HAPI or GCP. HAPI should be used for most FHIR servers, while GCP should
be used for GCP FHIR stores. `SYNC_FILTER_IGNORE_RESOURCES_FILE`: A list of URL
requests that should bypass the sync data filteration logic inside the
permission access checker.

Examples: `export PROXY_TO=http://localhost:8090/fhir`
`export TOKEN_ISSUER=http://localhost:8180/auth/realms/fhir-core`
`export ACCESS_CHECKER=permission`
`export ALLOWED_QUERIES_FILE="resources/hapi_page_url_allowed_queries.json`
`export BACKEND_TYPE=HAPI`
`export SYNC_FILTER_IGNORE_RESOURCES_FILE=resources/hapi_sync_filter_ignored_queries.json`

### Build Project

To build all modules, from the root run:

`mvn clean install`

### Generating the Plugins JAR
To generate the plugins JAR file, execute the following command from the plugins module:
`mvn clean package`

The generated JAR file can be found in the target directory.
Please note we are not running plugins jar explicitly. Instead we are running an exec module.

### Run project

After a successful build, the built-in Tomcat container will automatically
deploy your Spring Boot application. You can access your application in a web
browser by navigating to http://localhost:8080 or the specified port in your
application's configuration.

## Authors/Team

The OpenSRP team

See the list of contributors who participated in this project from the
Contributors link

## Documentation
