package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.repository.TrajetRepository;
import ma.youcode.covoiturage.service.impl.TrajetServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajetSearchServiceTest {
    @Mock private TrajetRepository repository;
    @InjectMocks private TrajetServiceImpl service;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 31);

    @Test void trimsCitiesBuildsDayRangeAndPreservesResultAndHasNext() {
        var request = new TrajetSearchRequest(" Casablanca ", " Settat ", DAY, 2,
                new BigDecimal("30.00"), 1, 2);
        var row = new TrajetSearchResult(10L, DAY.atTime(17, 0), 20L, "Casablanca",
                DAY.atTime(18, 0), 21L, "Settat", DAY.atTime(19, 0), new BigDecimal("30.00"), 3L);
        Slice<TrajetSearchResult> expected = new SliceImpl<>(List.of(row), PageRequest.of(1, 2), true);
        when(repository.search("Casablanca", "Settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                2, new BigDecimal("30.00"), PageRequest.of(1, 2))).thenReturn(expected);

        Slice<TrajetSearchResult> actual = service.search(request);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getContent()).containsExactly(row);
        assertThat(actual.hasNext()).isTrue();
        assertThat(actual.getContent().get(0).segmentPrice()).isEqualByComparingTo("30.00");
        verify(repository).search("Casablanca", "Settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                2, new BigDecimal("30.00"), PageRequest.of(1, 2));
        verifyNoMoreInteractions(repository);
    }

    @Test void returnsEmptySliceWithout404AndUsesDefaultPaginationAndOptionalPrice() {
        var request = new TrajetSearchRequest("Rabat", "Settat", DAY, 1, null, null, null);
        Slice<TrajetSearchResult> empty = new SliceImpl<>(List.of(), PageRequest.of(0, 10), false);
        when(repository.search("Rabat", "Settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, null, PageRequest.of(0, 10))).thenReturn(empty);

        var actual = service.search(request);

        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.hasNext()).isFalse();
        verify(repository).search("Rabat", "Settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, null, PageRequest.of(0, 10));
    }

    @Test void acceptsZeroMaximumPriceAndLeavesCaseComparisonToRepository() {
        var request = new TrajetSearchRequest("CASABLANCA", "settat", DAY, 1, BigDecimal.ZERO, 0, 100);
        when(repository.search("CASABLANCA", "settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, BigDecimal.ZERO, PageRequest.of(0, 100))).thenReturn(new SliceImpl<>(List.of()));
        assertThat(service.search(request).getContent()).isEmpty();
        verify(repository).search("CASABLANCA", "settat", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, BigDecimal.ZERO, PageRequest.of(0, 100));
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void rejectsInvalidCriteriaBeforeQuery(TrajetSearchRequest request) {
        assertThatThrownBy(() -> service.search(request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    static Stream<TrajetSearchRequest> invalidRequests() {
        return Stream.of(
                null,
                new TrajetSearchRequest(null, "Settat", DAY, 1, null, 0, 10),
                new TrajetSearchRequest("Rabat", null, DAY, 1, null, 0, 10),
                new TrajetSearchRequest(" ", "Settat", DAY, 1, null, 0, 10),
                new TrajetSearchRequest("Rabat", "", DAY, 1, null, 0, 10),
                new TrajetSearchRequest("a".repeat(256), "Settat", DAY, 1, null, 0, 10),
                new TrajetSearchRequest("Rabat", "b".repeat(256), DAY, 1, null, 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", null, 1, null, 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, null, null, 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 0, null, 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, -1, null, 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, new BigDecimal("-0.01"), 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, new BigDecimal("30.123"), 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, new BigDecimal("10000000000.00"), 0, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, null, -1, 10),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, null, 0, 0),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, null, 0, -1),
                new TrajetSearchRequest("Rabat", "Settat", DAY, 1, null, 0, 101));
    }
}
