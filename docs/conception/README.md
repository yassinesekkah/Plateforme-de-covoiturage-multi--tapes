# Diagramme de classes - Modele final

Ce diagramme décrit les six entités du code final, avec leurs noms d'attributs Java et leurs types. Les règles métier sont appliquées dans les DTO et services. Les relations UML présentent les liens du domaine ; elles n'imposent pas une liste Java dans chaque classe.

## Ouvrir le diagramme

- `diagramme-classes.svg` : image à ouvrir dans un navigateur.
- `diagramme-classes.drawio` : ouvrir dans https://app.diagrams.net avec Fichier > Ouvrir depuis > Appareil. Les classes et relations sont modifiables. Exporter en PNG avec Fichier > Exporter en tant que > PNG si nécessaire.
- `diagramme-classes.puml` : source PlantUML pour un outil compatible.
- `generate-diagram.cjs` : source commune des trois fichiers. Régénérer avec `node docs/conception/generate-diagram.cjs` depuis la racine. Une régénération remplace les modifications manuelles des fichiers générés.

## Lire les relations

| Relation | Explication | Côté JPA implémenté |
|---|---|---|
| Conducteur 1 — 0..* Vehicule | Un conducteur possède zéro ou plusieurs véhicules. Chaque véhicule possède un seul propriétaire. | Vehicule.conducteur : ManyToOne |
| Conducteur 1 — 0..* Trajet | Un conducteur publie plusieurs trajets. Chaque trajet a un conducteur. | Trajet.conducteur : ManyToOne |
| Vehicule 1 — 0..* Trajet | Un véhicule peut servir pour plusieurs trajets. Un trajet utilise un seul véhicule, appartenant à son conducteur. | Trajet.vehicule : ManyToOne |
| Trajet 1 — 2..* PointArret | Un trajet contient au moins deux points ordonnés. Chaque point appartient à un seul trajet et n’existe pas sans lui. Le losange plein représente cette composition. | Trajet.pointsArret : OneToMany ; PointArret.trajet : ManyToOne |
| Passager 1 — 0..* Reservation | Un passager peut avoir plusieurs réservations. Une réservation appartient à un seul passager. | Reservation.passager : ManyToOne |
| Trajet 1 — 0..* Reservation | Un trajet reçoit plusieurs réservations. Chaque réservation concerne un seul trajet. | Reservation.trajet : ManyToOne |
| PointArret 1 — 0..* Reservation (montée) | Une réservation a un seul point de montée. Plusieurs réservations peuvent partager ce point. | Reservation.montee : ManyToOne |
| PointArret 1 — 0..* Reservation (descente) | Une réservation a un seul point de descente. Plusieurs réservations peuvent partager ce point. | Reservation.descente : ManyToOne |

Les relations représentent les références aux objets : pas besoin de répéter conducteurId, trajetId, etc. dans les attributs UML. Les clés étrangères sont créées dans les tables. Conducteur n'a pas de liste de véhicules ou de trajets, et Passager n'a pas de liste de réservations : les repositories permettent de les retrouver. La seule collection d'entité est `Trajet.pointsArret`.

Une réservation possède deux références distinctes `montee` et `descente`. Chacune est une relation ManyToOne vers PointArret : les deux références ne forment pas une relation ManyToMany.

### Composition Trajet / PointArret

Le losange plein côté Trajet représente l'appartenance des points au trajet. Le code utilise `@OneToMany(mappedBy = "trajet", cascade = CascadeType.ALL)` et `@OrderBy("stopOrder ASC")`. Le côté propriétaire de la clé étrangère est `PointArret.trajet`, avec `trajet_id` non nullable. `addPointArret` renseigne les deux côtés avant la sauvegarde.

La borne `2..*` est une règle validée lors de la publication, pas une contrainte de taille JPA ou SQL. Le modèle n'utilise pas `orphanRemoval` : retirer un point d'une liste Java ne signifie pas automatiquement supprimer sa ligne. Aucun endpoint ne supprime un trajet ; les annulations changent uniquement les statuts.

## Noms des attributs dans le code

| Sens en français | Nom Java / JSON |
|---|---|
| Nom, prénom, téléphone | lastName, firstName, phone |
| Numéro de permis | licenseNumber |
| Marque, modèle, immatriculation, couleur, capacité | brand, model, registrationNumber, color, capacity |
| Départ, arrivée, date de départ, places proposées, statut du trajet | departureCity, arrivalCity, departureTime, offeredSeats, status |
| Ville, ordre, heure de passage, prix cumulé | city, stopOrder, passageTime, priceFromDeparture |
| Montée et descente d'une réservation | montee, descente dans l'entité ; monteeId, descenteId dans le DTO de création |

Les types de capacité, ordre et nombre de places sont `Integer`, comme dans les entités. Les ids sont des `Long`. Le statut du trajet s'appelle `status`, celui de la réservation `statut`.

## Décisions et règles importantes

- Les profils Conducteur et Passager restent deux classes distinctes, sans héritage supplémentaire.
- Il n’y a pas de classe Segment : une partie du parcours est définie par deux points d’arrêt.
- Les points de montée et de descente d’une réservation doivent appartenir à son trajet. L’ordre de montée est inférieur à l’ordre de descente.
- Exemple : Rabat (0 DH), Casablanca (40 DH), Settat (70 DH), Marrakech (120 DH). Deux places Casablanca → Marrakech coûtent (120 − 40) × 2 = 160 DH.
- Les places restantes sont calculées : places proposées moins les places des réservations CONFIRMEE. Une place est occupée sur tout le trajet, même pour une réservation partielle.
- `/remainingSeats` est un attribut dérivé UML : il n'est pas un champ de l'entité Trajet ni une colonne SQL. Il est calculé par le service et exposé dans les DTO de lecture ; le résultat de recherche utilise aussi un calcul JPQL. Le prix du segment n’est pas stocké non plus. Le montant total de la réservation est conservé.
- Les attributs departureCity, arrivalCity et departureTime du trajet sont déterminés par les points et ne sont pas saisis indépendamment.
- Les heures sont des LocalDateTime pour gérer un trajet passant à la date suivante. Tous les montants utilisent BigDecimal à deux décimales.
- L’email conducteur, le permis et l’immatriculation sont uniques comme demandé dans le brief. L’email passager est également unique, par choix explicite de conception. Un passager peut conserver son propre email lors d’une modification ; l’email d’un autre passager est refusé.
- La contrainte « une réservation CONFIRMEE par passager et par trajet » n’est pas une relation 1 à 1 : les réservations annulées restent dans l’historique.
- La composition concerne les points du trajet. Elle ne signifie pas qu’annuler un trajet supprime ses points ou ses réservations : une annulation change les statuts.
- Les règles métier (dates, capacités, propriétaire, statuts) sont vérifiées dans les services. La capacité 1..8 est contrôlée par la validation DTO, pas par une annotation de capacité dans l'entité. `stopOrder` est attribué par le service ; l'unicité des villes du parcours est vérifiée par ce service, sans contrainte SQL composée ajoutée dans PointArret.
- Publication, réservation, annulations et terminaison sont transactionnelles. Réservation, annulation et terminaison réutilisent le verrou PESSIMISTIC_WRITE sur le trajet pour sérialiser ses changements de statut ou de places.

## Statuts et historique

| Action | Transition |
|---|---|
| Dernière place réservée | PUBLIE vers COMPLET |
| Annulation passager libérant une place | COMPLET vers PUBLIE |
| Terminer après l'heure de départ, conducteur propriétaire | PUBLIE ou COMPLET vers TERMINE |
| Annuler le trajet, conducteur propriétaire | PUBLIE ou COMPLET vers ANNULE ; ses réservations CONFIRMEE vers ANNULEE |

TERMINE et ANNULE sont finaux. Une annulation passager est autorisée jusqu'à exactement 24 heures avant le départ du trajet entier. Annuler un trajet n'impose pas ce délai de 24 heures. Les réservations annulées restent dans l'historique avec leurs montants et dates ; la terminaison ne change pas leurs statuts.
