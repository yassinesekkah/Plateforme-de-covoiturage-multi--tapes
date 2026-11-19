package ma.youcode.covoiturage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.youcode.covoiturage.enums.StatutTrajet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trajets")
@Getter
@Setter
@NoArgsConstructor
public class Trajet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducteur_id", nullable = false)
    private Conducteur conducteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private Vehicule vehicule;

    @Column(nullable = false)
    private String departureCity;

    @Column(nullable = false)
    private String arrivalCity;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private Integer offeredSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutTrajet status = StatutTrajet.PUBLIE;

    @OneToMany(mappedBy = "trajet", cascade = CascadeType.ALL)
    @OrderBy("stopOrder ASC")
    private List<PointArret> pointsArret = new ArrayList<>();

    public void addPointArret(PointArret pointArret) {
        pointsArret.add(pointArret);
        pointArret.setTrajet(this);
    }
}
