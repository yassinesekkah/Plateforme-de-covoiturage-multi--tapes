# Plateforme de covoiturage multi-etapes

API REST permettant aux conducteurs de publier des trajets avec plusieurs
points d'arret et aux passagers de reserver des places sur une partie
ou la totalite d'un trajet.

## Technologies

- Java 17
- Spring Boot et Maven
- Spring Data JPA / Hibernate
- PostgreSQL 17 avec Docker Compose
- Bean Validation
- MapStruct et Lombok
- JUnit 5, Mockito et AssertJ
- Postman

## Etat actuel

- Diagramme de classes disponible dans `docs/conception/`.
- PostgreSQL configure avec Docker Compose.
- Connexion Spring Boot / PostgreSQL verifiee.
- Configuration MapStruct et Lombok ajoutee.
- Packages de l'architecture prepares.
- Gestion centralisee des erreurs avec ProblemDetail.
- Endpoints techniques de verification disponibles avec le profil `test-errors`.
- Creation d'un conducteur avec validation des donnees et controle d'unicite.
- Consultation, liste, recherche par nom ou email et modification du profil conducteur.
- Creation, consultation, liste, recherche par email et modification des passagers.
- Ajout, consultation, liste par conducteur, modification et suppression des vehicules.
- Publication d'un trajet avec points d'arret ordonnes et validations du parcours (COV-12).
- Tests unitaires du service conducteur et du traitement des contraintes SQL.

Les fonctionnalites metier seront ajoutees progressivement.

## Prerequis

- JDK 17
- Docker Desktop demarre
- Git
- Postman pour les tests HTTP

Les commandes ci-dessous sont prevues pour PowerShell sur Windows.
Maven est fourni par le Maven Wrapper du projet.
Les ports 5433 (PostgreSQL) et 8080 (API) doivent etre disponibles.

## Recuperer le projet

```powershell
git clone https://github.com/yassinesekkah/Plateforme-de-covoiturage-multi--tapes.git
cd Plateforme-de-covoiturage-multi--tapes
```

Pour recuperer les fonctionnalites integrees :

```powershell
git switch develop
```

Les nouvelles fonctionnalites sont developpees dans des branches `feature/COV-...`,
puis fusionnees dans `develop` apres verification.

## Demarrer PostgreSQL

Depuis la racine du projet :

```powershell
docker compose up -d
docker compose ps
```

Configuration locale :

| Parametre | Valeur |
|---|---|
| Host | localhost |
| Port depuis le PC | 5433 |
| Base | covoiturage |
| Utilisateur | covoiturage |
| Mot de passe local | covoiturage |

Ces identifiants sont reserves au developpement local.

Pour verifier la base :

```powershell
docker compose exec postgres psql -U covoiturage -d covoiturage -c "SELECT current_database(), current_user;"
```

Les donnees sont conservees dans un volume Docker.

## Demarrer l'API

PostgreSQL doit etre demarre avant l'API.

```powershell
.\mvnw.cmd spring-boot:run
```

Adresse de base : `http://localhost:8080`.
Cette adresse ne correspond pas a une page d'accueil : utiliser les endpoints disponibles.

Utiliser `Ctrl + C` pour arreter l'application.

Hibernate cree ou met a jour les tables a partir des entites avec
`spring.jpa.hibernate.ddl-auto=update`, pour le developpement local.
Sans entites metier, aucune table metier n'est creee.

## Lancer les tests Java

Avec PostgreSQL demarre :

```powershell
.\mvnw.cmd clean test
```

Le test de chargement du contexte utilise la configuration PostgreSQL locale.
Les tests HTTP ci-dessous sont actuellement executes manuellement avec Postman.

Pour lancer uniquement les tests unitaires sans PostgreSQL :

```powershell
.\mvnw.cmd "-Dtest=ConducteurServiceImplTest,PassagerServiceImplTest,GlobalExceptionHandlerTest" test
```

Les 13 tests de ConducteurServiceImpl couvrent la creation et les doublons,
la liste avec ou sans resultats, la recherche et le mot-cle vide,
ainsi que la modification, le proprietaire, le profil absent et les doublons.
Les 2 tests de GlobalExceptionHandler simulent les erreurs SQL :
23505 retourne 409, une autre erreur de contrainte retourne 500.
Ces simulations ne constituent pas un test de concurrence reelle en base.
Le log ERROR affiche par le test du cas 500 est volontaire.

Les 14 tests de PassagerServiceImpl couvrent creation, detail existant ou absent,
liste et recherche avec ou sans resultats, nettoyage et refus du terme de recherche,
modification du profil et refus de modification pour absence ou mauvais proprietaire,
ainsi que les emails deja utilises en creation et modification.
Ils utilisent des mocks : les validations HTTP, la recherche SQL et la persistance
reelle se verifient avec Postman et PostgreSQL.

## Creer un conducteur

Cet endpoint fonctionne sans le profil `test-errors` :

```text
POST http://localhost:8080/api/conducteurs
Content-Type: application/json
```

Corps de la requete :

```json
{
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0612345678",
  "licenseNumber": "AB123456"
}
```

Tous les champs sont obligatoires, ne doivent pas etre vides et sont limites
a 255 caracteres. L'email doit avoir un format valide.
L'email et le numero de permis doivent etre uniques parmi les conducteurs.
Le client ne fournit pas d'identifiant : PostgreSQL le genere.

Une creation valide retourne 201 Created :

```json
{
  "id": 1,
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0612345678",
  "licenseNumber": "AB123456"
}
```

L'identifiant ci-dessus est un exemple. La ligne est enregistree dans la table
`conducteurs`, avec la colonne `license_number` pour le numero de permis.
La creation est journalisee au niveau INFO.

Dans le dossier Conducteurs de Postman, conserver les cas suivants :

| Cas | Status attendu | Resultat |
|---|---|---|
| Donnees valides et uniques | 201 | Conducteur cree avec son identifiant |
| Email existant, permis nouveau | 409 | Cet email est deja utilise par un conducteur |
| Permis existant, email nouveau | 409 | Ce numero de permis est deja utilise |
| Nom vide | 400 | Liste errors contenant Le nom est obligatoire |
| Email invalide | 400 | Liste errors contenant L'email est invalide |
| Corps vide `{}` | 400 | Erreurs des champs obligatoires |

Les requetes refusees ne doivent pas ajouter de conducteur.
Les controles du service fournissent des messages precis sur les doublons.
Les contraintes uniques PostgreSQL restent la protection finale : si un doublon
atteint la base (SQLState 23505), le gestionnaire retourne 409 avec le message
Une valeur devant etre unique est deja utilisee.

La collection reste sauvegardee dans Postman. Son export dans le depot est prevu
apres l'ajout des autres fonctionnalites.

## Consulter et rechercher les conducteurs

Ces endpoints fonctionnent sans le profil `test-errors` :

| Methode | Chemin | Resultat |
|---|---|---|
| GET | /api/conducteurs | 200 et liste des conducteurs |
| GET | /api/conducteurs/{id} | 200 et profil, ou 404 si absent |
| GET | /api/conducteurs/search?keyword=Alami | 200 et liste des correspondances |

La recherche porte sur `lastName` ou `email`. Elle accepte une partie du texte,
ignore la casse et retire les espaces au debut et a la fin du mot-cle.
Un mot-cle absent, vide ou compose uniquement d'espaces retourne 400.
Une liste ou une recherche sans resultat retourne 200 avec `[]`.
Un identifiant non numerique retourne 400. Aucun tri particulier n'est garanti.

## Modifier un profil conducteur

```text
PUT http://localhost:8080/api/conducteurs/1
Content-Type: application/json
X-Conducteur-Id: 1
```

Remplacer `1` par l'identifiant du conducteur. Le header `X-Conducteur-Id`
represente l'acteur pour simuler le controle de propriete dans ce projet.
Il est fourni par le client et ne constitue pas une authentification securisee.

Le PUT remplace les cinq informations du profil : tous les champs sont requis,
avec les memes validations que pour la creation.

```json
{
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0699999999",
  "licenseNumber": "AB123456"
}
```

Une modification valide retourne 200 avec le profil actualise et le meme `id`.
Elle ne cree pas une nouvelle ligne. Le conducteur peut conserver son email
et son permis ; les controles de doublons excluent son propre identifiant.
La modification est transactionnelle et journalisee au niveau INFO.

| Cas | Status attendu |
|---|---|
| Modification de son profil avec des valeurs valides | 200 |
| Profil inexistant | 404 |
| Acteur different du proprietaire du profil existant | 403 |
| Email ou permis deja utilise par un autre conducteur | 409 |
| Champs invalides ou obligatoires absents | 400 |
| Header X-Conducteur-Id absent ou non numerique | 400 |

Conserver ces cas dans le dossier Conducteurs de Postman. Apres un PUT valide,
relire le profil avec GET et verifier la base. Apres un refus, les donnees doivent
rester identiques. Pour tester les doublons, utiliser un second conducteur avec
des valeurs distinctes. Les controles sont aussi proteges par les contraintes
uniques PostgreSQL et le traitement du SQLState 23505.

## Gerer les passagers

Ces endpoints fonctionnent sans le profil `test-errors` :

| Methode | Chemin | Resultat |
|---|---|---|
| POST | /api/passagers | 201 et profil avec identifiant genere |
| GET | /api/passagers/{id} | 200 et profil, ou 404 si absent |
| GET | /api/passagers | 200 et liste, ou `[]` |
| GET | /api/passagers/search?email=salma | 200 et liste des correspondances |
| PUT | /api/passagers/{id} | 200 et profil actualise |

Corps JSON pour POST et PUT :

```json
{
  "lastName": "Bennani",
  "firstName": "Salma",
  "email": "salma@example.com",
  "phone": "0612345678"
}
```

Les quatre champs sont obligatoires, non blancs et limites a 255 caracteres.
L'email doit avoir un format valide. Le client ne fournit pas d'identifiant.
La table `passagers` ne contient pas de permis. L'email est unique parmi les passagers,
comme choix de conception complementaire au brief. Creation avec email existant :
409 et Cet email est deja utilise par un passager.
L'unicite compare les valeurs exactes, comme pour les conducteurs ; la recherche
reste insensible a la casse.

La recherche accepte une partie de l'email, ignore la casse et retire les espaces
en debut et fin. Une recherche sans resultat retourne 200 avec `[]` ; un parametre
absent ou blanc retourne 400. Aucun tri particulier n'est garanti.

PUT demande les quatre champs et conserve l'identifiant. Le header obligatoire
`X-Passager-Id` represente l'acteur : il doit correspondre au profil a modifier.
C'est une simulation de propriete, pas une authentification securisee.
Un profil absent retourne 404, un autre proprietaire 403, des donnees invalides
ou un header absent/non numerique 400. Le passager peut conserver son propre email.
L'email d'un autre passager est refuse avec 409 et le message
Cet email est deja utilise par un autre passager. La contrainte unique PostgreSQL
protege aussi les acces concurrents ; une violation SQLState 23505 retourne 409.
Les creations et modifications sont transactionnelles et journalisees au niveau INFO.

Le gestionnaire d'erreurs commun est reutilise. L'historique des reservations
sera ajoute avec la fonctionnalite de reservation.

Le [guide des tests Postman passagers](docs/tests-passagers.md) fournit les requetes,
les corps, les headers et les resultats attendus. La collection reste dans Postman
jusqu'a l'export final prevu avec les autres fonctionnalites.

La [collection simple importable](postman/covoiturage-simple.postman_collection.json)
regroupe Conducteurs, Passagers, Vehicules et Publication des trajets, sans variables ni scripts. Les URLs sont completes
et les identifiants d'exemple sont ecrits directement (1, 2, 999999).
Adapter manuellement les ids et les emails aux profils de la base.
Un POST genere son propre id, qui n'est pas forcement 1. Les noms et descriptions
des requetes indiquent les resultats attendus. Executer la creation valide avant
le test de doublon correspondant, en conservant les memes valeurs pour ce dernier.

## Gerer les vehicules

| Methode | Chemin | Resultat |
|---|---|---|
| POST | /api/conducteurs/{conducteurId}/vehicules | 201 et vehicule cree |
| GET | /api/vehicules/{id} | 200, ou 404 si absent |
| GET | /api/conducteurs/{conducteurId}/vehicules | 200 et liste, ou 404 si conducteur absent |
| PUT | /api/vehicules/{id} | 200 et vehicule actualise |
| DELETE | /api/vehicules/{id} | 204 sans corps |

POST, PUT et DELETE demandent le header `X-Conducteur-Id`, qui simule l'acteur.
Il doit correspondre au proprietaire ; sinon 403. Ce header n'est pas une authentification.
Le proprietaire est fixe a la creation par l'identifiant du chemin et ne peut pas
etre modifie par PUT. Une ressource absente retourne 404.

Corps POST/PUT complet :

```json
{
  "brand": "Dacia",
  "model": "Logan",
  "registrationNumber": "12345-A-6",
  "color": "Blanc",
  "capacity": 4
}
```

Les textes sont obligatoires et limites a 255 caracteres. La capacite est obligatoire,
entre 1 et 8 places passagers, sans compter le conducteur. Donnees invalides : 400.
L'immatriculation est unique : doublon en creation ou sur un autre vehicule en
modification retourne 409. Conserver son immatriculation actuelle est autorise.
La reponse contient les champs du vehicule, son id genere et conducteurId.
Un conducteur existant sans vehicules a une liste vide (200 et []).

Avant suppression, le service verifie qu'aucun trajet n'utilise le vehicule : sinon 409.
La cle etrangere Trajet -> Vehicule protege aussi la base, sans suppression en cascade.
Le DELETE est force par flush et un refus SQLState 23503 est traduit en 409.
Les tests d'integration PostgreSQL verifient le refus du service, la protection
directe par la base et la conservation du vehicule et du trajet apres rollback.

Les tests VehiculeServiceImplTest couvrent proprietaire, doublons, lecture, liste,
modification et suppression. VehiculeValidationTest couvre les bornes 1 et 8,
les capacites nulles/hors limites et les champs textes obligatoires.
Le dossier Vehicules de la collection simple contient les requetes manuelles,
avec ids et headers ecrits directement. Adapter les ids aux donnees existantes,
et executer la suppression apres les lectures et modifications.

## Publier un trajet (COV-12)

```text
POST http://localhost:8080/api/conducteurs/1/trajets
Content-Type: application/json
X-Conducteur-Id: 1
```

Le conducteur du chemin et l'acteur du header doivent correspondre. Le vehicule
doit appartenir a ce conducteur. Le header reste une simulation d'identite.

```json
{
  "vehiculeId": 1,
  "offeredSeats": 3,
  "pointsArret": [
    {"city": "Rabat", "passageTime": "2026-12-01T17:00:00", "priceFromDeparture": 0.00},
    {"city": "Casablanca", "passageTime": "2026-12-01T18:15:00", "priceFromDeparture": 40.00},
    {"city": "Marrakech", "passageTime": "2026-12-01T21:30:00", "priceFromDeparture": 120.00}
  ]
}
```

Adapter les ids aux donnees existantes et les dates pour conserver un depart futur.
Les places proposees vont de 1 a la capacite du vehicule. Le parcours contient
au moins deux points, avec villes non vides et uniques apres trim/comparaison
sans distinction de casse. Les heures sont strictement croissantes. Le premier
prix vaut zero, puis les prix sont strictement croissants, non negatifs et limites
a deux decimales (10 chiffres entiers maximum).

Le service genere stopOrder (0, 1, 2...), deduit departureCity, arrivalCity et
departureTime des points et cree le trajet avec statut PUBLIE. Les prix sont des
BigDecimal stockes avec deux decimales. Le trajet et ses points sont sauvegardes
dans une seule transaction. Il n'existe pas de classe Segment.

| Cas | HTTP attendu |
|---|---|
| Publication valide | 201, ids du trajet et des points, statut PUBLIE |
| Conducteur ou vehicule inexistant | 404 |
| Acteur different ou vehicule d'un autre conducteur | 403 |
| Places invalides, parcours/date/ordre des heures/prix invalides | 400 |
| Suppression du vehicule apres publication | 409 |

Les annotations DTO verifient aussi les champs obligatoires et les elements de la
liste grace a Valid. Les regles comparees entre points sont verifiees dans le service.
Les contraintes uniques composees ne sont pas necessaires dans les annotations
des entites ; la validation du service gere l'ordre et les villes repetees.

Le dossier Trajets - Publication COV-12 de la collection simple contient les cas
valides et refuses. La [collection de publication seule](postman/trajets-publication.postman_collection.json)
permet d'importer uniquement ce dossier, sans variables ni scripts.

TrajetServiceImplTest couvre les validations avec mocks. TrajetValidationTest
verifie les contraintes DTO imbriquees. TrajetPublicationIntegrationTest utilise
PostgreSQL et les vrais mappers pour verifier la persistance, l'ordre, le rollback
du trajet avec ses points et le refus de suppression du vehicule reference.
Ce test cree et nettoie uniquement ses propres donnees identifiees par ids et UUID.
Un rollback ne remet pas a zero les sequences : des trous dans les ids sont normaux.

Pour l'instant, COV-12 expose uniquement la publication. La consultation detaillee,
la recherche, les reservations et les changements de statut restent dans les stories suivantes.

## Consulter le detail d'un trajet (COV-13)

`GET /api/trajets/{id}` retourne 200 pour un trajet existant et 404 sinon.
Un identifiant non numerique retourne 400. Aucun header d'acteur n'est demande.
La reponse contient les villes, le depart, les places proposees, le statut,
le conducteur (id, nom, prenom, telephone), le vehicule et les points d'arret
ordonnes avec leurs heures et prix. Le permis et l'email du conducteur ne sont
pas exposes dans ce detail.

TrajetDetailResponse est distinct du DTO de publication. Le repository utilise
JOIN FETCH pour charger conducteur, vehicule et points dans une seule requete.
Le service est transactionnel en lecture seule. Le test PostgreSQL verifie
le mapping, l'ordre des points et le nombre de requetes SQL (deux au total :
une pour le detail et ses relations, une pour la somme des places confirmees).

offeredSeats represente les places proposees a la publication. remainingSeats
est calcule a chaque lecture : offeredSeats moins la somme des nombrePlaces
des reservations CONFIRMEE. Les reservations ANNULEE sont exclues. Sans reservation,
remainingSeats = offeredSeats ; un trajet COMPLET a zero place restante.
Ce champ n'est pas stocke dans la table trajets. Le service utilise la requete
de somme du ReservationRepository et transmet le resultat au mapper.
Les tests PostgreSQL couvrent les reservations confirmees, annulees et le
remplissage complet sans changer le parcours ou les places proposees.

Importer `postman/trajets-detail.postman_collection.json` ou utiliser le dossier
Trajets - Detail COV-13 de la collection simple. Remplacer 1 par un id obtenu
lors d'une publication ; 999999 est suppose absent.

Verification HTTP du 18 novembre 2026 sur l'API locale et PostgreSQL : les sept
requetes de cette collection et deux controles supplementaires ont reussi.
remainingSeats concorde avec SQL : 4 sans reservation, 1 avec trois places
CONFIRMEE, 3 avec une place CONFIRMEE et deux ANNULEE, 0 pour COMPLET, puis 4
si toutes les reservations du trajet de test sont ANNULEE. Les ids, le parcours,
les heures, les prix et offeredSeats sont conserves ; les GET ne modifient pas
les donnees. Les statuts ANNULEE ont ete prepares uniquement sur les fixtures
via SQL, sans tester un endpoint d'annulation. Les fixtures ont ete nettoyees.

## Rechercher des trajets (COV-14)

TrajetSearchRequest prepare les parametres de recherche : departureCity et
arrivalCity (non vides, maximum 255 caracteres), date (LocalDate, format
yyyy-MM-dd), seats (obligatoire, minimum 1), maxPrice (optionnel, positif ou nul,
deux decimales et dix chiffres entiers maximum), page (minimum 0) et size (1 a 100).
Si page et size sont absents, le constructeur du record utilise 0 et 10.
Le prix maximum concerne une place sur le segment demande, pas le montant pour
toutes les places. Aucun filtre de date future n'est ajoute au DTO.

TrajetSearchResult represente un resultat de recherche : trajetId, departureTime
(depart du trajet entier, pour le tri), monteeId/departureCity/monteeTime et
descenteId/arrivalCity/descenteTime (segment demande), segmentPrice (par place)
et remainingSeats. La projection prepare un nombre de places de type Long,
compatible avec la somme JPQL des places reservees. Les entites JPA ne sont pas
exposees dans ce resultat.

TrajetRepository.search utilise deux jointures sur les points du meme trajet
(montee et descente), avec ordre montee < ordre descente. La recherche compare les
villes sans distinction de casse et garde uniquement les trajets PUBLIE.
Le filtre de date concerne le depart du trajet entier : debut du jour inclus,
debut du lendemain exclu, meme si la montee a lieu apres minuit.

Une jointure gauche sur les reservations CONFIRMEE permet de calculer les places
restantes avec SUM et COALESCE (zero en l'absence de reservation). GROUP BY regroupe
chaque segment et HAVING filtre les places disponibles. Les ANNULEE sont ignorees,
les places CONFIRMEE comptent sur tout le trajet, independamment de leur segment.
Le prix maximal optionnel porte sur la difference des prix cumules, par place.
La requete construit directement TrajetSearchResult, sans charger les entites.

Le resultat est un Slice, trie par depart du trajet puis id pour stabiliser les
egalites. Chaque page utilise une seule requete SQL, sans COUNT supplementaire ;
hasNext indique s'il reste une page suivante. Il n'y a pas de total de pages.

TrajetSearchValidationTest verifie les champs obligatoires et les bornes.
TrajetSearchRepositoryTest utilise PostgreSQL pour verifier segments intermediaires,
sens, appartenance des points, statuts, prix, reservations, bornes de dates,
pagination et une seule requete SQL par Slice. Les fixtures de test utilisent
des villes uniques et sont annulees par rollback pour conserver les donnees locales.
TrajetService.search verifie les criteres, retire les espaces au debut et a la fin
des villes et construit la plage de dates [debut du jour, debut du lendemain[.
Il cree PageRequest avec page/size puis appelle une seule fois le repository.
La methode est transactionnelle en lecture seule, sans sauvegarde ni mapping
supplementaire : le repository retourne deja des DTO dans un Slice. Une recherche
sans resultat ne declenche pas de 404 ; le Slice reste vide. Les tests service simulent le
repository et verifient criteres invalides, nettoyage, dates, pagination et hasNext.
GET /api/trajets/search accepte ces parametres dans l'URL, sans body ni header
d'acteur. Le controller lie les parametres au record avec ModelAttribute et
demande leur validation avec Valid. La reponse 200 utilise TrajetSearchResponse :
content (liste de TrajetSearchResult), page, size et hasNext. Sans correspondance,
content est vide et hasNext vaut false. Aucun totalPages n'est retourne.

```text
GET /api/trajets/search?departureCity=Casablanca&arrivalCity=Settat&date=2026-12-01&seats=1&page=0&size=10
```

Parametres obligatoires absents, date invalide/impossible, chiffres non numeriques
ou valeurs hors bornes retournent 400 ProblemDetail. La route exacte /search
est distincte de GET /api/trajets/{id}. Les endpoints de detail et publication
conservent leurs URLs. TrajetSearchControllerTest utilise MockMvc avec service
simule pour verifier liaison des parametres, valeurs par defaut, validation et JSON.

La [collection recherche](postman/trajets-recherche.postman_collection.json)
contient les preparations, cas valides, erreurs 400, pagination et reservations.
Elle existe aussi dans la collection simple, sans variables ni scripts. Le
[guide Postman recherche](docs/tests-recherche-trajets.md) explique les trois
trajets de reference B/C/A, les ids et dates a adapter, les prix attendus,
les places disponibles et les fixtures optionnelles pour les statuts annules.

## Reserver des places (COV-15)

Le modele Reservation et son repository sont prepares. Une reservation reference
un passager, un trajet, un point de montee et un point de descente via quatre
relations ManyToOne, sans cascade vers ces donnees existantes. Elle contient
nombrePlaces, montantTotal (BigDecimal a deux decimales), dateReservation et
statut (CONFIRMEE ou ANNULEE, stocke en texte).

ReservationPersistenceTest verifie ces relations et la sauvegarde des champs
dans PostgreSQL. Ses donnees temporaires sont annulees a la fin du test.
Il ne valide pas les regles metier ni une annulation via l'API.

ReservationCreateRequest accepte trajetId, monteeId, descenteId (obligatoires et
positifs) et nombrePlaces (obligatoire, minimum 1). Le passager sera identifie par
le chemin de l'endpoint et le header d'acteur, comme pour les autres fonctionnalites.
Le client ne choisit ni montant, ni date, ni statut. ReservationMapper copie le
nombre de places ; les relations et les champs calcules seront fixes par le service.
ReservationResponse expose les identifiants des relations et les champs de la
reservation, sans serialiser les entites JPA.

Les tests verifient la validation DTO et les conversions avec le vrai mapper
dans le test PostgreSQL. L'existence des ids, l'appartenance des points au trajet,
leur ordre et les places disponibles sont verifies dans le service.

ReservationService.create verifie passager, acteur, trajet PUBLIE, points du meme
trajet dans le bon ordre et nombre de places positif. Une seule reservation
CONFIRMEE par passager et trajet est autorisee. Une ancienne ANNULEE ne bloque pas.
Les places restantes sont calculees en soustrayant uniquement la somme des places
CONFIRMEE, sur tout le trajet, independamment du segment choisi. Les places
insuffisantes ou un statut incompatible retournent un conflit metier (409).
Les points d'un autre trajet ou dans le mauvais ordre sont refuses (400) ; une
ressource absente donne 404 et un acteur different donne 403.

Le montant vaut (prix descente - prix montee) fois nombrePlaces, avec deux decimales.
Le service fixe dateReservation et CONFIRMEE, puis sauvegarde la reservation.
Si aucune place ne reste, le trajet devient COMPLET dans la meme transaction.
Le trajet charge est gere par JPA : son changement de statut est sauvegarde
automatiquement au flush/commit (dirty checking).

Le verrou PESSIMISTIC_WRITE sur le trajet est pris avant lecture des places et
conserve jusqu'a la fin de la transaction. Les futurs traitements qui changent les
places ou le statut devront respecter le meme verrouillage.
Les tests service couvrent succes et refus ; PostgreSQL verifie la somme CONFIRMEE,
l'exclusion des ANNULEE et la persistance du statut COMPLET apres reservation.

ReservationConcurrencyTest lance deux threads avec deux transactions et connexions
PostgreSQL distinctes pour la derniere place. Il observe pg_blocking_pids pour
verifier que la seconde transaction attend reellement le verrou de la premiere.
Si la premiere valide, la seconde est refusee avec BusinessException (409 via le
handler HTTP). Si la premiere fait rollback, la seconde peut reserver la place.
Les deux scenarios verifient une seule reservation CONFIRMEE, zero place restante
et un trajet COMPLET. Les donnees commitees necessaires a ce test sont creees avec
des UUID, puis seules ces donnees sont nettoyees apres chaque scenario.
Ce sont des tests du service avec PostgreSQL, pas encore des tests HTTP.

Pour les rejouer seuls (PostgreSQL doit etre demarre) :

```powershell
.\mvnw.cmd "-Dtest=ReservationConcurrencyTest" test
```

POST /api/passagers/{passagerId}/reservations cree une reservation et retourne
201 avec ReservationResponse. Le header X-Passager-Id est obligatoire et doit
correspondre au passager du chemin. Le body demande trajetId, monteeId,
descenteId et nombrePlaces. DTO invalide, JSON malforme, header absent ou id non
numerique : 400. Ce header est une simulation de propriete, pas une authentification.

ReservationControllerTest verifie le JSON de succes, les validations avant appel
du service et les erreurs ProblemDetail. Ces tests utilisent MockMvc avec service
simule ; la persistance et la concurrence sont verifiees separement sur PostgreSQL.

Le [guide Postman Reservation](docs/tests-reservations.md) explique les fixtures,
le scenario 50 DH / 160 DH, les refus, le remplissage du trajet et les verifications
SQL en lecture seule. La collection importable est
`postman/reservations.postman_collection.json` ; son dossier est aussi dans la
collection simple. Sans variables ni scripts, ids et dates sont a adapter manuellement.
Les endpoints d'historique et d'annulation restent dans les stories suivantes.
Le detail trajet de COV-13 expose remainingSeats calcule a partir de ces reservations.

## Verifier les erreurs HTTP

Pour activer les endpoints techniques :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test-errors"
```

Ces endpoints sont des simulations pour verifier le gestionnaire d'erreurs.
Ils ne verifient pas encore les regles des fonctionnalites metier.

Dans IntelliJ, il est aussi possible de dupliquer la configuration Spring Boot
dans Run > Edit Configurations et de renseigner `test-errors` dans Active profiles.
Si ce champ n'est pas disponible, ajouter `--spring.profiles.active=test-errors`
dans Program arguments. Garder une configuration normale sans ce profil.

Dans Postman, regrouper les requetes dans le dossier
`Tests techniques - Gestion des erreurs` de la collection `Covoiturage`.

| Methode | Chemin | Corps | Status attendu |
|---|---|---|---|
| GET | /test-errors/not-found | Aucun | 404 |
| GET | /test-errors/forbidden | Aucun | 403 |
| GET | /test-errors/conflict | Aucun | 409 |
| GET | /test-errors/bad-request | Aucun | 400 |
| POST | /test-errors/validation | Nom vide | 400 |
| POST | /test-errors/validation | JSON malforme | 400 |
| GET | /test-errors/number/abc | Aucun | 400 |
| GET | /test-errors/number/12 | Aucun | 200, reponse vide |
| POST | /test-errors/validation | Nom valide | 200, reponse vide |
| POST | /test-errors/not-found | Aucun | 405, header Allow contenant GET |
| GET | /test-errors/internal | Aucun | 500 |

Pour les requetes POST de validation, utiliser
`Content-Type: application/json` (Body > raw > JSON dans Postman).

Corps valide :

```json
{
  "nom": "Salma"
}
```

Corps invalide :

```json
{
  "nom": ""
}
```

JSON malforme (accolade de fermeture volontairement absente) :

```text
{"nom": "Salma"
```

Verifier que le status HTTP correspond au champ `status` du JSON,
que `instance` contient le chemin et que `timestamp` est present.
Le test 500 doit retourner un message general au client et ecrire
les details techniques dans les logs, sans arreter l'application.

Les endpoints `/test-errors` sont desactives sans le profil `test-errors`.

## Format des erreurs

Les erreurs sont gerees dans `GlobalExceptionHandler`.

Les reponses utilisent ProblemDetail avec :

- `status` : code HTTP ;
- `title` : titre de l'erreur ;
- `detail` : message explicatif ;
- `instance` : chemin de la requete ;
- `timestamp` : date et heure de l'erreur ;
- `errors` : liste des erreurs de validation, si applicable.

Le champ standard `type` utilise la valeur par defaut `about:blank`
lorsqu'aucun type particulier n'est defini ; sa presence dans le JSON
depend de la serialisation.

Les refus metier, les acces interdits, les donnees refusees et les erreurs
de validation sont journalises au niveau WARN.
Les erreurs inattendues sont journalisees au niveau ERROR,
sans exposer leurs details techniques au client.
Les erreurs Spring implementant ErrorResponse conservent leur status et leurs headers.

## Architecture

```text
ma.youcode.covoiturage
|-- controller
|-- service
|   `-- impl
|-- repository
|-- entity
|-- dto
|   |-- conducteur
|   |-- passager
|   |-- vehicule
|   `-- trajet
|-- mapper
`-- exception
```

Chaque fonctionnalite est developpee avec ses couches et ses tests
avant de passer a la suivante.

## Conception

- [Diagramme SVG](docs/conception/diagramme-classes.svg)
- [Diagramme modifiable draw.io](docs/conception/diagramme-classes.drawio)
- [Source PlantUML](docs/conception/diagramme-classes.puml)
- [Explications des relations](docs/conception/README.md)

## Workflow Git

- `main` : branche de livraison.
- `develop` : integration des fonctionnalites terminees.
- `feature/COV-...` : travail sur une fonctionnalite.

Chaque changement coherent possede son commit.
Les branches feature sont fusionnees dans develop apres verification,
sans squash pour conserver les commits.
Develop sera fusionnee dans main pour la livraison.

## Arreter PostgreSQL

```powershell
docker compose stop
```

## Probleme connu sous Windows

Si Maven Wrapper affiche que `powershell` est introuvable,
ajouter son dossier au PATH du terminal courant :

```powershell
$env:Path += ";C:\Windows\System32\WindowsPowerShell\v1.0"
```

Puis relancer la commande Maven.
