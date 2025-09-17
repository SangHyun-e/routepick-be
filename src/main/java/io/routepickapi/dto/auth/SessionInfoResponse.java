package io.routepickapi.dto.auth;

public record SessionInfoResponse(
    String tokenId,
    long issuedAtEpochSec,
    long expiresAtEpochSec,
    long ttlSec
) {

}
