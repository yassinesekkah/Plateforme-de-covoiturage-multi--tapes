# COV-19 - Terminer un trajet

Importer `postman/trajets-terminer.postman_collection.json`, ou reimporter la collection simple complete. Les deux contiennent le dossier **Trajets - Terminer COV-19**. Aucun script ni variable : remplacer directement les ids dans les URL, headers et bodies.

## Preparation

1. Demarrer PostgreSQL et l'application sur le port 8080.
2. Choisir deux conducteurs existants (exemples 1 et 2), un passager 1 et un vehicule 1 du conducteur 1 de capacite au moins quatre.
3. Publier le trajet de quatre places avec des dates futures. Les dates de 2030 dans la collection sont des exemples. Noter le vrai id du trajet et les deux ids des points ; remplacer les exemples 1, 1 et 2 dans les requetes concernees.
4. Reserver une place pour le passager : montant 40 DH, statut CONFIRMEE, trajet PUBLIE et remainingSeats 3. Noter les champs de la reservation pour comparer apres terminaison.
5. Executer les refus avant de terminer le trajet. Pour le cas autre proprietaire, le conducteur 2 doit exister. Les ids 999999999 doivent etre absents.

## Route

```http
PATCH http://localhost:8080/api/conducteurs/1/trajets/1/terminer
X-Conducteur-Id: 1
```

Body : **none**. Succes : **204 No Content**, reponse vide.

## Tester le temps sans attendre

La publication refuse un depart passe. Pour tester la terminaison immediatement, publier et reserver d'abord avec un depart futur. Puis utiliser la console SQL IntelliJ sur la base locale pour deplacer uniquement les dates du trajet de test et de ses points dans le passe. Ce SQL prepare les donnees ; il ne termine pas le trajet.

Remplacer tous les ids de trajet `1` ci-dessous par le vrai id du trajet de test. Utiliser la meme heure locale dans la JVM et la session PostgreSQL (`SELECT LOCALTIMESTAMP;` permet de comparer).

```sql
BEGIN;

UPDATE points_arret
SET passage_time = passage_time + (
    LOCALTIMESTAMP - INTERVAL '1 hour'
    - (SELECT departure_time FROM trajets WHERE id = 1)
)
WHERE trajet_id = 1;

UPDATE trajets
SET departure_time = (
    SELECT MIN(passage_time) FROM points_arret WHERE trajet_id = 1
)
WHERE id = 1;

COMMIT;
```

Le premier point et le depart deviennent environ une heure avant maintenant ; les intervalles entre les points restent identiques. Le statut et les reservations restent inchanges. Appliquer uniquement a des donnees de test.

## Cas a essayer

| Cas | Attendu |
| --- | --- |
| PUBLIE, proprietaire, depart passe | 204, statut TERMINE |
| COMPLET, proprietaire, depart passe | 204, statut TERMINE |
| PUBLIE sans reservation, depart passe | 204, statut TERMINE |
| Depart encore futur | 409, statut inchange |
| Exactement l'instant du depart | 409 ; limite couverte precisement par les tests unitaires a horloge fixe |
| Autre acteur dans le header | 403 |
| Conducteur existant mais pas proprietaire du trajet | 403 |
| Conducteur absent | 404 |
| Trajet absent avec conducteur existant | 404 |
| Header conducteur absent, invalide ou remplace par header passager | 400 |
| Id non numerique dans URL | 400 |
| DELETE au lieu de PATCH | 405 |
| Trajet deja TERMINE | 409 |
| Trajet ANNULE | 409 |

Apres le succes, GET `/api/trajets/1` doit retourner TERMINE avec les memes champs et remainingSeats 3. GET `/api/conducteurs/1/trajets/1/reservations`, avec `X-Conducteur-Id: 1`, et GET `/api/passagers/1/reservations`, avec `X-Passager-Id: 1`, doivent conserver la reservation CONFIRMEE, son montant 40 DH, sa date et ses identifiants. Terminer un trajet ne libere pas les places et n'annule aucune reservation.

Reessayer PATCH terminer ou PATCH annuler sur le trajet TERMINE : 409. Une nouvelle reservation sur ce trajet est aussi refusee avec 409.

Pour COMPLET : publier un autre trajet proposant une seule place, reserver cette place avant de deplacer les dates. Adapter les ids de la section 3 de la collection (exemples trajet 2, points 3 et 4). Verifier COMPLET, preparer ses dates avec le meme SQL, puis terminer : 204 ; le detail montre TERMINE et remainingSeats 0.

Pour ANNULE : publier un autre trajet puis l'annuler via PATCH `/api/conducteurs/1/trajets/3/annuler` avec le header conducteur. Essayer ensuite de le terminer : 409. Adapter l'id 3.

Pour le trajet sans reservation : publier un autre trajet, deplacer ses dates puis utiliser la derniere requete de la collection avec son vrai id (exemple 4).

## Verification SQL apres les requetes

```sql
SELECT id, conducteur_id, departure_time, status, offered_seats
FROM trajets WHERE id = 1;

SELECT id, trajet_id, passager_id, statut, nombre_places, montant_total, date_reservation
FROM reservations WHERE trajet_id = 1 ORDER BY id;
```

Seul le statut du trajet passe a TERMINE pendant la terminaison. Comparer les reservations avant/apres. Les refus ne doivent modifier aucune donnee.

## Tests automatises

`TrajetFinishServiceTest` teste les regles metier et les limites de temps avec une horloge fixe. `TrajetFinishControllerTest` teste le routage PATCH, le passage des trois identifiants au service, le header obligatoire et les reponses 204/400/403/404/405/409 avec MockMvc et un service simule.

Ces tests Controller ne remplacent pas les essais HTTP sur l'application et les comparaisons SQL ci-dessus.

## Verification HTTP realisee le 18 novembre 2026

Les **31 requetes de la collection** ont ete executees sur l'application reelle, port 8081, avec PostgreSQL. Les ids et dates exemples ont ete remplaces en memoire par ceux de donnees de test isolees. Resultat : **31/31 reussies**, script termine avec code 0. Le rapport local est `target/cov19-http-results.json` (non versionne).

- Trois terminaisons reelles : PUBLIE avec reservation, COMPLET avec reservation et PUBLIE sans reservation ; chaque PATCH retourne 204 avec corps vide.
- Les snapshots SQL avant/apres montrent que seul le statut du trajet change. Les reservations CONFIRMEE et les anciennes ANNULEE conservent tous leurs champs : ids, montants, places, dates, passagers et points. Les points et les autres champs des trajets sont inchanges.
- Les details et historiques HTTP correspondent aux donnees SQL. remainingSeats reste 3 pour le trajet PUBLIE de quatre places avec une place confirmee, et 0 pour le trajet COMPLET d'une place.
- Les cas 400/403/404/405/409 renvoient les erreurs attendues sans aucune modification en base. Avant le depart, terminer est refuse ; TERMINE et ANNULE restent finaux.
- Verifications supplementaires : annulation individuelle d'une reservation sur trajet TERMINE refusee avec 409 ; recherche excluant les trajets TERMINE et ANNULE ; trajet d'un autre conducteur et sa reservation conserves.
- Les trajets ont ete publies via HTTP puis leurs dates ont ete avancees via SQL uniquement pour eviter l'attente. Le statut ANNULE a ete prepare avec la vraie route d'annulation.
- Les donnees creees pour cette verification ont ete supprimees. Les empreintes de toutes les lignes des six tables, comparees avant creation et apres nettoyage, sont identiques : aucune donnee preexistante n'a change.

La limite exacte de l'heure de depart est verifiee par les tests unitaires a horloge fixe. La suite Java complete compte **330 tests reussis**, sans erreur ni echec.
