# Guide de démonstration — SmartRecruit AI (soutenance)

Scénario de **10 à 12 minutes** qui montre toute la chaîne : Angular → Spring Boot → MySQL, et Spring Boot → FastAPI
pour les quatre modules d'IA. Chaque étape indique **quoi faire** puis **quoi dire** (1 ou 2 phrases simples).

Ce scénario est rejoué automatiquement, dans le même ordre, par le test Playwright
[`frontend/e2e/demo-journey.e2e.ts`](../frontend/e2e/demo-journey.e2e.ts), qui vérifie chaque étape dans MySQL :
le lancer la veille garantit que la démonstration fonctionne.

## Préparation (avant l'arrivée du jury)

1. Démarrer les services (détails dans le [README](../README.md#installation-pas-à-pas)) :
   `docker compose up -d mysql`, puis le backend Spring Boot, le service IA (`python -m app`) et Angular (`npm start`).
2. Vérifier que tout est prêt :
   - `http://127.0.0.1:8000/health` → `{"status":"UP",...}` (service IA) ;
   - le modèle de turnover est entraîné (`ai-service/ml/artifacts/turnover_model.joblib` existe, sinon
     `python -m ml.download_dataset` puis `python -m ml.train`) ;
   - connexion admin : dashboard admin → carte « Module Machine Learning — risque de turnover » disponible.
3. Préparer **deux comptes de démonstration** (inscription depuis `http://localhost:4200/register`) :
   - un **recruteur** (choisir le rôle « Recruteur » sur la page d'inscription), avec son entreprise déjà renseignée
     (page « Entreprise ») : par exemple « Société Démo », secteur Logiciel, Tunis ;
   - un **candidat**, avec son profil rempli (titre « Développeuse Full Stack », ville Tunis).
4. Garder sous la main le CV **fictif** `ai-service/tests/fixtures/cv/cv_dev_fullstack_fr.pdf`
   (Camille Exemple, aucune donnée réelle).
5. Ouvrir deux fenêtres de navigateur (ou une fenêtre normale + une fenêtre privée) : une pour le recruteur, une pour
   le candidat, pour éviter de se déconnecter à chaque changement de rôle.

## Déroulé

### 1. Connexion recruteur

**Faire** : `/login` → email et mot de passe du recruteur → « Se connecter » → arrivée sur le dashboard recruteur.

**Dire** : « La connexion renvoie un jeton JWT signé qui contient le rôle ; Spring Boot vérifie ce jeton et le rôle à
chaque requête, l'interface Angular ne fait qu'adapter les menus. »

### 2. Création et publication d'une offre

**Faire** : « Nouvelle offre » → intitulé « Développeur Full Stack Java », contrat CDI, lieu Tunis, expérience
minimale 3 ans, niveau Bac+5, compétences Java, Spring Boot, Angular, MySQL, Docker, Kubernetes (Entrée après chacune),
description → « Enregistrer en brouillon ». Dans « Mes offres », l'offre est « Brouillon » → bouton « Publier » →
« Publiée ».

**Dire** : « Une offre en brouillon est invisible pour les candidats ; une fois publiée, elle apparaît dans la
recherche. Les compétences sont enregistrées dans un référentiel commun, ce qui permettra le matching. »

### 3. Connexion candidat

**Faire** : dans la deuxième fenêtre, `/login` avec le compte candidat → dashboard candidat.

**Dire** : « Même écran de connexion, mais le rôle CANDIDAT donne accès à un autre espace : un candidat qui appelle
une API recruteur ou admin reçoit une erreur 403. »

### 4. Import du CV

**Faire** : « Mon CV » → choisir `cv_dev_fullstack_fr.pdf`.

**Dire** : « Spring Boot vérifie la taille (5 Mo maximum) et la signature PDF du contenu, stocke le fichier sous un
nom aléatoire hors de Git, puis l'envoie au service IA avec une clé interne. »

### 5. Analyse NLP du CV

**Faire** : attendre « CV analysé » ; montrer les technologies détectées (Java, Spring Boot, Angular, Docker…),
les formations, les expériences et les langues ; montrer le bouton « Ajouter au profil ».

**Dire** : « Le CV Parser extrait le texte avec pdfplumber, découpe les sections, puis reconnaît compétences,
diplômes, expériences et langues avec un lexique et des expressions régulières, sans LLM. Rien n'est ajouté au
profil sans l'accord du candidat. »

### 6. Matching avec l'offre

**Faire** : « Offres » → rechercher « Full Stack » → ouvrir l'offre → montrer le score de compatibilité
(90 % avec ce CV et cette offre), les compétences trouvées, la compétence manquante Kubernetes et le détail du calcul.

**Dire** : « Le score est explicable : 60 % compétences, 25 % expérience, 15 % formation, chaque point est justifié
à l'écran. Ce n'est qu'une aide : le message rappelle qu'il ne constitue pas une décision de recrutement. »

### 7. Candidature

**Faire** : « Postuler à cette offre » → confirmer → message « Candidature envoyée » ; « Mes candidatures » :
statut « En attente ».

**Dire** : « La candidature est enregistrée dans MySQL avec le statut En attente ; une deuxième candidature sur la
même offre est refusée (erreur 409). »

### 8. Assistant RH (chatbot)

**Faire** : « Assistant RH » → poser « Quelles offres demandent Kubernetes ? », puis « Comment fonctionne le score
de compatibilité ? ».

**Dire** : « Le chatbot reconnaît l'intention de la question (classification TF-IDF) et répond uniquement à partir des
offres réellement publiées ; s'il ne sait pas, il le dit au lieu d'inventer. Aucune donnée personnelle du candidat
n'est envoyée au service IA. »

### 9. Classement côté recruteur

**Faire** : fenêtre recruteur → « Mes offres » → « Candidats » sur l'offre → la candidate apparaît **#1** avec son
score ; changer le statut en « Présélectionné » ; ouvrir « Profil et détail du score ».

**Dire** : « Les candidats sont classés par score de compatibilité, mais c'est le recruteur qui décide et change
le statut. Un recruteur ne voit que les candidatures de ses propres offres. »

### 10. Dashboards

**Faire** : montrer le dashboard recruteur (offres, candidatures par statut, score moyen), puis le dashboard
candidat ; enfin se connecter en **admin** → dashboard admin (utilisateurs, entreprises, offres, candidatures,
graphiques) → pages « Utilisateurs », « Entreprises », « Offres ».

**Dire** : « Tous les chiffres sont calculés en direct par des requêtes SQL sur MySQL, sans donnée fictive ; chaque
rôle ne voit que son périmètre. L'admin peut désactiver un compte ou supprimer une entreprise ou une offre. »

### 11. Démonstration ML : risque de turnover

**Faire** : (recruteur ou admin) « Risque de turnover » → « Exemple fictif n°2 » → « Estimer le risque » → résultat
**ÉLEVÉ, 91,7 %**, avec les principaux facteurs et l'avertissement. Option : « Exemple fictif n°1 » → **FAIBLE, 7,9 %**.

**Dire** : « Le modèle est une forêt aléatoire scikit-learn entraînée sur le dataset public et fictif IBM HR Analytics ;
âge, genre et situation familiale sont exclus. Ses performances sont modestes (F1 ≈ 0,50 sur le jeu de test) : c'est
une estimation qui ne doit jamais être utilisée seule. »

## Questions probables du jury

| Question | Réponse courte |
|---|---|
| Pourquoi pas de LLM ? | Réponses fondées sur les vraies offres, explicables, sans clé API ni envoi de données personnelles à un tiers ([chatbot.md](ai/chatbot.md#pourquoi-pas-de-llm-externe)). |
| Que se passe-t-il si le service IA est arrêté ? | Chatbot, turnover et recalcul des scores renvoient un 503 lisible ; un CV importé reste enregistré avec une analyse « en échec » à relancer ; offres, candidatures et dashboards continuent de fonctionner (testé par `scripts/e2e-step13.ps1`). |
| Le score de 90 % est-il fiable ? | Il est déterministe et justifié point par point, mais repose sur des pondérations choisies par l'équipe, pas apprises ([matching.md](ai/matching.md#8-limites-à-présenter-en-soutenance)). |
| Les métriques ML sont-elles réelles ? | Oui : rapport `ai-service/ml/reports/turnover_evaluation.json`, régénéré à l'identique par `python -m ml.train` ([turnover-ml.md](ai/turnover-ml.md#7-résultats-réellement-obtenus)). |
| Où sont les secrets ? | Dans `.env`, ignoré par Git ; l'historique Git a été vérifié (aucune valeur secrète commitée). |

## Après la démonstration

Supprimer les comptes de démonstration depuis l'espace admin (« Utilisateurs », puis « Entreprises ») : la
suppression d'un candidat supprime aussi son CV (fichier et analyse) et ses candidatures.
