package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.ReservationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationCancellationControllerTest {
    private static final String URL = "/api/passagers/1/reservations/20/annuler";
    private ReservationService service;
    private MockMvc mvc;

    @BeforeEach void prepare() {
        service = mock(ReservationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void forwardsIdsAndReturns204WithoutBody() throws Exception {
        mvc.perform(patch(URL).header("X-Passager-Id", "1"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).cancel(1L, 20L, 1L);
        verifyNoMoreInteractions(service);
    }
    @Test void requiresActorHeader() throws Exception {
        mvc.perform(patch(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void conducteurHeaderDoesNotReplacePassengerHeader() throws Exception {
        mvc.perform(patch(URL).header("X-Conducteur-Id", "1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericActor() throws Exception {
        mvc.perform(patch(URL).header("X-Passager-Id", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {
            "/api/passagers/abc/reservations/20/annuler", "/api/passagers/1/reservations/abc/annuler"})
    void rejectsNonNumericPathIds(String url) throws Exception {
        mvc.perform(patch(url).header("X-Passager-Id", "1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void forwardsActualActorToServiceForOwnershipCheck() throws Exception {
        doThrow(new ForbiddenException("Action interdite")).when(service).cancel(1L, 20L, 2L);
        mvc.perform(patch(URL).header("X-Passager-Id", "2"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verify(service).cancel(1L, 20L, 2L);
    }
    @ParameterizedTest @ValueSource(ints = {403, 404, 409})
    void returnsServiceErrorsAsProblemDetail(int code) throws Exception {
        RuntimeException error = switch (code) {
            case 403 -> new ForbiddenException("Reservation d'un autre passager");
            case 404 -> new ResourceNotFoundException("Reservation introuvable");
            default -> new BusinessException("Annulation trop tardive");
        };
        doThrow(error).when(service).cancel(1L, 20L, 1L);
        mvc.perform(patch(URL).header("X-Passager-Id", "1"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.detail").value(error.getMessage()))
                .andExpect(jsonPath("$.instance").value(URL)).andExpect(jsonPath("$.timestamp").exists());
    }
    @Test void unsupportedMethodReturns405InsteadOf500() throws Exception {
        mvc.perform(delete(URL).header("X-Passager-Id", "1"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
        verifyNoInteractions(service);
    }
}
