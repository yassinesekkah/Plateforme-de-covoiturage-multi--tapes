package ma.youcode.covoiturage.dto.trajet;

import java.util.List;

public record TrajetSearchResponse(
        List<TrajetSearchResult> content,
        int page,
        int size,
        boolean hasNext
) {
}
