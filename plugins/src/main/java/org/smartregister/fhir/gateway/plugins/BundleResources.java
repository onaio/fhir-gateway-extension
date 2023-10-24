package org.smartregister.fhir.gateway.plugins;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.instance.model.api.IBaseResource;

@Getter
@Setter
public class BundleResources {
  private RequestTypeEnum requestType;
  private IBaseResource resource;

  public BundleResources(RequestTypeEnum requestType, IBaseResource resource) {
    this.requestType = requestType;
    this.resource = resource;
  }
}
