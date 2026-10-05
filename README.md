# Kilometerbuch

Eine einfache Android-App, um für ein oder mehrere Autos festzuhalten, wie viel man pro Monat gefahren ist, wie hoch der Verbrauch war und was das Tanken gekostet hat. Die Werte werden als Diagramme über die Zeit angezeigt.

Die App ist für den privaten Überblick gedacht, sie ist kein Fahrtenbuch fürs Finanzamt. Alle Daten bleiben auf dem Handy, es gibt kein Konto und keinen Server.

## Funktionen

**Fahrten**
- Pro Monat die gefahrenen Kilometer und optional den Durchschnittsverbrauch (l/100 km) eintragen
- Jahresübersicht: gefahrene Kilometer, Ø pro Monat, Ø Verbrauch, ungefähre Spritmenge
- Diagramme: Kilometer pro Monat (Balken mit Durchschnittslinie) und Verbrauch (Linie)

**Tanken und Wartung**
- Tankbelege mit Datum, Litern und Gesamtkosten eintragen
- Der Preis pro Liter wird automatisch berechnet
- Diagramme: Ausgaben pro Monat (Balken mit gestrichelter Linie für den Monatsdurchschnitt) und Preis pro Liter (Linie)
- Wartungs- und Reparaturkosten mit Art, Betrag und Notiz

**Termine** (im Menü)
- Erinnerungen an HU (TÜV), Inspektion, Reifenwechsel und Bremsflüssigkeit, jede einzeln schaltbar
- Benachrichtigung, wenn etwas bald fällig oder überfällig ist

**Mehrere Autos**
- Beliebig viele Autos anlegen, umbenennen und löschen
- Oben links zwischen den Autos wechseln oder „Alle Autos“ für die Gesamtansicht wählen
- In der Gesamtansicht sind die Balken nach Autos gestapelt, und jedes Auto hat eine eigene Verbrauchslinie in seiner Farbe
- Farbe aus einer Palette oder frei wählbar; Kilometerstand mit dem Tacho abgleichen

**Sprachen und Darstellung**
- Deutsch, Englisch, Spanisch, Französisch, Italienisch, Portugiesisch, Polnisch, Türkisch
- Hell, dunkel oder wie das System

**Daten sichern**
- Über das Menü oben rechts alles als CSV-Datei herunterladen, sie lässt sich mit Excel öffnen
- Eine CSV-Datei hochladen, um Daten zu übernehmen oder wiederherzustellen

Für die Diagramme gilt: Ein Monat wird angetippt, und sein Wert erscheint über dem Diagramm. Der Zeitraum lässt sich auf 12 Monate, 24 Monate oder alles stellen.

## Installieren und bauen

Voraussetzungen: [Android Studio](https://developer.android.com/studio) und ein Android-Gerät ab Android 8.0 oder ein Emulator.

1. Repository klonen und den Ordner in Android Studio mit **File → Open** öffnen.
2. Warten, bis die Gradle-Synchronisierung fertig ist.
3. Gerät auswählen und auf **▶ Run** klicken.

Am Handy muss dafür einmalig das USB- oder WLAN-Debugging in den Entwickleroptionen eingeschaltet sein.

Eine installierbare APK-Datei entsteht mit **Build → Build App Bundle(s) / APK(s) → Build APK(s)** unter `app/build/outputs/apk/`.

Ohne Android Studio, über die Kommandozeile (braucht Java 17 oder neuer und das Android SDK):

```
./gradlew assembleDebug        # APK bauen (unter Windows: gradlew.bat assembleDebug)
./gradlew testDebugUnitTest    # Tests für CSV-Export und -Import
```

## Backups

Ein Backup kann als CSV Datei erstellt werden. Diese enthält alle Autos, Fahrten, Tankbelege und Wartungskosten. Die Datei ist UTF-8-kodiert, Trennzeichen ist das Semikolon, Dezimalzeichen das Komma, damit ein deutsches Excel sie direkt öffnet.

```
Typ;Auto;Monat;Kilometer;Verbrauch (l/100 km);Datum;Liter;Kosten (€);Preis (€/l);Art;Notiz
Fahrt;Golf;2026-09;1250;6,4;;;;;;
Tanken;Golf;;;;2026-09-16;40,9;72,36;1,769;;
Wartung;Golf;;;;2026-03-12;;289,90;;Inspektion;Ölwechsel
```

- **Typ** ist `Fahrt`, `Tanken` oder `Wartung`. Das Format ist in jeder App-Sprache gleich.
- **Art** bei Wartung: `Inspektion`, `Reparatur`, `Reifen`, `HU` oder `Sonstiges`.
- **Auto** wird über den Namen zugeordnet. Unbekannte Namen werden beim Hochladen als neue Autos angelegt.
- Monate gehen als `2026-09` oder `09.2026`, Daten als `2026-09-16` oder `16.09.2026`.
- **Preis (€/l)** dient nur zur Information und wird beim Hochladen ignoriert.

Beim Hochladen werden die Daten ergänzt, nicht ersetzt. Gibt es für ein Auto einen Monat schon, wird er überschrieben. Ein Tankbeleg mit gleichem Auto, Datum, Litern und Betrag wird übersprungen. Zeilen, die sich nicht lesen lassen, werden mit Zeilennummer gemeldet.

## Projektaufbau

```
app/src/main/java/de/kilometerbuch/
├── MainActivity.kt
├── data/            Datenmodell, Speicherung (JSON im App-Speicher), CSV
└── ui/
    ├── App.kt       Grundgerüst: Autowahl, Seitenmenü, Navigation, Dialoge
    ├── TripsScreen.kt / FuelScreen.kt   die beiden Seiten
    ├── MonthChart.kt                    Balken- und Liniendiagramm
    └── MainViewModel.kt                 Zustand und Speichern
```

Die App ist in Kotlin mit Jetpack Compose (Material 3) geschrieben und kommt ohne Datenbank oder Diagramm-Bibliothek aus. Die Diagramme sind selbst gezeichnet, die Daten liegen als JSON-Dateien im privaten Speicher der App. Über das automatische Android-Backup werden sie mitgesichert.

## Lizenz

[Apache License 2.0](LICENSE)
