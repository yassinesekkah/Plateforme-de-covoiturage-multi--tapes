package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.TrajetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrajetFinishControllerTest {
    private static final String URL = "/api/conducteurs/1/trajets/10/terminer";
    private TrajetService service;
    private MockMvc mvc;
    @BeforeEach void prepare() {
        service = mock(TrajetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TrajetController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void returns204WithoutContent() throws Exception {
        mvc.perform(patch(URL).header("X-Conducteur-Id", "1"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).finish(1L, 10L, 1L);
    }
    @Test void requiresConducteurHeader() throws Exception {
        mvc.perform(patch(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void passengerHeaderDoesNotReplaceConducteurHeader() throws Exception {
        mvc.perform(patch(URL).header("X-Passager-Id", "1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsNonNumericActor() throws Exception {
        mvc.perform(patch(URL).header("X-Conducteur-Id", "abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {
            "/api/conducteurs/abc/trajets/10/terminer", "/api/conducteurs/1/trajets/abc/terminer"})
    void rejectsNonNumericPathIds(String url) throws Exception {
        mvc.perform(patch(url).header("X-Conducteur-Id", "1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void forwardsRealActorToService() throws Exception {
        doThrow(new ForbiddenException("Action interdite")).when(service).finish(1L, 10L, 2L);
        mvc.perform(patch(URL).header("X-Conducteur-Id", "2")).andExpect(status().isForbidden());
        verify(service).finish(1L, 10L, 2L);
    }
    @ParameterizedTest @ValueSource(ints = {403, 404, 409})
    void preservesServiceErrors(int code) throws Exception {
        RuntimeException error = switch (code) {
            case 403 -> new ForbiddenException("Autre proprietaire");
            case 404 -> new ResourceNotFoundException("Trajet introuvable");
            default -> new BusinessException("Statut incompatible");
        };
        doThrow(error).when(service).finish(1L, 10L, 1L);
        mvc.perform(patch(URL).header("X-Conducteur-Id", "1"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.detail").value(error.getMessage()))
                .andExpect(jsonPath("$.instance").value(URL)).andExpect(jsonPath("$.timestamp").exists());
    }
    @Test void rejectsDeleteMethod() throws Exception {
        mvc.perform(delete(URL).header("X-Conducteur-Id", "1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
