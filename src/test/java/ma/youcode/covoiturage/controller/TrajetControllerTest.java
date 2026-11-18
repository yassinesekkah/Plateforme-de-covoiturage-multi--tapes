package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.dto.vehicule.VehiculeResponse;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.TrajetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrajetControllerTest {
    private TrajetService service;
    private MockMvc mvc;

    @BeforeEach void prepare() {
        service = mock(TrajetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TrajetController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void returnsDetailWithoutActorHeader() throws Exception {
        var time = LocalDateTime.of(2030, 1, 1, 17, 0);
        var detail = new TrajetDetailResponse(10L,
                new ConducteurTrajetResponse(1L, "Alami", "Yassine", "0600000000"),
                new VehiculeResponse(2L, "Dacia", "Logan", "TEST", "Blanc", 4, 1L),
                "Rabat", "Casablanca", time, 3, 1, StatutTrajet.PUBLIE,
                List.of(new PointArretResponse(20L, "Rabat", 0, time, BigDecimal.ZERO),
                        new PointArretResponse(21L, "Casablanca", 1, time.plusHours(1), new BigDecimal("40.00"))));
        when(service.getById(10L)).thenReturn(detail);

        mvc.perform(get("/api/trajets/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.conducteur.firstName").value("Yassine"))
                .andExpect(jsonPath("$.conducteur.licenseNumber").doesNotExist())
                .andExpect(jsonPath("$.vehicule.model").value("Logan"))
                .andExpect(jsonPath("$.status").value("PUBLIE"))
                .andExpect(jsonPath("$.offeredSeats").value(3))
                .andExpect(jsonPath("$.pointsArret[1].city").value("Casablanca"))
                .andExpect(jsonPath("$.remainingSeats").value(1));
    }

    @Test void returns404ForMissingTrip() throws Exception {
        when(service.getById(99L)).thenThrow(new ResourceNotFoundException("Trajet introuvable"));
        mvc.perform(get("/api/trajets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/trajets/99"));
    }

    @Test void returns400ForNonNumericId() throws Exception {
        mvc.perform(get("/api/trajets/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test void keepsPublicationAtItsOriginalUrl() throws Exception {
        mvc.perform(post("/api/conducteurs/1/trajets"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
