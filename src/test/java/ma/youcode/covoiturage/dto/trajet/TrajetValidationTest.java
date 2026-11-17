package ma.youcode.covoiturage.dto.trajet;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TrajetValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }
    private PointArretCreateRequest point() {
        return new PointArretCreateRequest("Rabat", LocalDateTime.now().plusDays(2), BigDecimal.ZERO);
    }

    @Test void rejectsMissingListAndTooFewPoints() {
        assertThat(validator.validate(new TrajetCreateRequest(1L, 1, null))).isNotEmpty();
        assertThat(validator.validate(new TrajetCreateRequest(1L, 1, List.of(point())))).isNotEmpty();
    }
    @Test void rejectsNullElementInsideList() {
        assertThat(validator.validate(new TrajetCreateRequest(1L, 1, Arrays.asList(point(), null))))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).contains("pointsArret"));
    }
    @Test void validatesNestedPointFields() {
        PointArretCreateRequest invalid = new PointArretCreateRequest(" ", null, new BigDecimal("1.001"));
        assertThat(validator.validate(new TrajetCreateRequest(1L, 1, List.of(point(), invalid))))
                .hasSize(3);
    }
    @Test void rejectsMissingVehicleAndSeats() {
        assertThat(validator.validate(new TrajetCreateRequest(null, null, List.of(point(), point()))))
                .hasSize(2);
    }
}
