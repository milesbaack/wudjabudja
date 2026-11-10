package com.wudjabudja.services;

/**
 * The outcome of one backend call: worked or not, a message to show, and the parsed data.
 * status is the HTTP code (0 if the backend couldn't be reached at all).
 */
public record ApiResult<T>(boolean success, int status, String message, T data) {

    public static <T> ApiResult<T> ok(int status, T data) {
        return new ApiResult<>(true, status, "OK", data);
    }

    public static <T> ApiResult<T> fail(int status, String message) {
        return new ApiResult<>(false, status, message, null);
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
