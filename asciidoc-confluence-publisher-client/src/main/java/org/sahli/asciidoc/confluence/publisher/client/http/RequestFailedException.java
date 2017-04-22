package org.sahli.asciidoc.confluence.publisher.client.http;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import static org.sahli.asciidoc.confluence.publisher.client.utils.InputStreamUtils.inputStreamAsString;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
@SuppressWarnings("WeakerAccess")
public class RequestFailedException extends RuntimeException {

    RequestFailedException(HttpRequest request, HttpResponse response) {
        super(""
                + response.getStatusLine().getStatusCode()
                + " "
                + response.getStatusLine().getReasonPhrase()
                + " "
                + request.getRequestLine().getMethod()
                + " "
                + request.getRequestLine().getUri()
                + " "
                + failedResponseContent(response)
        );
    }

    private static String failedResponseContent(HttpResponse response) {
        try {
            return inputStreamAsString(response.getEntity().getContent());
        } catch (Exception ignored) {
            return "";
        }
    }

}
