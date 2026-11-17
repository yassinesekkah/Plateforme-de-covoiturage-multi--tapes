package ma.youcode.covoiturage.dto.vehicule;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class VehiculeValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 9})
    void rejectsInvalidCapacityForCreateAndUpdate(Integer capacity) {
        assertThat(validator.validate(new VehiculeCreateRequest("Dacia", "Logan", "ABC", "Blanc", capacity)))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("capacity"));
        assertThat(validator.validate(new VehiculeUpdateRequest("Dacia", "Logan", "ABC", "Blanc", capacity)))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("capacity"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8})
    void acceptsCapacityBoundaries(int capacity) {
        assertThat(validator.validate(new VehiculeCreateRequest("Dacia", "Logan", "ABC", "Blanc", capacity)))
                .isEmpty();
        assertThat(validator.validate(new VehiculeUpdateRequest("Dacia", "Logan", "ABC", "Blanc", capacity)))
                .isEmpty();
    }

    @Test
    void rejectsMissingTextFields() {
        assertThat(validator.validate(new VehiculeCreateRequest("", " ", null, "", 4))).hasSize(4);
    }
}
