package ma.youcode.covoiturage.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void shouldReturn409WhenSqlStateIs23505() {
        // Arrange : simuler une violation de contrainte unique
        SQLException sqlException =
                new SQLException("Detail SQL interne", "23505");

        RuntimeException intermediateException =
                new RuntimeException(sqlException);

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Erreur de sauvegarde",
                        intermediateException
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/conducteurs"
                );

        // Act
        ResponseEntity<ProblemDetail> response =
                handler.handleDataIntegrityViolation(exception, request);

        // Assert
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        ProblemDetail problem = response.getBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle())
                .isEqualTo("Donnees deja existantes");
        assertThat(problem.getDetail())
                .isEqualTo(
                        "Une valeur devant etre unique est deja utilisee"
                );
        assertThat(problem.getInstance().toString())
                .isEqualTo("/api/conducteurs");
        assertThat(problem.getProperties())
                .containsKey("timestamp");
    }

    @Test
    void shouldReturn500WhenSqlStateIsNot23505() {
        // Arrange : simuler une autre erreur de contrainte
        SQLException sqlException =
                new SQLException("Detail SQL interne", "23502");

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Erreur de sauvegarde",
                        sqlException
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/conducteurs"
                );

        // Act
        ResponseEntity<ProblemDetail> response =
                handler.handleDataIntegrityViolation(exception, request);

        // Assert
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ProblemDetail problem = response.getBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail())
                .isEqualTo("Une erreur interne est survenue");
        assertThat(problem.getInstance().toString())
                .isEqualTo("/api/conducteurs");
        assertThat(problem.getProperties())
                .containsKey("timestamp");
    }
}