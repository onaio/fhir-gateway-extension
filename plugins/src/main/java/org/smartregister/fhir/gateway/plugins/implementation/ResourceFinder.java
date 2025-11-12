package org.smartregister.fhir.gateway.plugins.implementation;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.model.BundleResources;

import com.google.common.annotations.VisibleForTesting;
import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import lombok.SneakyThrows;

public final class ResourceFinder
        implements org.smartregister.fhir.gateway.plugins.interfaces.ResourceFinder {
    private static final Logger logger = LoggerFactory.getLogger(ResourceFinder.class);
    private static ResourceFinder instance = null;
    private final FhirContext fhirContext;

    // This is supposed to be instantiated with getInstance method only.
    private ResourceFinder(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    // A singleton instance of this class should be used, hence the constructor is private.
    public static synchronized ResourceFinder getInstance(FhirContext fhirContext) {
        if (instance != null) {
            return instance;
        }

        instance = new ResourceFinder(fhirContext);
        return instance;
    }

    @SneakyThrows
    @Override
    public List<BundleResources> findResourcesInBundle(RequestDetailsReader request) {
        IBaseResource resource = createResourceFromRequest(request);
        InvalidRequestException invalidRequestException;
        if (!(resource instanceof Bundle)) {
            invalidRequestException =
                    new InvalidRequestException("The provided resource is not a Bundle!");
            ExceptionUtil.throwRuntimeExceptionAndLog(
                    logger, invalidRequestException.getMessage(), invalidRequestException);
        }
        Bundle bundle = (Bundle) resource;

        if (bundle.getType() != Bundle.BundleType.TRANSACTION) {
            invalidRequestException =
                    new InvalidRequestException("Bundle type needs to be transaction!");
            // Currently, support only for transaction bundles
            ExceptionUtil.throwRuntimeExceptionAndLog(
                    logger, invalidRequestException.getMessage(), invalidRequestException);
        }

        List<BundleResources> requestTypeEnumList = new ArrayList<>();
        if (!bundle.hasEntry()) {
            return requestTypeEnumList;
        }

        for (Bundle.BundleEntryComponent entryComponent : bundle.getEntry()) {
            Bundle.HTTPVerb httpMethod = entryComponent.getRequest().getMethod();
            if (httpMethod != Bundle.HTTPVerb.GET && !entryComponent.hasResource()) {
                invalidRequestException =
                        new InvalidRequestException("Bundle entry requires a resource field!");
                ExceptionUtil.throwRuntimeExceptionAndLog(
                        logger, invalidRequestException.getMessage(), invalidRequestException);
            }

            requestTypeEnumList.add(
                    new BundleResources(
                            RequestTypeEnum.valueOf(httpMethod.name()),
                            entryComponent.getResource()));
        }

        return requestTypeEnumList;
    }

    @VisibleForTesting
    public IBaseResource createResourceFromRequest(RequestDetailsReader request) {
        byte[] requestContentBytes = request.loadRequestContents();
        Charset charset = request.getCharset();
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        String requestContent = new String(requestContentBytes, charset);
        IParser jsonParser = fhirContext.newJsonParser();
        return jsonParser.parseResource(requestContent);
    }
}
