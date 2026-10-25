package dev.w0fv1.browserbridge;

final class CdpException extends RuntimeException {
    CdpException(String method, int code, String message) {
        super(method + " 失败 (" + code + "): " + message);
    }
}
