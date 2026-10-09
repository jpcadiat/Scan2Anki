<p align="center">
  <img src="scan2anki.svg" alt="Logo de Scan2Anki" width="128">
</p>

# Scan2Anki

*[English version](README.md)*

Scan2Anki est une application Android qui transforme une liste de vocabulaire
imprimée sur deux colonnes en cartes
[AnkiDroid](https://github.com/ankidroid/Anki-Android). Photographiez les pages,
vérifiez que les colonnes sont bien séparées, nettoyez le texte, puis envoyez les
paires de mots dans un paquet AnkiDroid en un seul geste.

## Fonctionnalités

- **Capture de plusieurs pages** avec l’appareil photo (lampe torche activable)
  ou depuis la galerie. Les pages peuvent être supprimées pendant la capture.
- **Reconnaissance de texte (OCR)**
  - Sur l’appareil avec Google ML Kit : gratuite, hors ligne, utilisée par
    défaut. Écriture latine ou chinoise, au choix dans les paramètres.
  - Google Cloud Vision en option pour les pages difficiles. Elle nécessite
    votre propre clé d’API, que vous pouvez tester depuis les paramètres.
- **Éditeur de colonnes** : les lignes détectées s’affichent sur la photo. Vous
  pouvez déplacer ou ajouter la séparation entre les deux colonnes, supprimer
  les lignes parasites (un titre, par exemple) et tracer des zones d’exclusion
  sur le texte à ignorer. Un aperçu en direct montre les paires obtenues, et la
  mise en page est enregistrée pour chaque page : vous pouvez la rouvrir et la
  corriger plus tard.
- **Écran de révision** : modifiez, ajoutez ou supprimez des paires, inversez
  les colonnes recto et verso, et relancez l’OCR sur une page (sur l’appareil
  ou dans le cloud).
- **Règles de nettoyage**, applicables au recto, au verso ou aux deux, avec un
  aperçu de chaque modification avant de l’appliquer :
  - supprimer les caractères parasites (et d’autres caractères de votre choix),
  - retirer les numéros de page en fin de ligne,
  - couper tout ce qui suit un séparateur,
  - corriger la casse (majuscule en début de phrase ou tout en minuscules).

  Vos réglages de nettoyage sont conservés d’une session à l’autre.
- **Export vers AnkiDroid**
  - Choisissez le paquet et le type de note dans les listes d’AnkiDroid, ou
    saisissez leur nom.
  - Le type de note par défaut (*Généralités* / *General*) est créé
    automatiquement s’il n’existe pas. Il produit deux cartes par paire :
    recto → verso et verso → recto.
  - Tout autre type de note comportant au moins deux champs peut être utilisé.
    La paire remplit les deux premiers champs, les autres restent vides.
  - Les doublons déjà présents dans AnkiDroid sont ignorés et signalés.
  - Le bouton *Vérifier la connexion* des paramètres teste le lien avec
    AnkiDroid et demande l’autorisation nécessaire.
- **Aucune donnée résiduelle** : les numérisations n’existent que pendant la
  session en cours. Seuls vos paramètres sont conservés au redémarrage.
- Interface en **français et en anglais**.

## Prérequis

- Android 8.0 (API 26) ou plus récent.
- [AnkiDroid](https://play.google.com/store/apps/details?id=com.ichi2.anki)
  installé sur le même appareil (également sur
  [F-Droid](https://f-droid.org/packages/com.ichi2.anki/)).
- En option : une clé d’API Google Cloud Vision pour l’OCR dans le cloud.

## Prise en main

### Compiler depuis les sources

Il faut un JDK (17 ou plus récent) pour lancer Gradle, ainsi que le SDK Android
(plateforme 36). Le wrapper Gradle télécharge la bonne version de Gradle.
Gradle s’exécute sur le JDK 25, qu’il installe automatiquement s’il est absent
(voir `gradle/gradle-daemon-jvm.properties`).

```bash
git clone https://github.com/jpcadiat/Scan2Anki.git
cd Scan2Anki
./gradlew assembleDebug
```

L’APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Pour
l’installer sur un appareil connecté :

```bash
./gradlew installDebug
```

Vous pouvez aussi ouvrir le projet dans Android Studio et lancer la
configuration `app`.

### Premier lancement

1. Installez AnkiDroid et ouvrez-le au moins une fois. Créez le paquet à
   remplir.
2. Ouvrez Scan2Anki, allez dans **Paramètres → AnkiDroid → Vérifier la
   connexion**, puis autorisez l’accès à AnkiDroid quand Android le demande.
   Vous pouvez aussi le faire plus tard : la demande d’autorisation apparaît au
   premier envoi de cartes.
3. Si vous le souhaitez, définissez un paquet et un type de note par défaut.

## Utilisation

1. **Capture** : photographiez chaque page de la liste ou choisissez des images
   dans la galerie. Appuyez sur **Terminé — vérifier les paires** une fois
   toutes les pages ajoutées.
2. **Ajuster les colonnes** : pour chaque page, vérifiez la ligne qui sépare la
   colonne recto de la colonne verso. Touchez une ligne de texte pour la
   sélectionner et la supprimer (un titre de page, par exemple). Tracez une
   zone d’exclusion pour ignorer une partie de la page. L’aperçu se met à jour
   au fur et à mesure.
3. **Révision** : corrigez les erreurs d’OCR, ajoutez ou supprimez des lignes,
   et utilisez **Nettoyer** pour corriger les problèmes récurrents sur toute la
   liste. Le menu de chaque page permet de rouvrir l’éditeur de colonnes ou de
   relancer l’OCR.
4. **Envoyer vers AnkiDroid** : vérifiez le paquet et le type de note en bas de
   l’écran, puis appuyez sur **Envoyer vers AnkiDroid**.

## OCR dans le cloud (facultatif)

L’OCR sur l’appareil suffit pour la plupart des listes imprimées nettes. Pour
des photos de mauvaise qualité, des mises en page denses ou des écritures
mélangées, Google Cloud Vision est souvent plus précis.

1. Créez un projet dans la [console Google Cloud](https://console.cloud.google.com/).
2. Activez l’**API Cloud Vision** et la **facturation** pour ce projet. Google
   refuse les requêtes des projets sans facturation, même dans la limite du
   niveau gratuit.
3. Créez une clé d’API. Il est conseillé de la restreindre à l’API Cloud
   Vision.
4. Collez la clé dans **Paramètres → Reconnaissance de texte** et appuyez sur
   **Tester la clé API**. En cas d’échec, l’application affiche le message
   d’erreur renvoyé par Google.

Une fois la clé enregistrée, l’option **Relancer l’OCR (Cloud)** apparaît dans
le menu de chaque page de l’écran de révision.

## Confidentialité

- L’OCR sur l’appareil ne quitte jamais votre téléphone.
- L’OCR dans le cloud envoie l’image de la page à Google Cloud Vision, et
  uniquement quand vous le choisissez pour une page.
- La clé d’API et vos préférences sont stockées localement avec Android
  DataStore.
- Les pages numérisées et les paires de mots sont supprimées au redémarrage de
  l’application.
- L’application ne contient ni statistiques d’utilisation ni pistage. Elle
  utilise l’appareil photo, l’accès à Internet (uniquement pour l’OCR dans le
  cloud) et l’autorisation d’accès à la base de données d’AnkiDroid.

## Structure du projet

Application Kotlin à module unique, construite avec Jetpack Compose
(Material 3), Hilt, Room, DataStore, CameraX, ML Kit, OkHttp et
kotlinx.serialization.

```
app/src/main/java/
├── com/scan2anki/
│   ├── ui/          Écrans Compose (capture, éditeur de zones, révision, paramètres), dialogues, navigation, thème
│   ├── vm/          ViewModels de chaque écran
│   ├── ocr/         Interface OcrEngine, implémentations ML Kit et Cloud Vision
│   ├── parse/       ColumnParser (lignes OCR → paires de mots) et règles OcrCleanup
│   ├── data/        Base Room : session, pages, paires de mots, zones par page
│   ├── settings/    Préférences utilisateur (DataStore)
│   ├── ankidroid/   Intégration AnkiDroid (paquets, types de note, envoi des notes)
│   └── util/        Outils d’image (rotation EXIF, décodage)
└── com/ichi2/anki/  API publique d’AnkiDroid (copie intégrée, voir plus bas)
```

Les schémas Room sont exportés dans `app/schemas/` et couverts par des tests de
migration.

## Tests

Les tests unitaires et d’interface s’exécutent sur la JVM avec Robolectric,
sans appareil :

```bash
./gradlew testDebugUnitTest
```

## Contribuer

Les signalements de bugs et les pull requests sont les bienvenus. Lancez la
suite de tests avant de proposer une modification et ajoutez des tests pour
tout nouveau comportement. Les textes de l’interface se trouvent dans
`app/src/main/res/values/strings.xml` (anglais) et
`app/src/main/res/values-fr/strings.xml` (français) : pensez à mettre à jour
les deux.

## Licence

Scan2Anki est distribué sous la
[Licence publique de l’Union européenne v. 1.2](LICENSE) (EUPL-1.2). L’EUPL
existe dans toutes les langues officielles de l’UE, dont le
[français](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32017D0863),
sur le site de la [Commission européenne](https://interoperable-europe.ec.europa.eu/collection/eupl/eupl-text-eupl-12).
Toutes les versions linguistiques ont la même valeur.

### Code tiers

Le dossier `app/src/main/java/com/ichi2/anki/` contient une copie de
l’[API d’AnkiDroid](https://github.com/ankidroid/Anki-Android/tree/main/api)
(© Timothy Rae, Mark Carter et contributeurs). Elle est distribuée sous
**GNU LGPL v3.0 ou ultérieure**, sauf `FlashCardsContract.kt`, dont l’en-tête
contient une licence entièrement permissive. Ce code n’est pas couvert par
l’EUPL.

Scan2Anki est un projet indépendant, sans lien avec AnkiDroid, Anki ou Google,
et non approuvé par eux.
