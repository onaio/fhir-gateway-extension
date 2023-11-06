# fhir-gateway-plugin

This repository extends the FHIR Info Gateway code base release artifact found
here https://github.com/google/fhir-gateway and adds custom OpenSRP logic.

The custom functionality added includes:

- **Permissions Checker** - Authorization per FHIR Endpoint per HTTP Verb
- **Data Access Checker** - Data filtering based on user assignment (Sync
  strategy enhancements) i.e. Support for sync by Team/Organization, Location,
  Practitioner, Careteam
- Custom FHIR resources REST endpoints i.e. **PractitionerDetails** and
  **LocationHierarchy**.

## Getting Started

## Pre requisites

### FHIR Server (JPA Server):

The FHIR server must be configured to accept connections from the FHIR Info
Gateway plugin.

Project and documentation can be found here
https://github.com/hapifhir/hapi-fhir-jpaserver-starter

### Keycloak Server

The keycloak server must be configured to perform authentication via the FHIR
Info Gateway plugin.

Project and documentation can be found here https://github.com/keycloak/keycloak

## Development setup

### Modules

#### plugins

There is a module plugins that holds custom access checkers and custom
end-points built on top of the Google's FHIR Info Gateway server and HAPI FHIR
instance.

#### exec

There is also a sample exec module which shows how all pieces can be woven
together into a single Spring Boot application.</br>
[See documention here](https://github.com/google/fhir-gateway#modules)

### Generating the Plugins JAR

To generate the plugins JAR file, execute the following command from the plugins
module:

```console
$ mvn clean package
```

The generated JAR file can be found in the `exec/target` directory. Please note,
we are not running the plugins jar explicitly. Instead we are running an _exec
module_.

## Configuration Parameters

Most of the configuration parameters are inherited from the
[FHIR Info Gateway](https://github.com/google/fhir-gateway) and provided as
environment variables. Below is a list of the required configurations.

- `ACCESS_CHECKER`: Specify the OpenSRP Access Checker e.g.

  ```bash
   export ACCESS_CHECKER=permission
  ```

  For more on Access Checkers
  [read documentation here](https://github.com/google/fhir-gateway/wiki/Understanding-access-checker-plugins).

- `PROXY_TO`: The base url of the FHIR store e.g.

  ```shell
     export PROXY_TO=https://example.com/fhir
  ```

- `TOKEN_ISSUER`: The URL of the access token issuer, e.g.

  ```shell
  export TOKEN_ISSUER=http://localhost:9080/auth/realms/test
  ```

- `ALLOWED_QUERIES_FILE`: A list of URL requests that should bypass the access
  checker and always be allowed.
  [`AllowedQueriesChecker`](https://github.com/google/fhir-gateway/blob/main/server/src/main/java/com/google/fhir/gateway/AllowedQueriesChecker.java)
  compares the incoming request with a configured set of allowed-queries. The
  intended use of this checker is to override all other access-checkers for
  certain user-defined criteria. The user defines their criteria in a config
  file and if the URL query matches an entry in the config file, access is
  granted.
  [AllowedQueriesConfig](https://github.com/google/fhir-gateway-plugin/blob/main/server/src/main/java/com/google/fhir/gateway/AllowedQueriesConfig.java)
  provides all the supported configurations. An example of this is
  [`hapi_page_url_allowed_queries.json`](https://github.com/google/fhir-gateway/blob/main/resources/hapi_page_url_allowed_queries.json).
  To use this file with `ALLOWED_QUERIES_FILE`:

  ```shell
  export ALLOWED_QUERIES_FILE="resources/hapi_page_url_allowed_queries.json"
  ```

- `SYNC_FILTER_IGNORE_RESOURCES_FILE`: A list of URL requests that should bypass
  the sync filter (sync by strategy)
  [`IgnoredResourcesConfig`](https://github.com/onaio/fhir-gateway-plugin/blob/main/plugins/src/main/java/org/smartregister/fhir/gateway/plugins/SyncAccessDecision.java#IgnoredResourcesConfig)

  An example of this is
  [`hapi_sync_filter_ignored_queries.json`](https://github.com/onaio/fhir-gateway-plugin/blob/main/resources/hapi_sync_filter_ignored_queries.json).
  To use this file with `ALLOWED_QUERIES_FILE`:

  ```shell
  export SYNC_FILTER_IGNORE_RESOURCES_FILE="resources/hapi_sync_filter_ignored_queries.json"
  ```

- `BACKEND_TYPE`: The type of backend, either `HAPI` or `GCP`. `HAPI` should be
  used for most FHIR servers, while `GCP` should be used for GCP FHIR stores.

**Caching**

The plugins implementation supports caching for the sync strategy details which
are expensive to fetch per request. By default, the sync strategy ids are cached
for _60 seconds_. You can however override this by passing a value to the
`OPENSRP_CACHE_EXPIRY_SECONDS` environment variable. For this to work, one needs
to pass a value greater than `0` e.g.

```bash
export OPENSRP_CACHE_EXPIRY_SECONDS=30
```

To disable caching, set the value to `0`. Note, the value provided is in
**seconds**. This configuration is _optional_.

### Run project

As document on the Info Gateway modules
[section here](https://github.com/google/fhir-gateway#modules), the command to
run is:

```console
$ java -jar exec/target/opensrp-gateway-plugin-exec.jar --server.port=8081
```

After a successful build, the built-in _Tomcat container_ will automatically
deploy your _Spring Boot application_. You can access your application in a web
browser by navigating to http://localhost:8080 (default) or the specified port
in your application's configuration.

### Tests

To run the unit tests use the command below which both runs tests and generates
a code coverage report.

```shell
$ mvn clean test jacoco:report
```

The test report is located at `/plugins/target/site/jacoco/index.html`

## Documentation

- HAPI FHIR JPA Starter project
  https://github.com/hapifhir/hapi-fhir-jpaserver-starter
- FHIR Info Gateway project https://github.com/google/fhir-gateway
- Gateway Access Checkers
  https://github.com/google/fhir-gateway/wiki/Understanding-access-checker-plugins
