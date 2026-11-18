package ma.youcode.covoiturage.dto.reservation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() { factory.close(); }

    @Test void acceptsOneOrSeveralSeats() {
        assertThat(validator.validate(new ReservationCreateRequest(1L, 2L, 3L, 1))).isEmpty();
        assertThat(validator.validate(new ReservationCreateRequest(1L, 2L, 3L, 4))).isEmpty();
    }

    @Test void rejectsAllMissingFields() {
        assertThat(validator.validate(new ReservationCreateRequest(null, null, null, null)))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactlyInAnyOrder("trajetId", "monteeId", "descenteId", "nombrePlaces");
    }

    @Test void rejectsZeroOrNegativeIdsAndSeats() {
        assertThat(validator.validate(new ReservationCreateRequest(0L, -1L, 0L, 0))).hasSize(4);
        assertThat(validator.validate(new ReservationCreateRequest(-1L, 0L, -1L, -2))).hasSize(4);
    }
}
