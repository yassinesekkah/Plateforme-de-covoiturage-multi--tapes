# Tests Postman - Creation de reservations (COV-15)

Importer `postman/reservations.postman_collection.json` pour ajouter uniquement
le dossier Reservations. Il existe aussi dans la collection simple complete.
Aucune variable ni script. Les identifiants ci-dessous sont des exemples :
les remplacer manuellement dans chaque URL, header et body concerne.

## Preparer les donnees

Le dossier `0 - Preparer les donnees` contient la liste des passagers, la liste
des vehicules du conducteur et une publication pre-remplie avec quatre points.
Adapter conducteur, vehicule et dates pour garder un depart futur.

1. Demarrer PostgreSQL et l'application avec le profil normal.
2. Choisir ou creer quatre passagers. Leurs ids d'exemple sont 1, 2, 3, 4.
3. Choisir un vehicule appartenant au conducteur et de capacite au moins 4.
4. Publier un nouveau trajet avec 4 places proposees et quatre points :

| Point | Ordre | Prix depuis depart | Id d'exemple |
|---|---|---|---|
| Rabat | 0 | 0.00 | 1 |
| Casablanca | 1 | 40.00 | 2 |
| Settat | 2 | 70.00 | 3 |
| Marrakech | 3 | 120.00 | 4 |

Utiliser des dates futures et strictement croissantes. Conserver l'id du trajet
(1 dans les exemples) et les ids des points retournes par POST publication.
Les ids des points sont distincts des numeros d'ordre et ne repartent pas a 1
pour chaque nouveau trajet. GET /api/trajets/{id} permet de les retrouver.

5. Pour les tests de mauvais trajet, publier un second trajet. Remplacer le
point 5 d'exemple par un point reel de ce second trajet.
6. 999999 est suppose absent. Adapter si cet id existe.

## Requete de creation

```http
POST http://localhost:8080/api/passagers/2/reservations
Content-Type: application/json
X-Passager-Id: 2
```

```json
{
  "trajetId": 1,
  "monteeId": 2,
  "descenteId": 4,
  "nombrePlaces": 2
}
```

Une creation valide retourne 201 avec id, passagerId, trajetId, monteeId,
descenteId, nombrePlaces, montantTotal, dateReservation et statut CONFIRMEE.
Le montant ici vaut 160.00. Le header simule l'acteur ; ce n'est pas une
authentification securisee. Ne pas envoyer montantTotal, dateReservation ou statut.

## Ordre d'execution

Executer les requetes du dossier `1 - Refus avant reservation` sur le trajet
PUBLIE et encore sans reservations. Toutes doivent etre refusees sans creer de ligne.
Leur nom et description donnent le resultat attendu et les preconditions.

Les tests ANNULE et TERMINE sont optionnels tant que les endpoints de changement
de statut ne sont pas implementes. Ils demandent deux autres trajets de test deja
dans ces statuts (ids 3 et 4 d'exemple). Ne pas les lancer sur des trajets inexistants :
cela donne 404. Ne pas modifier les trajets du scenario principal pour ces essais.

Executer ensuite le dossier scenario dans cet ordre :

| Etape | Action | HTTP | Montant | Places restantes attendues |
|---|---|---|---|---|
| 01 | Passager 1 : Settat-Marrakech, 1 place | 201 | 50.00 | 3 |
| 02 | Refaire la demande du passager 1 | 409 | - | 3 |
| 03 | Passager 2 : Casablanca-Marrakech, 2 places | 201 | 160.00 | 1 |
| 04 | Passager 3 demande 2 places | 409 | - | 1 |
| 05 | Passager 3 : Rabat-Casablanca, 1 place | 201 | 40.00 | 0 |
| 06 | Passager 4 demande 1 place sur trajet COMPLET | 409 | - | 0 |
| 07 | Lire le detail trajet | 200, statut COMPLET | - | 0 |

Les places comptent sur tout le trajet, meme pour un petit segment.
Ne pas relancer les succes sur le meme trajet : les reservations precedentes
existent encore et changent les resultats attendus. Publier un nouveau trajet
et remplacer ses ids pour rejouer le scenario.

## Verifier les refus et la base

Les reponses d'erreur contiennent status, title, detail, instance et timestamp.
Les violations DTO contiennent aussi errors. Status HTTP et champ status concordent.

Dans IntelliJ Database, executer cette requete en remplacant 1 par le vrai trajet :

```sql
SELECT id, passager_id, trajet_id, montee_id, descente_id,
       nombre_places, montant_total, date_reservation, statut
FROM reservations WHERE trajet_id = 1 ORDER BY id;
```

Apres le scenario, exactement trois reservations CONFIRMEE : 1 + 2 + 1 places.
Les refus ne doivent pas ajouter de lignes.

```sql
SELECT t.id, t.status, t.offered_seats,
       t.offered_seats - COALESCE(SUM(r.nombre_places)
           FILTER (WHERE r.statut = 'CONFIRMEE'), 0) AS places_restantes
FROM trajets t LEFT JOIN reservations r ON r.trajet_id = t.id
WHERE t.id = 1
GROUP BY t.id, t.status, t.offered_seats;
```

Attendu : COMPLET, offered_seats = 4, places_restantes = 0.
GET detail expose maintenant remainingSeats. Apres les creations 01, 03 et 05,
verifier respectivement 3, 1 puis 0 ; offeredSeats reste toujours 4.
L'annulation et l'historique sont dans des stories suivantes ; aucun endpoint
pour ces actions n'est ajoute dans cette collection.

## Verification HTTP effectuee le 18 novembre 2026

Cette execution a precede l'ajout de remainingSeats au detail trajet.

Les 31 requetes de la collection ont ete executees contre une instance reelle
de l'API sur localhost:8081 avec PostgreSQL local. Les ids et dates ont ete
adaptes a des donnees temporaires creees pour ce test, sans modifier la collection.
Les statuts ANNULE et TERMINE ont ete prepares uniquement sur deux trajets de test
via SQL, car leurs endpoints ne sont pas encore implementes.

Resultat : 31/31 statuts attendus. Les erreurs ProblemDetail, les champs des
reservations creees et la conservation des donnees apres chaque refus ont ete
verifies. PostgreSQL confirme les montants 50.00, 160.00 et 40.00, trois
reservations CONFIRMEE totalisant quatre places et 250.00 DH, zero place restante
et le trajet COMPLET. Les trajets, points, profils et reservations temporaires
ont ete supprimes apres verification. Cette execution ne teste pas les futurs
endpoints d'annulation ni d'historique.
