# Tests Postman - Annuler un trajet (COV-18)

Importer `postman/trajets-annulation.postman_collection.json`, ou utiliser le
meme dossier dans la collection simple. Aucun script ni variable : adapter les
ids dans URLs, headers et bodies de preparation et conserver des dates futures.

```http
PATCH http://localhost:8080/api/conducteurs/1/trajets/1/annuler
X-Conducteur-Id: 1
```

Aucun body ; succes 204 sans contenu. Seul le proprietaire peut annuler un trajet
PUBLIE ou COMPLET. Contrairement a l'annulation passager, ce ticket ne demande pas
de delai de 24h. Un trajet ANNULE ou TERMINE retourne 409 et reste final.

## Preparation et refus

Choisir deux conducteurs existants (exemples 1,2), deux passagers distincts
(exemples 1,2), et un vehicule du conducteur 1 de capacite au moins 4.
Le dossier 0 publie un nouveau trajet de 4 places, puis cree deux reservations :
Casablanca-Marrakech, 2 places, 160 DH ; Settat-Marrakech, 2 places, 100 DH.
Parcours : Rabat 0 DH -> Casablanca 40 DH -> Settat 70 DH -> Marrakech 120 DH.
Les ids generes sont a reporter manuellement ; les ids des points ne sont pas
leurs numeros d'ordre. Adapter la date du 1 decembre 2026 si necessaire.

Avant l'annulation valide, executer le dossier 1 : autre acteur/proprietaire 403,
conducteur ou trajet absent 404, headers/ids invalides 400, DELETE au lieu de
PATCH 405. Les refus ne doivent changer ni trajet ni reservations.

## Scenario principal

Executer le dossier 2 dans l'ordre :

| Action | Attendu |
|---|---|
| GET detail avant | COMPLET, remainingSeats=0 |
| GET reservations conducteur avant | Deux CONFIRMEE |
| PATCH annulation trajet | 204 sans contenu |
| GET detail apres | ANNULE, remainingSeats=4 |
| GET reservations conducteur apres | Memes deux lignes, ANNULEE |
| GET historique de chaque passager | Reservation conservee, montant/date/places/points inchanges |
| Recherche entre deux points | Le trajet ANNULE est absent |
| Nouvelle reservation sur ce trajet | 409 |
| Deuxieme annulation du trajet | 409, aucun changement |
| Annulation individuelle d'une de ses reservations | 409, trajet reste ANNULE |

remainingSeats est un calcul arithmetique : 4 signifie qu'aucune place n'est
consommee par une reservation CONFIRMEE. Le statut ANNULE interdit de reserver
et exclut le trajet de la recherche, meme avec un nombre de places positif.

Le dossier 3 demande ses propres trajets : PUBLIE sans reservations, PUBLIE avec
reservations et eventuellement une ancienne ANNULEE, et TERMINE. Les deux premiers
s'annulent avec 204 ; le dernier reste inchange avec 409. L'endpoint pour terminer
un trajet sera ajoute dans COV-19. Les reservations deja ANNULEE restent intactes.

## Verifier PostgreSQL

Remplacer 1 par le vrai id du trajet :

```sql
SELECT id, status, offered_seats FROM trajets WHERE id = 1;
SELECT id, passager_id, nombre_places, montant_total, date_reservation, statut
FROM reservations WHERE trajet_id = 1 ORDER BY id;
```

Attendu : trajet ANNULE ; deux reservations ANNULEE, montants 160.00 et 100.00,
meme nombre de places et date qu'avant. Aucune ligne n'est supprimee.
Une autre reservation sur un autre trajet doit rester inchangee.

Les tests automatiques couvrent aussi rollback commun et concurrence avec creation
ou annulation individuelle. Toutes les operations utilisent le verrou du trajet.

## Verification HTTP effectuee le 18 novembre 2026

Les 31 requetes de la collection ont reussi contre l'API reelle sur localhost:8081
et PostgreSQL. Les ids, dates et villes ont ete adaptes a des fixtures temporaires.
Le trajet COMPLET est devenu ANNULE ; ses deux reservations CONFIRMEE sont
devenues ANNULEE dans la meme operation. Les montants 160.00 et 100.00, dates,
places, points et ids sont restes identiques dans les historiques des passagers
et la consultation conducteur. Une ancienne ANNULEE sur un autre trajet de test
est restee intacte lors de l'annulation de ce second trajet.

La comparaison des lignes avant/apres confirme que seuls les statuts attendus
ont change. Les GET et les refus n'ont modifie aucune ligne. Un trajet protege
d'un autre conducteur et sa reservation sont restes PUBLIE/CONFIRMEE.
La recherche exclut le trajet annule, une nouvelle reservation retourne 409,
une seconde annulation retourne 409 et l'annulation passager ne le rouvre pas.
Les trajets PUBLIE vides et avec reservations sont annulables ; TERMINE est refuse.
Trois paires de lectures supplementaires detail/reservations ont confirme les succes.

Le statut TERMINE a ete prepare via SQL uniquement sur une fixture ; l'endpoint
de cloture n'est pas encore teste. Les autres annulations du scenario utilisent
les vrais endpoints. Toutes les fixtures ont ete nettoyees. Rapport local non
versionne : target/cov18-http-results.json.
