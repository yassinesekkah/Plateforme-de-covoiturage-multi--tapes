# COV-6 — Conception simple

Cette conception décrit le modèle à développer, pas des classes déjà implémentées.

## Ouvrir le diagramme

- `diagramme-classes.svg` : image à ouvrir dans un navigateur.
- `diagramme-classes.drawio` : ouvrir dans https://app.diagrams.net avec Fichier > Ouvrir depuis > Appareil. Les classes et relations sont modifiables. Exporter en PNG avec Fichier > Exporter en tant que > PNG si nécessaire.
- `diagramme-classes.puml` : source PlantUML pour un outil compatible.
- `generate-diagram.cjs` : source commune des trois fichiers. Régénérer avec `node docs/conception/generate-diagram.cjs` depuis la racine. Une régénération remplace les modifications manuelles des fichiers générés.

## Lire les relations

| Relation | Explication | Côté JPA prévu |
|---|---|---|
| Conducteur 1 — 0..* Vehicule | Un conducteur possède zéro ou plusieurs véhicules. Chaque véhicule possède un seul propriétaire. | Vehicule.conducteur : ManyToOne |
| Conducteur 1 — 0..* Trajet | Un conducteur publie plusieurs trajets. Chaque trajet a un conducteur. | Trajet.conducteur : ManyToOne |
| Vehicule 1 — 0..* Trajet | Un véhicule peut servir pour plusieurs trajets. Un trajet utilise un seul véhicule, appartenant à son conducteur. | Trajet.vehicule : ManyToOne |
| Trajet 1 — 2..* PointArret | Un trajet contient au moins deux points ordonnés. Chaque point appartient à un seul trajet et n’existe pas sans lui. Le losange plein représente cette composition. | Trajet.pointsArret : OneToMany ; PointArret.trajet : ManyToOne |
| Passager 1 — 0..* Reservation | Un passager peut avoir plusieurs réservations. Une réservation appartient à un seul passager. | Reservation.passager : ManyToOne |
| Trajet 1 — 0..* Reservation | Un trajet reçoit plusieurs réservations. Chaque réservation concerne un seul trajet. | Reservation.trajet : ManyToOne |
| PointArret 1 — 0..* Reservation (montée) | Une réservation a un seul point de montée. Plusieurs réservations peuvent partager ce point. | Reservation.pointMontee : ManyToOne |
| PointArret 1 — 0..* Reservation (descente) | Une réservation a un seul point de descente. Plusieurs réservations peuvent partager ce point. | Reservation.pointDescente : ManyToOne |

Les relations suffisent à représenter les références aux objets : pas besoin de répéter conducteurId, trajetId, etc. dans les attributs UML. Les clés étrangères seront créées dans les tables. Les collections inverses JPA sont facultatives : une association UML n’impose pas de créer une collection Java partout.

## Décisions et règles importantes

- Les profils Conducteur et Passager restent deux classes distinctes, sans héritage supplémentaire.
- Il n’y a pas de classe Segment : une partie du parcours est définie par deux points d’arrêt.
- Les points de montée et de descente d’une réservation doivent appartenir à son trajet. L’ordre de montée est inférieur à l’ordre de descente.
- Exemple : Rabat (0 DH), Casablanca (40 DH), Settat (70 DH), Marrakech (120 DH). Deux places Casablanca → Marrakech coûtent (120 − 40) × 2 = 160 DH.
- Les places restantes sont calculées : places proposées moins les places des réservations CONFIRMEE. Une place est occupée sur tout le trajet, même pour une réservation partielle.
- `/placesRestantes` est un attribut dérivé UML : il ne devient pas une colonne. Le prix du segment n’est pas stocké non plus. Le montant total de la réservation est conservé.
- Les attributs villeDepart, villeArrivee et dateDepart du trajet sont conservés pour suivre le modèle du brief. Ils sont déterminés par les points et ne sont pas saisis indépendamment.
- Les heures sont des LocalDateTime pour gérer un trajet passant à la date suivante. Tous les montants utilisent BigDecimal à deux décimales.
- L’email conducteur, le permis et l’immatriculation sont uniques comme demandé dans le brief. L’unicité de l’email passager n’est pas ajoutée sans décision explicite.
- La contrainte « une réservation CONFIRMEE par passager et par trajet » n’est pas une relation 1 à 1 : les réservations annulées restent dans l’historique.
- La composition concerne les points du trajet. Elle ne signifie pas qu’annuler un trajet supprime ses points ou ses réservations : une annulation change les statuts.
- Les règles métier (dates, capacités, propriétaire, statuts) seront vérifiées dans les services. Le diagramme reste centré sur les entités ; controllers, services, repositories et DTO seront développés fonctionnalité par fonctionnalité.
