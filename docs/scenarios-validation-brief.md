# COV-20 - Les 11 scenarios de validation du brief

Source : PDF `brief-sc-plateforme-de-covoiturage-6afa0328d2bfb347495371.pdf`, section **9. Scenarios de validation**, avec l'exemple du trajet de Hamid.

Ce document presente les donnees, les resultats attendus et le compte rendu de l'execution HTTP des 11 scenarios COV-20, en section 7.

Pour la collection adaptee au jeu de demonstration COV-21, suivre [le guide demo](demo/README.md) : les ids sont reels et les preparations sont des GET. Le scenario 5 utilise une copie deja complete pour eviter de recopier un id de reservation nouvellement cree. Les sections ci-dessous conservent le parcours de reference valide pendant COV-20.

## 1. Donnees communes

Base des URL : `http://localhost:8080/api`.

Utiliser de nouvelles donnees de test. Les ids ci-dessous sont des exemples a remplacer directement dans les URL, headers et bodies. Aucun besoin de variables Postman.

| Profil | Id d'exemple | Utilisation |
| --- | --- | --- |
| Hamid, conducteur | 1 | Proprietaire du trajet principal et du vehicule 1 |
| Autre conducteur | 2 | Proprietaire du vehicule 2 |
| Salma, passager | 1 | Reserve une place |
| Karim, passager | 2 | Reserve deux places |
| Imane, passager | 3 | Essaie les demandes refusees |

Si ces profils sont crees, choisir des emails distincts, des permis distincts pour les conducteurs et des immatriculations distinctes pour les vehicules.

| Vehicule | Proprietaire | Capacite passagers |
| --- | --- | --- |
| 1, Dacia Logan | Hamid 1 | 4 |
| 2 | Autre conducteur 2 | 4 |

**Attention : le trajet principal propose 3 places, meme si le vehicule a une capacite de 4.** C'est la configuration du brief. Les anciens tests avec quatre places proposees restent utiles, mais ne donnent pas les memes places restantes.

## 2. Trajet principal T1

Choisir une date de depart environ trois jours apres le jour d'execution. Remplacer partout `2030-12-01` ci-dessous par cette date future, y compris dans la recherche. Les heures et prix correspondent a l'exemple du brief.

| Point | stopOrder | Heure | Prix depuis le depart | Id d'exemple |
| --- | --- | --- | --- | --- |
| Rabat | 0 | 17:00 | 0 DH | 1 |
| Casablanca | 1 | 18:15 | 40 DH | 2 |
| Settat | 2 | 19:15 | 70 DH | 3 |
| Marrakech | 3 | 21:30 | 120 DH | 4 |

Preparation : `POST /conducteurs/1/trajets`, header `X-Conducteur-Id: 1` et `Content-Type: application/json`.

```json
{
  "vehiculeId": 1,
  "offeredSeats": 3,
  "pointsArret": [
    {"city": "Rabat", "passageTime": "2030-12-01T17:00:00", "priceFromDeparture": 0},
    {"city": "Casablanca", "passageTime": "2030-12-01T18:15:00", "priceFromDeparture": 40},
    {"city": "Settat", "passageTime": "2030-12-01T19:15:00", "priceFromDeparture": 70},
    {"city": "Marrakech", "passageTime": "2030-12-01T21:30:00", "priceFromDeparture": 120}
  ]
}
```

Attendu pour cette preparation : **201**, statut PUBLIE, trois places restantes. Noter le vrai id du trajet et de chaque point de la reponse. L'id d'un point n'est pas son stopOrder ; les ids ne repartent pas a 1 pour chaque trajet.

Dans les exemples suivants, T1 porte l'id 1. Nommer R1 la reservation de Salma et R2 celle de Karim, puis noter leurs vrais ids.

## 3. Les 11 scenarios, dans l'ordre du brief

### 1 - Salma reserve une place Settat vers Marrakech

`POST /passagers/1/reservations`, header `X-Passager-Id: 1`.

```json
{"trajetId": 1, "monteeId": 3, "descenteId": 4, "nombrePlaces": 1}
```

Attendu : **201**, reservation R1 CONFIRMEE, `montantTotal = 50.00` car `(120 - 70) x 1 = 50`. T1 reste PUBLIE ; `remainingSeats = 2`.

### 2 - Karim recherche Settat vers Casablanca

`GET /trajets/search?departureCity=Settat&arrivalCity=Casablanca&date=2030-12-01&seats=1&page=0&size=10`.

Aucun body ni header d'acteur. Utiliser la date reelle de T1.

Attendu : **200**, T1 absent de `content`, car Settat vient apres Casablanca. La liste peut contenir d'autres trajets qui font ce parcours dans le bon sens ; elle est vide si aucun autre trajet ne correspond. Aucun changement en base ; T1 reste PUBLIE avec deux places restantes.

### 3 - Karim reserve deux places Casablanca vers Marrakech

`POST /passagers/2/reservations`, header `X-Passager-Id: 2`.

```json
{"trajetId": 1, "monteeId": 2, "descenteId": 4, "nombrePlaces": 2}
```

Attendu : **201**, reservation R2 CONFIRMEE, `montantTotal = 160.00` car `(120 - 40) x 2 = 160`. Avec R1, les trois places sont occupees : `remainingSeats = 0`, T1 devient COMPLET.

### 4 - Imane essaie de reserver sur le trajet complet

`POST /passagers/3/reservations`, header `X-Passager-Id: 3`.

```json
{"trajetId": 1, "monteeId": 1, "descenteId": 4, "nombrePlaces": 1}
```

Attendu : **409**, aucune reservation creee pour Imane. R1 et R2 restent CONFIRMEE, T1 reste COMPLET avec zero place restante.

### 5 - Karim annule sa reservation trois jours avant le depart

`PATCH /passagers/2/reservations/2/annuler`, header `X-Passager-Id: 2`, sans body. Remplacer le deuxieme `2` par le vrai id de R2.

T1 doit toujours partir environ trois jours plus tard ; le delai est donc superieur a 24 heures.

Attendu : **204**, corps vide, R2 devient ANNULEE. R1 reste CONFIRMEE avec son montant de 50 DH. Deux places se liberent : `remainingSeats = 2`, T1 redevient PUBLIE. La ligne de R2 reste dans l'historique avec son montant de 160 DH.

### 6 - Imane essaie de monter a Marrakech et descendre a Settat

`POST /passagers/3/reservations`, header `X-Passager-Id: 3`.

```json
{"trajetId": 1, "monteeId": 4, "descenteId": 3, "nombrePlaces": 1}
```

Attendu : **400**, montee apres descente. Aucune nouvelle reservation ; T1 reste PUBLIE avec deux places restantes.

Executer apres le scenario 5 : si T1 est encore COMPLET, le service refuse d'abord son statut avec 409 et ce test ne verifie pas le mauvais sens.

### 7 - Hamid propose cinq places avec un vehicule de capacite quatre

`POST /conducteurs/1/trajets`, header `X-Conducteur-Id: 1`. Reprendre le body de publication avec son vehicule 1 et changer uniquement `offeredSeats` en `5`. Garder les dates futures et le parcours valide.

Attendu : **400**, cinq places depassent la capacite de quatre. Aucun trajet ni point ajoute. T1 et ses reservations restent inchanges.

### 8 - Hamid essaie de publier avec le vehicule d'un autre conducteur

`POST /conducteurs/1/trajets`, header `X-Conducteur-Id: 1`. Reprendre le body valide avec trois places mais mettre `vehiculeId: 2`, vehicule appartenant au conducteur 2.

Attendu : **403**, aucun trajet ni point ajoute. Le vehicule doit exister et appartenir a l'autre conducteur ; un vehicule absent donnerait 404 et ne verifierait pas cette regle.

### 9 - Hamid essaie de terminer avant l'heure de depart

`PATCH /conducteurs/1/trajets/1/terminer`, header `X-Conducteur-Id: 1`, sans body.

Attendu : **409**, T1 reste PUBLIE avec deux places restantes. Les reservations restent inchangees.

Le brief donne l'exemple du jour du depart avant 17:00. Pour notre parcours HTTP, garder T1 avec son depart futur suffit a verifier le refus avant depart sans attendre le jour J. L'exemple exact 16:00 pour un depart a 17:00 est reproductible avec une horloge fixe dans les tests unitaires. La preparation de ce scenario ne doit pas avancer les dates de T1, sinon on fausserait les autres controles.

### 10 - Salma essaie d'annuler deux heures avant le depart

Utiliser un **autre trajet T2**, pour garder les dates et l'historique de T1. Publier T2 avec un depart dans environ deux heures, trois places et le meme parcours/prix ; adapter les heures suivantes pour qu'elles restent strictement croissantes. Salma y reserve une place Settat vers Marrakech : preparation **201**, montant 50 DH, deux places restantes.

Noter les vrais ids de T2, de ses points et de cette nouvelle reservation R3.

`PATCH /passagers/1/reservations/3/annuler`, header `X-Passager-Id: 1`, sans body. Remplacer `3` par le vrai id de R3.

Attendu : **409**, le delai de 24 heures est depasse. R3 reste CONFIRMEE, T2 reste PUBLIE avec deux places restantes. Ni suppression ni liberation de place.

Le delai se calcule depuis le depart du trajet entier, donc Rabat, meme si Salma monte a Settat. A exactement 24 heures avant le depart, l'annulation est autorisee ; cette limite est verifiable avec une horloge fixe.

### 11 - Hamid annule un trajet avec deux reservations

Utiliser un **troisieme trajet T3** de trois places, publie avec une date future et le meme parcours. Preparer deux reservations distinctes : Salma, une place Settat-Marrakech (50 DH), et Karim, deux places Casablanca-Marrakech (160 DH). Les creations retournent 201 ; T3 devient COMPLET avec zero place restante.

`PATCH /conducteurs/1/trajets/3/annuler`, header `X-Conducteur-Id: 1`, sans body. Remplacer `3` par le vrai id de T3.

Attendu : **204**, corps vide. T3 devient ANNULE et ses deux reservations passent de CONFIRMEE a ANNULEE dans la meme transaction. Les lignes, ids, montants, dates et points sont conserves.

Le detail calcule alors `remainingSeats = 3`, car il n'y a plus de reservation CONFIRMEE. Le statut ANNULE interdit quand meme toute nouvelle reservation et exclut T3 de la recherche. T1 et T2 restent inchanges.

Un rollback en cas d'erreur intermediaire et la concurrence sont a verifier avec les tests d'integration existants ; le seul HTTP 204 ne prouve pas ces proprietes.

## 4. Resume des resultats attendus

| Numero du brief | Action | HTTP | Montant | Etat attendu |
| --- | --- | --- | --- | --- |
| 1 | Salma reserve une place | 201 | 50 DH | T1 PUBLIE, deux places restantes |
| 2 | Recherche Settat-Casablanca | 200 | - | T1 absent des resultats |
| 3 | Karim reserve deux places | 201 | 160 DH | T1 COMPLET, zero place restante |
| 4 | Imane reserve sur COMPLET | 409 | - | Aucun changement |
| 5 | Karim annule trois jours avant | 204 | 160 DH conserve en historique | T1 PUBLIE, deux places restantes |
| 6 | Montee apres descente | 400 | - | Aucune reservation ajoutee |
| 7 | Cinq places, capacite quatre | 400 | - | Aucun trajet ajoute |
| 8 | Vehicule d'un autre conducteur | 403 | - | Aucun trajet ajoute |
| 9 | Terminer avant depart | 409 | - | T1 inchange |
| 10 | Annuler deux heures avant | 409 | 50 DH inchange | R3 CONFIRMEE sur T2 |
| 11 | Annuler T3 et ses deux reservations | 204 | 50 et 160 DH conserves | T3 ANNULE, deux reservations ANNULEE |

## 5. Comment verifier pendant l'execution suivante

Apres chaque action, lire `GET /trajets/1` avec le vrai id du trajet concerne : comparer `status`, `offeredSeats` et `remainingSeats`.

Lire aussi `GET /passagers/1/reservations` avec `X-Passager-Id: 1`, et la meme route pour Karim avec son header. Le conducteur peut lire `GET /conducteurs/1/trajets/1/reservations` avec `X-Conducteur-Id: 1`.

Pour une reservation reussie, comparer les points, le nombre de places, le montant et le statut avec la base. Pour chaque refus, verifier qu'aucune donnee n'a change. Les erreurs doivent contenir le bon `status`, un `detail` clair, `instance` et `timestamp`. Les succes 204 ont un corps vide.

Exemple SQL de controle, a adapter avec le vrai id du trajet :

```sql
SELECT t.id, t.status, t.offered_seats,
       t.offered_seats - COALESCE(SUM(r.nombre_places)
           FILTER (WHERE r.statut = 'CONFIRMEE'), 0) AS remaining_seats
FROM trajets t
LEFT JOIN reservations r ON r.trajet_id = t.id
WHERE t.id = 1
GROUP BY t.id, t.status, t.offered_seats;

SELECT id, passager_id, trajet_id, montee_id, descente_id,
       nombre_places, montant_total, date_reservation, statut
FROM reservations
WHERE trajet_id = 1
ORDER BY id;
```

## 6. Mode emploi de la collection Postman

Fichier a importer : [scenarios-brief.postman_collection.json](../postman/scenarios-brief.postman_collection.json). La collection simple complete contient egalement le meme dossier **Validation brief - 11 scenarios COV-20**.

Le dossier contient **35 requetes** : preparation, les onze scenarios et lectures pour controler les resultats. Les scenarios gardent exactement leur ordre 01 a 11. Le dossier 00 prepare les profils, vehicules et T1 ; les dossiers 10 et 11 preparent leurs propres trajets avant leur action principale.

### Importer

1. Dans Postman, choisir **Import**, selectionner le fichier JSON puis confirmer l'import.
2. Ouvrir la collection **Covoiturage - Les 11 scenarios du brief** et son dossier de validation. Pour le garder dans une collection existante, deplacer ou copier ce dossier vers cette collection. Autre possibilite : importer la collection simple complete mise a jour.
3. Demarrer PostgreSQL et l'application sur le port 8080. Les URL sont deja ecrites en entier.
4. Executer les requetes manuellement avec **Send**, de haut en bas. Lire la description de chaque requete : elle indique les preconditions, les valeurs a adapter et le resultat attendu. Le code attendu est aussi dans son nom.

### Adapter les donnees pendant la preparation

Les creations ne garantissent jamais les ids 1, 2 ou 3 : la base attribue ses propres ids. Noter les vrais ids dans un petit tableau personnel, puis les ecrire directement dans les requetes suivantes.

| Element | Id exemple dans la collection | Ou le remplacer |
| --- | --- | --- |
| Hamid / autre conducteur | 1 / 2 | URL conducteur, X-Conducteur-Id |
| Salma / Karim / Imane | 1 / 2 / 3 | URL passager, X-Passager-Id |
| Vehicule Hamid / autre conducteur | 1 / 2 | vehiculeId du body publication |
| T1 | 1 | trajetId des reservations, URL detail/terminer |
| Points T1 | 1 / 2 / 3 / 4 | monteeId et descenteId selon la ville |
| R2, reservation Karim sur T1 | 2 | URL de l'annulation du scenario 05 |
| T2 et ses points | 2 et 5 / 6 / 7 / 8 | Body reservation Salma, URL detail T2 |
| R3, reservation Salma sur T2 | 3 | URL de l'annulation refusee du scenario 10 |
| T3 et ses points | 3 et 9 / 10 / 11 / 12 | Bodies reservations, URL detail/annuler/reservations |

Les ids des profils et ceux des trajets sont independants, meme si l'exemple utilise parfois le meme nombre. Ne pas remplacer tous les chiffres `1` globalement : `nombrePlaces: 1` et les prix ne sont pas des ids.

Pour creer des profils de test, modifier les emails, numeros de permis et immatriculations si les valeurs proposees existent deja. Une creation refusee pour doublon n'est pas un scenario du brief : corriger la preparation avant de continuer. On peut reutiliser des profils et vehicules existants si leurs proprietaires et capacites conviennent ; dans ce cas, sauter uniquement leurs creations.

### Adapter les dates

Pour T1, T3 et les publications refusees des scenarios 07/08 : remplacer les dates exemples `2030-12-01` par une date environ trois jours plus tard. Conserver les heures 17:00, 18:15, 19:15 et 21:30. Mettre la meme date dans la recherche du scenario 02.

Pour T2 du scenario 10 : remplacer les quatre dates/heures avant publication. Par exemple, si maintenant il est 10:00, choisir Rabat 12:00, Casablanca 13:15, Settat 14:15 et Marrakech 16:30 le meme jour. Si le parcours passe minuit, utiliser la date du lendemain pour les points concernes. Garder le depart dans le futur et a moins de 24 heures de maintenant.

**Si les dates exemples de 2030 restent dans T2, l'annulation peut reussir avec 204 au lieu du 409 attendu.** Corriger sa preparation en publiant un nouveau T2 avec les bonnes dates ; ne pas continuer sur une reservation deja annulee.

### Executer sans fausser les resultats

Faire une seule fois les creations et les actions qui doivent reussir. Une deuxieme reservation identique ou une deuxieme annulation change le resultat attendu. Pour rejouer tout le parcours, publier de nouveaux T1/T2/T3 et reporter leurs nouveaux ids ; les profils peuvent etre reutilises.

Les GET de verification peuvent etre repetes. Les requetes PATCH ne demandent aucun body. Pour les POST, les bodies JSON et les headers Content-Type sont deja prepares.

Ne pas lancer le dossier entier dans Runner sans adaptation entre les creations : cette collection n'a ni variable ni script pour recopier automatiquement les ids retournes.

Les JSON et les donnees des requetes ont ete verifies localement. L'execution HTTP du parcours complet COV-20 et la comparaison SQL sont consignees ci-dessous.

## 7. Resultats HTTP et SQL du 18 novembre 2026

Les requetes de `postman/scenarios-brief.postman_collection.json` ont ete lues et executees par un script Node contre l'application reelle sur `http://localhost:8081`, avec PostgreSQL local. Les ids, valeurs uniques et dates ont ete adaptes en memoire ; les villes ont recu un suffixe de test pour isoler la recherche. La collection reste simple, sans variables ni scripts Postman.

**Resultat : 11 scenarios sur 11 reussis et 35 requetes sur 35 conformes**, preparations et GET de controle compris. Le script termine avec le code 0. Les lectures de detail et de reservations effectuees en plus apres chaque scenario correspondent aux donnees SQL. Rapport local non versionne : `target/cov20-http-results.json`.

| Scenario | HTTP observe pour l'action principale | Verification SQL observee |
| --- | --- | --- |
| 1 | 201 | R1 CONFIRMEE, une place, montant 50.00 ; T1 PUBLIE, deux places restantes |
| 2 | 200 | T1 absent de la recherche dans le mauvais sens ; aucune modification |
| 3 | 201 | R2 CONFIRMEE, deux places, montant 160.00 ; T1 COMPLET, zero place restante |
| 4 | 409 | Aucune reservation Imane ; T1 reste COMPLET, zero place restante |
| 5 | 204, corps vide | R2 ANNULEE avec montant 160.00 conserve ; R1 CONFIRMEE, T1 PUBLIE et deux places restantes |
| 6 | 400 | Message montee avant descente ; aucune reservation ajoutee |
| 7 | 400 | Capacite quatre depassee ; aucun trajet ni point ajoute |
| 8 | 403 | Vehicule d'un autre conducteur ; aucun trajet ni point ajoute |
| 9 | 409 | Depart de T1 encore futur ; statut, dates et reservations inchanges |
| 10 | 409 | Depart de T2 a environ deux heures ; R3 reste CONFIRMEE, montant 50.00, deux places restantes |
| 11 | 204, corps vide | T3 ANNULE et ses deux reservations ANNULEE ; montants 50.00/160.00 et autres champs conserves ; trois places restantes calculees |

Les snapshots SQL des trajets, points et reservations ont ete compares avant/apres chaque refus et chaque GET : aucune modification. Pour les annulations, seuls les statuts attendus changent ; dates, ids, prix, montants, nombre de places et points sont conserves. Les erreurs contiennent le code attendu, le bon chemin, un message correspondant a la regle testee et un timestamp.

Etat final avant nettoyage : trois trajets, cinq reservations ; aucune reservation Imane. T1 contient R1 CONFIRMEE et R2 ANNULEE ; T2 contient R3 CONFIRMEE ; T3 contient R4 et R5 ANNULEE. T1 et T2 ne sont pas modifies par l'annulation de T3.

T1/T3 ont ete publies environ trois jours dans le futur, T2 environ deux heures dans le futur, sans modifier leurs dates via SQL. Pour le scenario 9, le HTTP verifie le refus avant depart ; il ne reproduit pas une horloge reelle a 16:00 le jour d'un depart a 17:00. Les limites exactes restent du ressort des tests unitaires a horloge fixe. Cette execution HTTP ne rejoue pas les tests de rollback ni de concurrence.

Les donnees de test ont ete nettoyees. Les empreintes des lignes des six tables, comparees avant creation et apres nettoyage, sont identiques : aucune ligne preexistante n'a change. Les sequences d'identifiants peuvent avoir avance normalement apres ces creations et suppressions.

## 8. Historique de verification des tests metier (COV-66)

Pendant COV-20, les tests des fonctionnalites precedentes ont ete relus pour verifier les regles des onze scenarios. Le tableau suivant conserve cette verification historique, avant la simplification des tests dans COV-21. La suite actuelle contient uniquement `TrajetServiceTest` (9 tests) et `ReservationServiceTest` (11 tests), decrits dans le README principal. Les anciens fichiers cites dans le tableau ont ete retires.

| Scenarios | Tests existants qui couvrent les regles |
| --- | --- |
| 1 et 3 : montants 50/160 DH, calcul des places et passage a COMPLET | `ReservationServiceImplTest` : `calculates50DhAndMarksTripFullForLastSeat`, `calculates160DhForTwoSeatsAndKeepsTripPublished`, `calculatesRemainingSeatsUsingConfirmedReservationsOnly` |
| 2 : recherche dans le mauvais sens | `TrajetSearchRepositoryTest.rejectsReverseSameCityAndUnknownCities` ; test d'integration de la vraie requete JPQL |
| 4 : trajet complet | `ReservationServiceImplTest.rejectsUnavailableTrip` et `rejectsInsufficientSeatsEvenForSmallSegment` |
| 5 : annulation trois jours avant, reouverture et historique | `ReservationCancellationServiceTest.cancelsThreeDaysBeforeDepartureReopensFullTripAndPreservesHistoryFields` |
| 6 : montee apres descente | `ReservationServiceImplTest.rejectsEqualOrReversedStopOrder` |
| 7 : cinq places, capacite quatre | `TrajetServiceImplTest.rejectsInvalidSeats`, cas parametre 5 |
| 8 : vehicule d'un autre conducteur | `TrajetServiceImplTest.rejectsAnotherConducteursVehicle` |
| 9 : terminaison avant depart | `TrajetFinishServiceTest.refusesBeforeDeparture`, `refusesAtDepartureOrJustBefore` |
| 10 : annulation tardive, limite de 24 h | `ReservationCancellationServiceTest.refusesLateOrPastDeparture`, `acceptsExactly24HoursBeforeDeparture`, `refusesOneNanosecondAfterDeadline` |
| 11 : annulation groupee | `TrajetCancellationServiceTest.cancelsTripAndItsConfirmedReservationsWithoutPassengerDeadline` ; `TrajetCancellationIntegrationTest` verifie aussi conservation, rollback et concurrence sur PostgreSQL |

Execution complete avant simplification : **330 tests, aucun echec ni erreur**, BUILD SUCCESS pendant COV-19. Les 35 requetes HTTP COV-20 ont ete executees ensuite avec comparaison SQL, comme indique en section 7. Apres simplification dans COV-21 : **20 tests unitaires, aucun echec ni erreur**, BUILD SUCCESS. La collection finale adaptee aux ids de demonstration a ete verifiee separement : 59 requetes reussies sur une copie de la base ; voir le guide demo.
