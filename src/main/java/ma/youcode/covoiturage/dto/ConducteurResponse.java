package ma.youcode.covoiturage.dto;

public record ConducteurResponse(
        Long id,
        String lastName,
        String firstName,
        String email,
        String phone,
        String licenseNumber
) {
}