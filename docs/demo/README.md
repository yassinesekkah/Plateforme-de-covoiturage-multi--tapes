# Donnees de demonstration

La base locale a ete reinitialisee a la demande de l'utilisateur. Les six tables metier et leurs sequences repartent de 1. Les collections Postman utilisent deja ces nouveaux ids.

## Contenu

| Table | Nombre de lignes |
| --- | --- |
| conducteurs | 5 |
| passagers | 5 |
| vehicules | 6 |
| trajets | 6 |
| points_arret | 24 |
| reservations | 7 |

Les profils ont des noms, telephones et emails distincts. Les emails example.com sont fictifs.

Voitures : Dacia Logan blanche, Renault Clio bleue, Toyota Corolla grise, Hyundai Tucson noire, Peugeot 208 rouge et Volkswagen Polo argent. Les capacites sont comprises entre 3 et 5 places passagers ; la voiture de Hamid a une capacite de 4.

## Ids utiles

Hamid : conducteur **1**, voiture **1**. Omar : conducteur **2**, voiture **2**.
Passagers : Salma **1**, Karim **2**, Imane **3**, Sara **4**, Amine **5**.
La voiture **6** n'a aucun trajet : elle sert uniquement a tester la suppression.

| Trajet | Id | Etat initial | Action prevue |
| --- | --- | --- | --- |
| Essais courants | 1 | PUBLIE, 2 places | Amine reserve ; Salma annule reservation 1 ; ensuite annulation trajet |
| Terminaison | 2 | PUBLIE, depart passe | Terminer le trajet ; reservation Salma 2 conservee |
| Scenario principal | 3 | PUBLIE, 3 places | Scenarios 1 a 4 : Salma puis Karim remplissent le trajet |
| Copie pour Karim | 4 | COMPLET, 0 place | Scenario 5 annule reservation Karim 4 ; scenarios 6 et 9 sur ce trajet redevenu PUBLIE |
| Depart proche | 5 | PUBLIE, 2 places | Scenario 10 refuse annulation reservation Salma 5 |
| Annulation groupee | 6 | COMPLET, 0 place | Scenario 11 annule trajet et reservations Salma 6 / Karim 7 |

Les parcours reprennent volontairement l'exemple du brief : Rabat 0 DH, Casablanca 40 DH, Settat 70 DH, Marrakech 120 DH. Les copies permettent de tester plusieurs regles sans devoir recopier les ids de reservations nouvellement creees.

## Essayer dans Postman

1. Demarrer PostgreSQL et l'API sur 8080.
2. Reimporter `postman/covoiturage-simple.postman_collection.json` pour remplacer les anciens ids 353, 419, etc.
3. Les GET sont utilisables directement. Les POST de nouveaux profils/vehicules ont des valeurs encore inutilisees et se font une seule fois.
4. Faire **Reservations avant Annuler un trajet**. Terminer utilise le trajet 2 ; supprimer utilise la voiture 6.
5. Le dossier des onze scenarios se joue **00 puis 01 a 11**. Les preparations sont deja faites et affichees avec GET.

Les trajets futurs partent trois jours apres la preparation. Le trajet 5 partait environ deux heures apres la preparation, et le trajet 2 une heure avant. Les dates de publication/recherche sont pre-remplies. Si plusieurs jours passent, adapter les dates. Les limites de temps sont verifiees avec l'heure locale de l'application.

Les GET peuvent etre repetes. Les actions qui creent, annulent, terminent ou suppriment changent l'etat : ne pas les rejouer en attendant le meme resultat.

## Reinitialisation

`donnees-demonstration.sql` supprime les donnees des six tables et remet leurs sequences a 1 avant insertion. Ne le relancer que pour recommencer volontairement le jeu complet. Une transaction protege l'ensemble.

Les ids peuvent ensuite avoir des trous : une sequence avance aussi lors de certains essais refuses ou rollbacks. Un id n'est pas un compteur du nombre de profils existants.

La sauvegarde avant cette reinitialisation est conservee localement dans `sauvegardes/base-avant-reinitialisation.sql` (hors Git).
Les fichiers `ids-demonstration.json` et `verification-donnees.json` decrivent le jeu actuel.

## Verification de la collection avec les petits ids

Le 19 novembre 2026, les **59 requetes** (24 endpoints metier et 35 requetes de preparation/controle des onze scenarios) ont ete executees sur une copie temporaire de cette base, avec les memes petits ids, via l'API sur 8081.

Resultat : **59/59 requetes et 11/11 scenarios reussis**, avec comparaison PostgreSQL apres chaque requete : montants 50/160 DH, places, proprietaires, parcours, historiques et transitions de statut. Les erreurs et GET n'ont modifie aucune ligne ; les annulations et suppressions ont modifie uniquement les donnees attendues.

Les essais courants ont respecte l'ordre indique : Reservations avant Annuler un trajet. La base originale a ete comparee ligne par ligne avant/apres dans les six tables et reste identique : les creations, la suppression, les annulations et la terminaison sont toujours disponibles pour la demonstration. La copie temporaire est supprimee apres les tests. Rapport local : `target/small-ids-full-results.json`.
