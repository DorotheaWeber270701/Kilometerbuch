# Google Play: Store-Eintrag und Checkliste

Alles, was in der Play Console einzutragen ist, zum Kopieren. Die Grafiken liegen in diesem Ordner
und lassen sich mit `python store/make_graphics.py` neu erzeugen.

## Store-Eintrag (Deutsch)

**App-Name** (max. 30 Zeichen)
```
Kilometerbuch
```

**Kurzbeschreibung** (max. 80 Zeichen)
```
Kilometer, Verbrauch, Tankkosten und TÜV-Termine im Blick. Ohne Konto.
```

**Vollständige Beschreibung** (max. 4.000 Zeichen)
```
Wie viel fährst du eigentlich im Monat? Was kostet dich das Tanken? Und wann ist der nächste TÜV fällig? Das Kilometerbuch beantwortet diese Fragen mit wenigen Eingaben und übersichtlichen Diagrammen.

FAHRTEN
• Einmal im Monat die gefahrenen Kilometer eintragen, auf Wunsch mit Durchschnittsverbrauch
• Jahresübersicht mit Kilometern, Durchschnitt pro Monat, Verbrauch und ungefährer Spritmenge
• Balkendiagramm der Kilometer mit Durchschnittslinie, Liniendiagramm des Verbrauchs

TANKEN UND WARTUNG
• Tankbelege mit Datum, Litern und Betrag erfassen
• Der Preis pro Liter wird automatisch berechnet
• Ausgaben pro Monat und Literpreis im Verlauf
• Werkstattrechnungen für Inspektion, Reparaturen und Reifen festhalten

TERMINE
• Erinnerungen an Hauptuntersuchung (TÜV), Inspektion, Reifenwechsel und Bremsflüssigkeit
• Jede Erinnerung einzeln ein- und ausschaltbar
• Die Inspektion wird aus deinen Kilometern hochgerechnet: nach Zeit oder Kilometern, je nachdem, was zuerst kommt
• Gebuchten TÜV-Termin eintragen und am Vortag erinnert werden

MEHRERE AUTOS
• Beliebig viele Autos anlegen, jedes mit eigener Farbe
• Gesamtansicht über alle Autos
• Kilometerstand jederzeit mit dem Tacho abgleichen

DEINE DATEN BLEIBEN BEI DIR
• Kein Konto, keine Werbung, kein Tracking
• Die App hat keinen Internetzugriff: Alles bleibt auf deinem Handy
• Sicherung als CSV-Datei, die sich mit Excel öffnen lässt, und Wiederherstellung aus dieser Datei

Mit hellem und dunklem Design, auf Deutsch, Englisch, Spanisch, Französisch, Italienisch, Portugiesisch, Polnisch und Türkisch.

Das Kilometerbuch ist für den privaten Überblick gedacht und kein Fahrtenbuch im Sinne des Finanzamts.
```

**Kategorie:** Auto & Fahrzeuge
**Tags (falls gefragt):** Auto, Fahrzeugverwaltung, Kraftstoff
**Kontakt-E-Mail:** deine öffentliche Kontaktadresse (wird im Store angezeigt)
**Datenschutzerklärung:** `https://github.com/PeguinDevelopment/Kilometerbuch/blob/main/DATENSCHUTZ.md`
(erst eintragen, wenn die Datei mit deiner E-Mail-Adresse auf GitHub liegt)

**Grafiken**
| Was | Datei / Vorgabe |
|---|---|
| App-Symbol 512 × 512 | `store/icon-512.png` |
| Vorstellungsgrafik 1024 × 500 | `store/feature-graphic-1024x500.png` |
| Smartphone-Screenshots | mindestens 2, besser 4–6. Die lange Seite darf höchstens doppelt so lang sein wie die kurze. Viele Handys haben 20:9 und sind damit zu lang, dann müssen die Bilder zugeschnitten werden. |

Gute Motive für Screenshots: Fahrten mit Diagrammen, Tanken mit Diagrammen, Termine-Übersicht, Gesamtansicht mit mehreren Autos, Dunkelmodus.

## Versionshinweise für 1.0
```
<de-DE>
Erste Version: Fahrten, Tanken, Termine und mehrere Autos.
</de-DE>
```

## Checkliste in der Play Console

### 1. App erstellen
- Name „Kilometerbuch“, Standardsprache Deutsch, **App**, **Kostenlos**
- Erklärungen zu Richtlinien und Exportbestimmungen bestätigen

### 2. App einrichten (Dashboard, Abschnitt „App einrichten“)
| Punkt | Antwort |
|---|---|
| Datenschutzerklärung | URL von oben |
| App-Zugriff | Alle Funktionen sind ohne besondere Zugriffsrechte verfügbar |
| Werbung | Nein, enthält keine Werbung |
| Einstufung des Inhalts | Fragebogen: Kategorie „Alle anderen App-Typen“, alle Fragen mit Nein beantworten (keine Gewalt, keine Nutzerinteraktion, kein Teilen des Standorts, keine Käufe) |
| Zielgruppe | 18 Jahre und älter (die App richtet sich an Autofahrer; so gelten keine Sonderregeln für Kinder) |
| Nachrichten-App | Nein |
| Datensicherheit | „Erfasst oder teilt die App erforderliche Nutzerdaten?“ **Nein**. Die App hat keine Internet-Berechtigung, nichts verlässt das Gerät. |
| Behörden-App | Nein |
| Finanzfunktionen | Keine |
| Gesundheit | Keine Gesundheitsfunktionen |
| Händlerstatus (Digital Services Act) | **Kein Händler**, wenn du die App privat und ohne Gewinnabsicht anbietest. Dann wird deine Adresse nicht im Store angezeigt. |

### 3. Store-Eintrag
- Texte und Grafiken von oben, dazu die Screenshots

### 4. Geschlossener Test (Pflicht für neue private Konten)
1. **Testen → Geschlossener Test → Track erstellen** (der Standard-Track „Alpha“ geht auch)
2. **Tester:** E-Mail-Liste anlegen mit **mindestens 12 Personen** mit Google-Konto. Sicherheitshalber 14–15, falls jemand abspringt.
3. **Release erstellen:** `app/build/outputs/bundle/release/app-release.aab` hochladen, Versionshinweise eintragen. Bei der Frage nach der **App-Signatur durch Google Play** zustimmen (Standard).
4. **Länder:** mindestens Deutschland auswählen
5. Release **zur Überprüfung senden**. Die erste Prüfung dauert oft einige Tage.
6. Nach der Freigabe den **Teilnahme-Link** an die Tester schicken. Jeder muss
   - den Link öffnen und „Tester werden“ antippen,
   - die App über den Play Store installieren,
   - **14 Tage lang Tester bleiben**. Wer früher austritt, zählt nicht.

### 5. Produktionszugriff beantragen
- Nach 14 Tagen mit mindestens 12 Testern erscheint im Dashboard **„Zugriff auf die Produktion beantragen“**
- Fragen zum Test beantworten (wie getestet wurde, was verbessert wurde)
- Google entscheidet meist innerhalb von 7 Tagen
- Danach: **Produktion → Release erstellen**, dasselbe oder ein neueres AAB hochladen, veröffentlichen

## Jedes Update
1. In `app/build.gradle.kts` `versionCode` um 1 erhöhen (Pflicht) und `versionName` anpassen
2. In Android Studio: **Build → Generate Signed App Bundle / APK**, oder auf der Kommandozeile `gradlew bundleRelease`
3. Neues AAB im gewünschten Track hochladen

Der Upload-Schlüssel liegt in `keystore/` mit Passwort in `keystore.properties`. Beides ist nicht im Repository: **sicher aufbewahren**, sonst sind keine Updates mehr möglich.
