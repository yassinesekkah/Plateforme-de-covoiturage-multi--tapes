package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.dto.reservation.*;
import ma.youcode.covoiturage.dto.trajet.PointArretResponse;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.ReservationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationHistoryControllerTest {
    private ReservationService service;
    private MockMvc mvc;
    private static final String URL = "/api/passagers/1/reservations";

    @BeforeEach void prepare() {
        service = mock(ReservationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private ReservationDetailResponse response(long id, StatutReservation statut) {
        var time = LocalDateTime.of(2030, 1, 1, 12, 0);
        return new ReservationDetailResponse(id, 1L,
                new TrajetReservationResponse(10L, "Rabat", "Marrakech", time, StatutTrajet.TERMINE),
                new PointArretResponse(20L, "Rabat", 0, time, BigDecimal.ZERO),
                new PointArretResponse(21L, "Marrakech", 1, time.plusHours(4), new BigDecimal("120.00")),
                1, new BigDecimal("120.00"), time.minusDays(1), statut);
    }

    @Test void returns200WithReadableConfirmedAndCancelledHistory() throws Exception {
        when(service.getByPassager(1L, 1L)).thenReturn(List.of(
                response(2L, StatutReservation.ANNULEE), response(1L, StatutReservation.CONFIRMEE)));
        mvc.perform(get(URL).header("X-Passager-Id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].passagerId").value(1))
                .andExpect(jsonPath("$[0].statut").value("ANNULEE"))
                .andExpect(jsonPath("$[1].statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$[0].trajet.id").value(10))
                .andExpect(jsonPath("$[0].trajet.status").value("TERMINE"))
                .andExpect(jsonPath("$[0].montee.city").value("Rabat"))
                .andExpect(jsonPath("$[0].descente.city").value("Marrakech"))
                .andExpect(jsonPath("$[0].montee.passageTime").exists())
                .andExpect(jsonPath("$[0].nombrePlaces").value(1))
                .andExpect(jsonPath("$[0].montantTotal").value(120))
                .andExpect(jsonPath("$[0].dateReservation").exists());
        verify(service).getByPassager(1L, 1L);
    }
    @Test void returns200AndEmptyArray() throws Exception {
        when(service.getByPassager(1L, 1L)).thenReturn(List.of());
        mvc.perform(get(URL).header("X-Passager-Id", "1"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void requiresActorHeader() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericActor() throws Exception {
        mvc.perform(get(URL).header("X-Passager-Id", "abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericPassenger() throws Exception {
        mvc.perform(get("/api/passagers/abc/reservations").header("X-Passager-Id", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(ints = {403, 404})
    void preservesAccessAndMissingPassengerErrors(int code) throws Exception {
        RuntimeException error = code == 403 ? new ForbiddenException("Acces interdit")
                : new ResourceNotFoundException("Passager introuvable");
        when(service.getByPassager(1L, 1L)).thenThrow(error);
        mvc.perform(get(URL).header("X-Passager-Id", "1"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.instance").value(URL)).andExpect(jsonPath("$.timestamp").exists());
    }
}
