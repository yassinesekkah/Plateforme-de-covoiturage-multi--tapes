package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.dto.reservation.*;
import ma.youcode.covoiturage.enums.StatutReservation;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.ReservationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest {
    private ReservationService service;
    private MockMvc mvc;
    private static final String URL = "/api/passagers/1/reservations";
    private static final String BODY = """
            {"trajetId":10,"monteeId":20,"descenteId":21,"nombrePlaces":2}
            """;

    @BeforeEach void prepare() {
        service = mock(ReservationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void returns201AndPassesActorAndRequestToService() throws Exception {
        var request = new ReservationCreateRequest(10L, 20L, 21L, 2);
        when(service.create(1L, 1L, request)).thenReturn(new ReservationResponse(
                30L, 1L, 10L, 20L, 21L, 2, new BigDecimal("160.00"),
                LocalDateTime.of(2030, 1, 1, 12, 0), StatutReservation.CONFIRMEE));
        mvc.perform(post(URL).header("X-Passager-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.passagerId").value(1))
                .andExpect(jsonPath("$.trajetId").value(10))
                .andExpect(jsonPath("$.monteeId").value(20))
                .andExpect(jsonPath("$.descenteId").value(21))
                .andExpect(jsonPath("$.nombrePlaces").value(2))
                .andExpect(jsonPath("$.montantTotal").value(160))
                .andExpect(jsonPath("$.dateReservation").exists())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"));
        verify(service).create(1L, 1L, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"trajetId\":0,\"monteeId\":20,\"descenteId\":21,\"nombrePlaces\":2}",
            "{\"trajetId\":10,\"monteeId\":-1,\"descenteId\":21,\"nombrePlaces\":2}",
            "{\"trajetId\":10,\"monteeId\":20,\"descenteId\":0,\"nombrePlaces\":2}",
            "{\"trajetId\":10,\"monteeId\":20,\"descenteId\":21,\"nombrePlaces\":0}",
            "{\"trajetId\":10,\"monteeId\":20,\"descenteId\":21,\"nombrePlaces\":-2}",
            "{\"trajetId\":10,\"monteeId\":20,\"descenteId\":21,\"nombrePlaces\":null}",
            "{\"trajetId\":"
    })
    void rejectsInvalidPayloadBeforeCallingService(String body) throws Exception {
        mvc.perform(post(URL).header("X-Passager-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test void requiresActorHeader() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericActor() throws Exception {
        mvc.perform(post(URL).header("X-Passager-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericPassenger() throws Exception {
        mvc.perform(post("/api/passagers/abc/reservations").header("X-Passager-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(ints = {400, 403, 404, 409})
    void preservesServiceErrorsAsProblemDetail(int status) throws Exception {
        RuntimeException error = switch (status) {
            case 400 -> new BadRequestException("La montee doit preceder la descente");
            case 403 -> new ForbiddenException("Action interdite");
            case 404 -> new ResourceNotFoundException("Trajet introuvable");
            default -> new BusinessException("Places insuffisantes");
        };
        when(service.create(eq(1L), eq(1L), any())).thenThrow(error);
        mvc.perform(post(URL).header("X-Passager-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(error.getMessage()))
                .andExpect(jsonPath("$.instance").value(URL))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
