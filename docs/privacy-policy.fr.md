---
title: Politique de confidentialité de Scan2Anki
---

# Politique de confidentialité de Scan2Anki

*[English version](privacy-policy.md)*

Date d'entrée en vigueur : 9 octobre 2026

Scan2Anki transforme des photos de listes de vocabulaire imprimées en cartes
AnkiDroid. L'application n'a ni compte utilisateur, ni publicité, ni serveur
propre. Le développeur ne reçoit aucune de vos photos, aucun texte ni aucun
réglage.

## Appareil photo et photos

Scan2Anki utilise l'appareil photo pour photographier les pages à importer, et
le sélecteur de photos d'Android pour importer les images que vous choisissez
dans votre galerie. L'application ne voit que les photos que vous
sélectionnez.

Les photos sont conservées dans le stockage privé de l'application uniquement
pour la session d'import en cours ; elles sont supprimées au démarrage de la
session suivante. Elles ne sont jamais
envoyées nulle part, sauf si vous choisissez la reconnaissance de texte dans le
cloud (voir ci-dessous).

## Reconnaissance de texte

**Sur l'appareil (par défaut).** Le texte est reconnu sur votre téléphone avec
Google ML Kit. Vos photos restent sur l'appareil. ML Kit envoie lui-même à
Google des données de diagnostic limitées : informations sur l'appareil (modèle,
version d'Android…), nom de paquet et version de l'application, un identifiant
propre à l'installation qui ne vous identifie pas, ni votre appareil, des
mesures de performance et la configuration de l'API. Google les utilise pour le
diagnostic et les statistiques d'utilisation et ne les transmet pas à des tiers.
Voir la [déclaration de Google sur les données de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).

**Google Cloud Vision (facultatif).** Si vous saisissez votre propre clé d'API
Google Cloud Vision et choisissez la reconnaissance dans le cloud, la photo de
la page est envoyée en HTTPS directement depuis votre téléphone à l'API Cloud
Vision de Google, facturée sur votre propre projet Google Cloud. Ce traitement
est régi par les conditions et la politique de confidentialité de Google Cloud,
et non par le développeur de Scan2Anki. Votre clé d'API est stockée uniquement
dans le stockage privé de l'application sur votre appareil et est exclue des
sauvegardes Android.

## AnkiDroid

Lors de l'envoi des cartes, Scan2Anki utilise l'API locale d'AnkiDroid pour lire
les noms de vos paquets et types de notes et pour ajouter les nouvelles notes.
Cet échange se fait entièrement sur votre téléphone.

## Réglages

Vos réglages (paquet par défaut, type de note, règles de nettoyage, écriture
OCR et clé d'API) sont stockés uniquement sur votre appareil. Ils sont supprimés
si vous désinstallez l'application ou effacez ses données.

## Enfants

Scan2Anki ne s'adresse pas aux enfants de moins de 13 ans et ne collecte
sciemment aucune donnée personnelle de qui que ce soit.

## Modifications

Si cette politique change, la nouvelle version sera publiée à cette adresse
avec une nouvelle date d'entrée en vigueur.

## Contact

Pour toute question sur cette politique, ouvrez un ticket sur
<https://github.com/jpcadiat/Scan2Anki/issues>.
