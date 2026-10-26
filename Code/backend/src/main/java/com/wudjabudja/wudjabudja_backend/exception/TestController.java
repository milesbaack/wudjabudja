package com.wudjabudja.wudjabudja_backend.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class TestController {

    @GetMapping("/test")
    public String test() {
        throw new IllegalArgumentException("Testing the exception handler!");
    }

    @GetMapping("/test404")
    public String test404() {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found");
    }

    @GetMapping("/test403")
    public String test403() {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access denied");
    }

    @GetMapping("/test500")
    public String test500() {
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }
    @GetMapping("/test504")
    public String test504() {
        throw new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT, "Something went wrong");
    }

}
