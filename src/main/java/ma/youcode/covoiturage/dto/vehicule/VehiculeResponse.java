package ma.youcode.covoiturage.dto.vehicule;

public record VehiculeResponse(
        Long id, String brand, String model, String registrationNumber,
        String color, Integer capacity, Long conducteurId
) {
}
