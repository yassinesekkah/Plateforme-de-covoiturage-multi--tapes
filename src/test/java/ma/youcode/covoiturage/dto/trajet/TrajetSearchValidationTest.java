package ma.youcode.covoiturage.dto.trajet;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class TrajetSearchValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private static final LocalDate DATE = LocalDate.of(2030, 1, 1);

    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    @Test void acceptsRequiredFieldsAndDefaultPagination() {
        var request = new TrajetSearchRequest("Casablanca", "Settat", DATE, 1, null, null, null);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.page()).isZero();
        assertThat(request.size()).isEqualTo(10);
    }
    @Test void acceptsOptionalPriceAndPaginationBoundaries() {
        assertThat(validator.validate(new TrajetSearchRequest("Casablanca", "Settat", DATE,
                2, new BigDecimal("30.00"), 0, 1))).isEmpty();
        assertThat(validator.validate(new TrajetSearchRequest("Rabat", "Marrakech", DATE,
                4, new BigDecimal("0.00"), 2, 100))).isEmpty();
    }
    @Test void rejectsMissingRequiredFields() {
        assertThat(validator.validate(new TrajetSearchRequest(null, null, null, null, null, null, null)))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactlyInAnyOrder("departureCity", "arrivalCity", "date", "seats");
    }
    @Test void rejectsBlankCities() {
        assertThat(validator.validate(new TrajetSearchRequest(" ", "", DATE, 1, null, 0, 10)))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactlyInAnyOrder("departureCity", "arrivalCity");
    }
    @Test void rejectsCitiesLongerThanDatabaseColumns() {
        assertThat(validator.validate(new TrajetSearchRequest("a".repeat(256), "b".repeat(256),
                DATE, 1, null, 0, 10))).hasSize(2);
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void rejectsNonPositiveSeats(int seats) {
        assertThat(validator.validate(new TrajetSearchRequest("Rabat", "Settat", DATE,
                seats, null, 0, 10)))
                .extracting(error -> error.getPropertyPath().toString()).containsExactly("seats");
    }
    @ParameterizedTest @ValueSource(strings = {"-0.01", "30.123", "10000000000.00"})
    void rejectsInvalidMaximumPrice(String price) {
        assertThat(validator.validate(new TrajetSearchRequest("Rabat", "Settat", DATE,
                1, new BigDecimal(price), 0, 10)))
                .extracting(error -> error.getPropertyPath().toString()).containsExactly("maxPrice");
    }
    @Test void rejectsNegativePage() {
        assertThat(validator.validate(new TrajetSearchRequest("Rabat", "Settat", DATE, 1, null, -1, 10)))
                .extracting(error -> error.getPropertyPath().toString()).containsExactly("page");
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 101})
    void rejectsPageSizeOutsideBounds(int size) {
        assertThat(validator.validate(new TrajetSearchRequest("Rabat", "Settat", DATE, 1, null, 0, size)))
                .extracting(error -> error.getPropertyPath().toString()).containsExactly("size");
    }
}
