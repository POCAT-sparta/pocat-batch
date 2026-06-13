package com.rocketcrew.pocatbatch.client.dto;

public record ApiResponseEnvelope<T>(boolean success, int status, T data) {
}
