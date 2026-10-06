package com.example.estimateserver.service;

public class MemberLimitException extends RuntimeException {
    public MemberLimitException(String message) {
        super(message);
    }
}