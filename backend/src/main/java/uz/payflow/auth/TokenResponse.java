package uz.payflow.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
