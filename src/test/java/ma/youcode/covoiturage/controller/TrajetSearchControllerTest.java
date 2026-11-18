package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.service.TrajetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrajetSearchControllerTest {
    private static final String URL = "/api/trajets/search";
    private TrajetService service;
    private MockMvc mvc;

    @BeforeEach void prepare() {
        service = mock(TrajetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TrajetController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private LinkedMultiValueMap<String, String> params() {
        var p = new LinkedMultiValueMap<String, String>();
        p.add("departureCity", "Casablanca"); p.add("arrivalCity", "Settat");
        p.add("date", "2030-01-01"); p.add("seats", "2"); return p;
    }

    @Test void bindsQueryParametersAndReturnsStablePaginationJsonWithoutHeaders() throws Exception {
        var day = LocalDate.of(2030, 1, 1);
        var request = new TrajetSearchRequest("Casablanca", "Settat", day, 2, new BigDecimal("30.00"), 1, 2);
        var row = new TrajetSearchResult(10L, day.atTime(17, 0), 20L, "Casablanca",
                day.atTime(18, 0), 21L, "Settat", day.atTime(19, 0), new BigDecimal("30.00"), 3L);
        when(service.search(request)).thenReturn(new SliceImpl<>(List.of(row), PageRequest.of(1, 2), true));
        mvc.perform(get(URL).queryParams(params()).param("maxPrice", "30.00").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trajetId").value(10))
                .andExpect(jsonPath("$.content[0].monteeId").value(20))
                .andExpect(jsonPath("$.content[0].descenteId").value(21))
                .andExpect(jsonPath("$.content[0].segmentPrice").value(30))
                .andExpect(jsonPath("$.content[0].remainingSeats").value(3))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalPages").doesNotExist());
        verify(service).search(request);
        verifyNoMoreInteractions(service);
    }

    @Test void usesDefaultsAndReturns200WithEmptyContentInsteadOf404() throws Exception {
        var request = new TrajetSearchRequest("Casablanca", "Settat", LocalDate.of(2030, 1, 1), 2, null, null, null);
        when(service.search(request)).thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 10), false));
        mvc.perform(get(URL).queryParams(params()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(service).search(request);
    }

    @ParameterizedTest @ValueSource(strings = {"departureCity", "arrivalCity", "date", "seats"})
    void rejectsMissingRequiredParameter(String name) throws Exception {
        var parameters = params(); parameters.remove(name);
        mvc.perform(get(URL).queryParams(parameters))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(URL)).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @MethodSource("invalidParameters")
    void rejectsInvalidValuesBeforeCallingService(String field, String value) throws Exception {
        var parameters = params(); parameters.set(field, value);
        mvc.perform(get(URL).queryParams(parameters))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidParameters() {
        return Stream.of(
                Arguments.of("departureCity", " "), Arguments.of("arrivalCity", ""),
                Arguments.of("departureCity", "a".repeat(256)), Arguments.of("arrivalCity", "b".repeat(256)),
                Arguments.of("date", "bad-date"), Arguments.of("date", "2030-02-30"),
                Arguments.of("seats", "0"), Arguments.of("seats", "-1"), Arguments.of("seats", "abc"),
                Arguments.of("maxPrice", "-0.01"), Arguments.of("maxPrice", "30.123"),
                Arguments.of("maxPrice", "10000000000.00"), Arguments.of("maxPrice", "abc"),
                Arguments.of("page", "-1"), Arguments.of("page", "abc"),
                Arguments.of("size", "0"), Arguments.of("size", "101"), Arguments.of("size", "abc"));
    }

    @Test void preserves400FromService() throws Exception {
        when(service.search(any())).thenThrow(new BadRequestException("Criteres invalides"));
        mvc.perform(get(URL).queryParams(params())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Criteres invalides"));
    }
    @Test void preserves405ForUnsupportedMethod() throws Exception {
        mvc.perform(put(URL).queryParams(params())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
