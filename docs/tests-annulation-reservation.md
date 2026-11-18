# Tests Postman - Annuler une reservation (COV-17)

Importer `postman/reservations-annulation.postman_collection.json`, ou utiliser
le dossier correspondant de la collection simple. Sans variables ni scripts :
adapter manuellement les ids dans les URLs, headers et bodies de preparation.

## Endpoint

```http
PATCH http://localhost:8080/api/passagers/1/reservations/1/annuler
X-Passager-Id: 1
```

Aucun body. Un succes retourne 204 sans contenu. Le header simule l'acteur,
comme pour les autres endpoints passager. Le serveur decide les changements
de statuts ; le client ne les envoie pas dans un JSON.

La limite est exactement 24h avant le depart du trajet entier. L'annulation
est refusee au-dela de cette limite, meme si le point de montee est plus tardif.
La reservation reste dans la table et l'historique avec statut ANNULEE.

## Preparation du scenario principal

Le dossier 0 fournit les requetes de preparation :

1. Choisir deux passagers distincts existants (ids 1 et 2 d'exemple).
2. Choisir conducteur et vehicule de capacite au moins 4.
3. Publier un trajet de quatre places avec depart dans plus de 24h.
   La collection propose le 1 decembre 2026 ; adapter toutes les dates si besoin.
4. Noter les ids du trajet et de chaque point : Rabat 0 DH, Casablanca 40 DH,
   Settat 70 DH, Marrakech 120 DH. Les ids des points ne sont pas leurs ordres.
5. Passager 1 reserve deux places Casablanca-Marrakech : 201, montant 160.00.
   Noter l'id de cette reservation et remplacer reservation 1 dans les PATCH.
6. Passager 2 reserve deux places Settat-Marrakech : 201, montant 100.00.
   Le trajet devient COMPLET, remainingSeats=0.

Les ids generes ne sont pas garantis 1, 2, 3. Pour l'historique d'un profil qui
avait deja des reservations, verifier la ligne par son id plutot que la taille
totale de sa liste.

## Refus avant annulation

Executer le dossier 1 avant de changer le statut de la reservation principale :

| Cas | Attendu |
|---|---|
| Header d'un autre acteur | 403 |
| Passager 2 tente d'annuler la reservation du passager 1 | 403 |
| Reservation absente ou passager absent | 404 |
| Header absent ou uniquement header conducteur | 400 |
| Header ou ids du chemin non numeriques | 400 |
| DELETE sur le chemin annuler | 405 |

Une erreur retourne ProblemDetail avec status, title, detail, instance et timestamp.
Les donnees et les places doivent rester identiques apres chaque refus.

## Scenario principal

Executer le dossier 2 dans cet ordre :

| Etape | Requete | Attendu |
|---|---|---|
| 01 | GET detail trajet | 200, COMPLET, remainingSeats=0 |
| 02 | GET historique passager 1 | Reservation cible CONFIRMEE, montant 160.00 |
| 03 | PATCH annulation | 204, sans contenu |
| 04 | GET detail trajet | 200, PUBLIE, remainingSeats=2, offeredSeats reste 4 |
| 05 | GET historique passager 1 | Reservation cible ANNULEE, meme id/montant/date/places/points |
| 06 | GET reservations conducteur | Les deux lignes existent : ANNULEE et CONFIRMEE |
| 07 | Refaire PATCH sur la meme reservation | 409 |
| 08 | GET detail trajet | Toujours PUBLIE, remainingSeats=2 |

Ne pas rejouer l'annulation valide sur une reservation deja annulee en attendant
un nouveau 204. Pour rejouer le scenario complet, publier un nouveau trajet et
creer deux nouvelles reservations, puis adapter tous les ids.

## Cas supplementaires avec leurs propres fixtures

Le dossier 3 demande d'autres reservations de test CONFIRMEE :

- Depart dans 2h ou 23h59 : 409, statuts et places inchanges.
- Trajet ANNULE ou TERMINE : 409, le trajet ne doit pas etre rouvert.
- Trajet deja PUBLIE avec depart dans plus de 24h : 204, reste PUBLIE et places
  recalculees en ignorant la reservation annulee.

Les cas ANNULE/TERMINE demandent des fixtures dans ces statuts ; leurs endpoints
ne sont pas encore disponibles. Ne pas changer les donnees du scenario principal
pour les preparer. Exactement 24h est verifie par les tests avec Clock.fixed :
un essai manuel qui calcule depart=maintenant+24h puis envoie la requete plus tard
a deja depasse la limite au moment du traitement.

## Verification PostgreSQL en lecture seule

Adapter les ids de la reservation et du trajet :

```sql
SELECT id, passager_id, trajet_id, nombre_places, montant_total,
       date_reservation, statut
FROM reservations WHERE id = 1;
```

Apres annulation : meme ligne, nombre_places=2, montant_total=160.00,
date_reservation inchangee, statut=ANNULEE.

```sql
SELECT t.id, t.status, t.offered_seats,
       t.offered_seats - COALESCE(SUM(r.nombre_places)
         FILTER (WHERE r.statut = 'CONFIRMEE'), 0) AS remaining_seats
FROM trajets t LEFT JOIN reservations r ON r.trajet_id = t.id
WHERE t.id = 1
GROUP BY t.id, t.status, t.offered_seats;
```

Avant : COMPLET, 4, 0. Apres : PUBLIE, 4, 2. Apres la deuxieme tentative refusee :
les memes valeurs. La deuxieme reservation reste CONFIRMEE.

## Verification HTTP effectuee le 18 novembre 2026

Les 28 requetes de la collection ont ete executees contre l'API reelle sur
localhost:8081 et PostgreSQL, avec des profils, trajets et reservations temporaires.
Les ids et dates ont ete adaptes aux fixtures. Resultat : 28/28 statuts attendus.

Avant annulation, le trajet principal etait COMPLET avec zero place disponible.
PATCH a retourne 204 sans contenu ; la premiere reservation est devenue ANNULEE
et le trajet PUBLIE avec deux places. Les autres champs de la reservation
(id, passager, trajet, points, nombrePlaces, montantTotal et dateReservation)
sont restes identiques. La seconde reservation est restee CONFIRMEE.
L'historique complet et les lectures conducteur ont ete compares au JSON construit
depuis SQL. Une deuxieme annulation a retourne 409 sans changer les deux places.

Les refus 400/403/404/405/409 et tous les GET n'ont modifie aucune ligne des
fixtures. Les departs dans 2h et 23h59 ont ete refuses ; ANNULE et TERMINE n'ont
pas ete rouverts. Une annulation sur un trajet deja PUBLIE a retourne 204, conserve
PUBLIE et libere la place attendue. Quatre GET supplementaires ont verifie detail
et historique apres les deux annulations valides.

Les dates proches et les statuts ANNULE/TERMINE ont ete prepares via SQL uniquement
sur les fixtures. Leurs futurs endpoints n'ont pas ete testes. Exactement 24h reste
couvert par les tests Java a horloge fixe. Toutes les fixtures ont ete nettoyees.
Le rapport local non versionne est target/cov17-http-results.json.
