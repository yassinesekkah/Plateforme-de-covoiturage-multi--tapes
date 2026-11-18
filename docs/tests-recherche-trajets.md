# Tests Postman - Recherche de trajets (COV-14)

Importer `postman/trajets-recherche.postman_collection.json`. Le meme dossier
existe dans la collection simple. Sans variables ni scripts, toutes les dates
et tous les ids sont a adapter manuellement. Demarrer PostgreSQL puis l'API.

## Endpoint et reponse

```http
GET http://localhost:8080/api/trajets/search?departureCity=Casablanca&arrivalCity=Settat&date=2026-12-01&seats=1&page=0&size=10
```

Aucun body ni header d'acteur. Villes, date et seats sont obligatoires.
maxPrice est optionnel, page vaut 0 par defaut et size vaut 10 (maximum 100).
Le prix maximum est par place, sur le segment demande. La date filtre le depart
du trajet entier, meme si la montee a lieu apres minuit. Les villes sont comparees
sans distinction de casse, apres retrait des espaces au debut et a la fin.

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "hasNext": false
}
```

Les resultats sont dans content : trajetId, departureTime (trajet entier),
monteeId, departureCity, monteeTime, descenteId, arrivalCity, descenteTime,
segmentPrice et remainingSeats. hasNext indique une page suivante, sans totalPages.
Une recherche valide sans resultat retourne 200 avec content vide, pas 404.

## Preparation

Choisir une date future sans autres trajets correspondants si on veut retrouver
exactement les resultats ci-dessous. Par exemple, la collection utilise le
1 decembre 2026 : adapter toutes ses dates si necessaire.

Le dossier 0 contient les publications de trois trajets, tous PUBLIE avec quatre
places, sur Rabat -> Casablanca -> Settat -> Marrakech. Adapter conducteur et
vehicule dans les publications ; le vehicule doit avoir une capacite au moins 4.

| Trajet | Depart du trajet | Prix cumules des quatre points | Casablanca-Settat |
|---|---|---|---|
| B | 09:00 | 0, 50, 90, 150 | 40 DH |
| C | 13:00 | 0, 40, 65, 120 | 25 DH |
| A | 17:00 | 0, 40, 70, 120 | 30 DH |

Conserver les vrais ids retournes par chaque publication. Ils ne sont pas
necessairement 1, 2, 3. Les points d'arret ont leurs propres ids, differents de
stopOrder. Choisir deux passagers existants distincts pour le dossier 3.

## Recherches et pagination : avant de reserver

Executer le dossier 1 avant toute reservation sur ces trois trajets.

| Recherche | Attendu : HTTP 200 |
|---|---|
| Casablanca -> Settat | B, C, A, dans cet ordre ; prix 40, 25, 30 |
| Rabat -> Marrakech | B, C, A ; prix 150, 120, 120 |
| Casse et espaces differents | Memes resultats |
| maxPrice=30, seats=1 ou 2 | C puis A |
| maxPrice=29.99 | C uniquement |
| maxPrice=0 | content vide |
| seats=4 | B, C, A |
| seats=5 | content vide |
| Settat -> Casablanca | content vide |
| Casablanca -> Casablanca | content vide |
| Ville absente ou autre date sans trajet | content vide |
| page=0, size=2 | B, C ; hasNext=true |
| page=1, size=2 | A ; hasNext=false |
| page=2, size=2 | content vide ; hasNext=false |

Le tri porte sur le depart du trajet, puis l'id en cas d'egalite.

## Parametres invalides

Les requetes du dossier 2 doivent retourner 400 avec ProblemDetail : status,
title, detail, instance et timestamp ; errors pour les validations DTO.
Cas couverts : champ obligatoire absent, ville vide, date invalide ou impossible,
seats nul/negatif/non numerique, maxPrice negatif/trop precis/trop grand/non
numerique, page negative/non numerique, size hors 1..100 ou non numerique.
Les erreurs ne doivent creer ni trajet ni reservation.

## Places disponibles et passage a COMPLET

Executer le dossier 3 dans l'ordre, apres les recherches de reference :

1. Passager 1 reserve deux places Rabat-Casablanca sur A : POST 201.
   Remplacer trajetId et points par les vrais ids de A, et adapter URL/header passager.
2. Recherche seats=3 : B et C uniquement. La reservation compte sur tout A,
   meme si elle concerne un segment different de la recherche Casablanca-Settat.
3. Recherche seats=2 : B,C,A ; remainingSeats de A vaut 2.
4. Passager 2 reserve deux places Settat-Marrakech sur A : POST 201.
   Adapter ids de trajet, points, URL et header. A devient COMPLET.
5. Recherche seats=1 : B et C uniquement. A est exclu.
6. GET detail de A : status COMPLET, offeredSeats=4, remainingSeats=0.

Pour rejouer ce scenario, publier un nouveau trajet A et remplacer les ids :
les reservations precedentes modifient les resultats attendus.

## Fixtures optionnelles

Les cas ANNULEE et trajets ANNULE/TERMINE sont couverts par les tests PostgreSQL.
Pour les verifier manuellement avec le dossier 4, il faut preparer des donnees de
test dans ces statuts. Les endpoints d'annulation et de cloture ne sont pas encore
disponibles. Une reservation ANNULEE ne reduit pas les places ; un trajet ANNULE
ou TERMINE ne doit jamais apparaitre. Ne pas modifier les trois trajets du scenario
principal pour preparer les trajets de statut final.

## Verification SQL en lecture seule

Remplacer 1 par le vrai id de A :

```sql
SELECT t.id, t.status, t.offered_seats,
       t.offered_seats - COALESCE(SUM(r.nombre_places)
         FILTER (WHERE r.statut = 'CONFIRMEE'), 0) AS remaining_seats
FROM trajets t LEFT JOIN reservations r ON r.trajet_id = t.id
WHERE t.id = 1
GROUP BY t.id, t.status, t.offered_seats;
```

Apres la premiere reservation : PUBLIE et remaining_seats=2.
Apres la seconde : COMPLET et remaining_seats=0.

## Verification HTTP effectuee le 18 novembre 2026

Les 49 requetes de la collection ont ete executees contre l'API reelle sur
localhost:8081 et PostgreSQL local. Des profils et trajets temporaires ont ete
crees, avec une date future et des noms de villes uniques pour isoler les resultats.
Seuls les ids, dates et villes ont ete adaptes pour cette execution.

Resultat : 49/49 reponses attendues. Pour chaque recherche valide, le contenu
complet, les prix, les places restantes, l'ordre et hasNext ont ete compares a
une requete SQL directe utilisant un calcul independant de la requete JPQL.
Les GET et les erreurs 400 n'ont modifie aucune ligne des fixtures.

Les prix des segments sont 40, 25 et 30 DH. maxPrice=30 garde C puis A,
y compris pour deux places. La pagination de taille 2 retourne B,C avec
hasNext=true, puis A avec hasNext=false, puis une page vide.

Les deux reservations sur A ont retourne 201 avec montants 80.00 et 100.00 DH.
Apres la premiere, A n'apparait plus pour seats=3 mais reste pour seats=2.
Apres la seconde, A est COMPLET avec zero place restante et est exclu de la recherche.
B avec une place CONFIRMEE et deux ANNULEE reste disponible avec trois places.
Les trajets ANNULE et TERMINE prepares pour le test sont egalement exclus.

Les statuts ANNULEE, ANNULE et TERMINE ont ete prepares via SQL uniquement sur
les fixtures : leurs futurs endpoints n'ont pas ete testes. Toutes les fixtures
ont ete nettoyees apres verification. Le rapport local est conserve dans
target/search-http-results.json (non versionne).
