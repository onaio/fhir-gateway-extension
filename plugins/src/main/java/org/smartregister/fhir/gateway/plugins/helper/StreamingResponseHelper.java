package org.smartregister.fhir.gateway.plugins.helper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

import org.hl7.fhir.r4.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.Constants;

import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Helper class for streaming large responses to improve memory usage and response time. This is
 * particularly useful for large location datasets that would otherwise consume significant memory
 * when loaded entirely into memory.
 */
public class StreamingResponseHelper {
    private static final Logger logger = LoggerFactory.getLogger(StreamingResponseHelper.class);

    private static final int DEFAULT_CHUNK_SIZE = 100;
    private static final int MAX_CHUNK_SIZE = 500;

    private final IParser fhirJsonParser;

    public StreamingResponseHelper(IParser fhirJsonParser) {
        this.fhirJsonParser = fhirJsonParser;
    }

    /** Stream a large list of locations as a paginated FHIR Bundle */
    public void streamLocationBundle(
            HttpServletRequest request,
            HttpServletResponse response,
            List<Location> locations,
            int page,
            int pageSize,
            int totalCount)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (OutputStream outputStream = response.getOutputStream();
                OutputStreamWriter writer =
                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PrintWriter printWriter = new PrintWriter(writer)) {

            // Start JSON object
            printWriter.println("{");
            printWriter.println("  \"resourceType\": \"Bundle\",");
            printWriter.println("  \"id\": \"" + java.util.UUID.randomUUID().toString() + "\",");
            printWriter.println("  \"type\": \"searchset\",");
            printWriter.println("  \"total\": " + totalCount + ",");

            // Add self link
            printWriter.println("  \"link\": [");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"self\",");
            String queryString = request.getQueryString();
            printWriter.println(
                    "      \"url\": \""
                            + request.getRequestURL()
                            + (queryString != null && !queryString.isEmpty()
                                    ? "?" + queryString
                                    : "")
                            + "\"");
            printWriter.println("    }");

            // Add pagination links
            addPaginationLinks(printWriter, request, page, totalCount, pageSize);
            printWriter.println("  ],");

            // Start entries array
            printWriter.println("  \"entry\": [");

            // Stream locations in chunks
            streamLocationEntries(printWriter, locations, page, pageSize);

            // End entries array and JSON object
            printWriter.println("  ]");
            printWriter.println("}");

            printWriter.flush();
        }
    }

    /** Stream locations using a function that provides data in chunks */
    public void streamLocationBundleWithChunking(
            HttpServletRequest request,
            HttpServletResponse response,
            BiFunction<Integer, Integer, List<Location>> locationProvider,
            int totalCount,
            int page,
            int pageSize)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (OutputStream outputStream = response.getOutputStream();
                OutputStreamWriter writer =
                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PrintWriter printWriter = new PrintWriter(writer)) {

            // Start JSON object
            printWriter.println("{");
            printWriter.println("  \"resourceType\": \"Bundle\",");
            printWriter.println("  \"id\": \"" + java.util.UUID.randomUUID().toString() + "\",");
            printWriter.println("  \"type\": \"searchset\",");
            printWriter.println("  \"total\": " + totalCount + ",");

            // Add self link
            printWriter.println("  \"link\": [");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"self\",");
            StringBuilder selfUrlBuilder = new StringBuilder();
            selfUrlBuilder.append(request.getRequestURL());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                selfUrlBuilder.append("?").append(queryString);
            }
            printWriter.println("      \"url\": \"" + selfUrlBuilder.toString() + "\"");
            printWriter.println("    }");

            // Add pagination links
            addPaginationLinks(printWriter, request, page, totalCount, pageSize);
            printWriter.println("  ],");

            // Start entries array
            printWriter.println("  \"entry\": [");

            // Stream locations in chunks
            streamLocationEntriesWithChunking(printWriter, locationProvider, page, pageSize);

            // End entries array and JSON object
            printWriter.println("  ]");
            printWriter.println("}");

            printWriter.flush();
        }
    }

    /** Stream location entries from a list */
    private void streamLocationEntries(
            PrintWriter printWriter, List<Location> locations, int page, int pageSize)
            throws IOException {

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, locations.size());

        boolean first = true;
        for (int i = start; i < end; i++) {
            if (!first) {
                printWriter.println(",");
            }
            first = false;

            Location location = locations.get(i);
            streamLocationEntry(printWriter, location);
        }
    }

    /** Stream location entries using chunked data provider */
    private void streamLocationEntriesWithChunking(
            PrintWriter printWriter,
            BiFunction<Integer, Integer, List<Location>> locationProvider,
            int page,
            int pageSize)
            throws IOException {

        int start = (page - 1) * pageSize;
        int end = start + pageSize;

        boolean first = true;
        int currentIndex = start;

        while (currentIndex < end) {
            // Calculate remaining items to fetch
            int remainingItems = end - currentIndex;
            int chunkSize = Math.min(remainingItems, getOptimalChunkSize(remainingItems));

            // Get chunk of data using offset and limit
            List<Location> chunk = locationProvider.apply(currentIndex, chunkSize);
            if (chunk == null || chunk.isEmpty()) {
                break;
            }

            // Stream locations from this chunk
            for (Location location : chunk) {
                if (currentIndex >= end) {
                    break;
                }

                if (!first) {
                    printWriter.println(",");
                }
                first = false;

                streamLocationEntry(printWriter, location);
                currentIndex++;
            }
        }
    }

    /** Stream a single location entry */
    private void streamLocationEntry(PrintWriter printWriter, Location location)
            throws IOException {
        printWriter.println("    {");
        printWriter.println(
                "      \"resource\": " + fhirJsonParser.encodeResourceToString(location));
        printWriter.print("    }");
    }

    /** Add pagination links to the response */
    private void addPaginationLinks(
            PrintWriter printWriter,
            HttpServletRequest request,
            int page,
            int totalCount,
            int pageSize) {

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        // Add first page link
        if (page > 1) {
            printWriter.println(",");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"first\",");
            printWriter.println(
                    "      \"url\": \"" + buildPaginationUrl(request, 1, pageSize) + "\"");
            printWriter.println("    }");
        }

        // Add previous page link
        if (page > 1) {
            printWriter.println(",");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"previous\",");
            printWriter.println(
                    "      \"url\": \"" + buildPaginationUrl(request, page - 1, pageSize) + "\"");
            printWriter.println("    }");
        }

        // Add next page link
        if (page < totalPages) {
            printWriter.println(",");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"next\",");
            printWriter.println(
                    "      \"url\": \"" + buildPaginationUrl(request, page + 1, pageSize) + "\"");
            printWriter.println("    }");
        }

        // Add last page link
        if (page < totalPages) {
            printWriter.println(",");
            printWriter.println("    {");
            printWriter.println("      \"relation\": \"last\",");
            printWriter.println(
                    "      \"url\": \"" + buildPaginationUrl(request, totalPages, pageSize) + "\"");
            printWriter.println("    }");
        }
    }

    /** Build pagination URL with updated page parameters */
    private String buildPaginationUrl(HttpServletRequest request, int page, int pageSize) {
        StringBuilder url = new StringBuilder(request.getRequestURL());
        url.append("?");

        // Copy existing parameters
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (!Constants.PAGINATION_PAGE_NUMBER.equals(key)
                                    && !Constants.PAGINATION_PAGE_SIZE.equals(key)) {
                                for (String value : values) {
                                    url.append(key).append("=").append(value).append("&");
                                }
                            }
                        });

        // Add pagination parameters
        url.append(Constants.PAGINATION_PAGE_NUMBER).append("=").append(page).append("&");
        url.append(Constants.PAGINATION_PAGE_SIZE).append("=").append(pageSize);

        return url.toString();
    }

    /** Check if streaming should be used based on data size */
    public static boolean shouldUseStreaming(int dataSize, int threshold) {
        return dataSize > threshold;
    }

    /** Get optimal chunk size based on available memory and data size */
    public static int getOptimalChunkSize(int totalSize) {
        if (totalSize <= 100) {
            return totalSize; // Small datasets, no chunking needed
        } else if (totalSize <= 1000) {
            return DEFAULT_CHUNK_SIZE;
        } else {
            return Math.min(MAX_CHUNK_SIZE, totalSize / 10); // Adaptive chunking
        }
    }
}
