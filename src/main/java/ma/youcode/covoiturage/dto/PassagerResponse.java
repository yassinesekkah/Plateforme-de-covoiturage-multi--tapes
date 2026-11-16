package ma.youcode.covoiturage.dto;

public record PassagerResponse(
        Long id,
        String lastName,
        String firstName,
        String email,
        String phone
) {
}
