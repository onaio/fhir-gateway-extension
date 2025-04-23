package org.smartregister.fhir.gateway.plugins.endpoint;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;

import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class BaseEndpoint extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(BaseEndpoint.class);

    protected static TokenVerifier tokenVerifier;
    protected final FhirContext fhirR4Context = FhirContext.forR4Cached();
    protected final IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

    static {
        try {
            tokenVerifier = TokenVerifier.createFromEnvVars();
        } catch (Exception exception) {
            logger.error(exception.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        RestUtils.addCorsHeaders(response);
    }

    protected BaseEndpoint() {}

    protected void writeUTF8StringToStream(OutputStream fileOutputStream, String content) {
        try (OutputStreamWriter outputStreamWriter =
                        new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
                PrintWriter printWriter = new PrintWriter(outputStreamWriter)) {
            printWriter.println(content);
            printWriter.flush();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
