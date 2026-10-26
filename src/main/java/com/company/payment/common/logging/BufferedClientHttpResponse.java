package com.company.payment.common.logging;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** A response whose body is read once into memory, so it can be logged AND read again by Spring. */
final class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private byte[] body;

    BufferedClientHttpResponse(ClientHttpResponse delegate) {
        this.delegate = delegate;
    }

    byte[] bodyBytes() throws IOException {
        if (body == null) {
            InputStream stream = delegate.getBody();
            body = stream == null ? new byte[0] : StreamUtils.copyToByteArray(stream);
        }
        return body;
    }

    @Override
    public InputStream getBody() throws IOException {
        return new ByteArrayInputStream(bodyBytes());
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
