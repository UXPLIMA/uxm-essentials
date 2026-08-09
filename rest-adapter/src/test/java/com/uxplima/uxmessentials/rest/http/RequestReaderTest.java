package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class RequestReaderTest {

    @Test
    void readsMethodPathHeadersAndBody() throws IOException {
        HttpRequest request = read("""
                POST /api/v1/economy/deposit HTTP/1.1\r
                Host: localhost\r
                Content-Type: application/json\r
                Content-Length: 13\r
                \r
                {"amount":5}\
                """ + "\n");

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/economy/deposit");
        assertThat(request.header("content-type")).contains("application/json");
        assertThat(request.body()).isEqualTo("{\"amount\":5}\n");
    }

    @Test
    void headerNamesAreFoundInAnyCase() throws IOException {
        HttpRequest request = read("GET /a HTTP/1.1\r\nAuThOrIzAtIoN: Bearer x\r\n\r\n");

        assertThat(request.header("authorization")).contains("Bearer x");
        assertThat(request.header("AUTHORIZATION")).contains("Bearer x");
    }

    @Test
    void queryStringIsSplitAndDecoded() throws IOException {
        HttpRequest request = read("GET /api/v1/warps?page=2&q=spawn%20area HTTP/1.1\r\n\r\n");

        assertThat(request.path()).isEqualTo("/api/v1/warps");
        assertThat(request.queryParam("page")).contains("2");
        assertThat(request.queryParam("q")).contains("spawn area");
    }

    @Test
    void aRequestWithoutContentLengthHasAnEmptyBody() throws IOException {
        assertThat(read("GET /a HTTP/1.1\r\n\r\n").body()).isEmpty();
    }

    @Test
    void aChunkedBodyIsRefusedRatherThanGuessedAt() {
        assertThatThrownBy(() -> read("POST /a HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void aHeadThatNeverEndsIsRefusedBeforeItIsBuffered() {
        String endless = "GET /a HTTP/1.1\r\n" + "X-Filler: " + "a".repeat(RequestReader.MAX_HEAD_BYTES) + "\r\n";

        assertThatThrownBy(() -> read(endless))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void aDeclaredBodyBiggerThanTheLimitIsRefusedWithoutReadingIt() {
        String head = "POST /a HTTP/1.1\r\nContent-Length: " + (RequestReader.MAX_BODY_BYTES + 1) + "\r\n\r\n";

        assertThatThrownBy(() -> read(head))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void tooManyHeadersIsRefused() {
        StringBuilder request = new StringBuilder("GET /a HTTP/1.1\r\n");
        for (int at = 0; at <= RequestReader.MAX_HEADERS; at++) {
            request.append("X-").append(at).append(": v\r\n");
        }
        request.append("\r\n");

        assertThatThrownBy(() -> read(request.toString()))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void aMalformedRequestLineIsABadRequest() {
        assertThatThrownBy(() -> read("NONSENSE\r\n\r\n"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aPathWithoutALeadingSlashIsABadRequest() {
        assertThatThrownBy(() -> read("GET api/v1/status HTTP/1.1\r\n\r\n"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aBodyShorterThanItsContentLengthIsABadRequest() {
        assertThatThrownBy(() -> read("POST /a HTTP/1.1\r\nContent-Length: 50\r\n\r\nshort"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aConnectionThatEndsMidRequestIsABadRequest() {
        assertThatThrownBy(() -> read("GET /a HTTP/1.1\r\nHost: local"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static HttpRequest read(String raw) throws IOException {
        InputStream stream = new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
        return RequestReader.read(stream);
    }
}
