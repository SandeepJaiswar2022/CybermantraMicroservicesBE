package com.cybermantra.microservices.in.paymentservice.service.jwt;

public  interface JwtService {
    boolean isTokenValid(String token);
//    static Long extractUserId(String token);
    String extractSubject(String token);

//    Long extractUserId(String token);

    String extractRole(String token);
}



