# Streaming TV

Eine **Android-TV-App** (IPTV-Player) für Sender, die du von deinem Anbieter
("Betreiber") beziehst. Unterstützt zwei Verbindungsarten:

- **Stalker / MAG Portal** – Portal-URL **+ MAC-Adresse**. Genau das
  „MAC + IP"-Modell: Die App identifiziert sich gegenüber dem Portal mit einer
  MAC-Adresse, die du deinem Betreiber zur Freischaltung gibst.
- **M3U Playlist** – einfache `.m3u` / `.m3u8`-Playlist-URL.

Gebaut mit Kotlin, Leanback (Android-TV-UI) und Media3/ExoPlayer.

## Funktionen

- 📺 Leanback-Oberfläche mit Senderkategorien und Logos, optimiert für die Fernbedienung
- 🔑 **MAC-Adresse generieren, anzeigen und kopieren** – mit MAG-Präfix `00:1A:79`,
  damit der Betreiber die Linie freischalten kann
- 🛰️ Stalker-Protokoll: Handshake, Profil, Genres, Senderliste und
  Stream-Auflösung über `create_link`
- 📄 M3U-Parser mit `group-title`-, `tvg-logo`- und `tvg-name`-Unterstützung
- ▶️ HLS/Live-Wiedergabe via ExoPlayer, inkl. Picture-in-Picture

## Einrichtung in der App

1. App starten → Einstellungen öffnen sich automatisch beim ersten Start
   (oder über die Such-/Aktions-Schaltfläche oben).
2. Anbieter-Typ wählen:
   - **Stalker / MAG Portal**: Portal-URL eingeben (z. B. `http://server:port/c/`).
     Eine MAC wird automatisch generiert – du kannst sie auch neu erzeugen.
     **Diese MAC dem Betreiber geben**, damit deine Linie aktiviert wird.
   - **M3U Playlist**: Playlist-URL eingeben.
3. Speichern. Die Sender werden geladen; einen Sender auswählen zum Abspielen.

## Build

Voraussetzungen: JDK 17, Android SDK (Platform 34, Build-Tools 34).

```bash
# local.properties mit sdk.dir auf dein Android SDK setzen, dann:
./gradlew assembleDebug
```

Das APK liegt anschließend unter
`app/build/outputs/apk/debug/app-debug.apk`.

Installation auf einem Android-TV-Gerät / Emulator:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Projektstruktur

```
app/src/main/java/com/streamingtv/player/
├── data/
│   ├── Models.kt              # Category, Channel, SourceType
│   ├── Prefs.kt              # Konfiguration + MAC-Generierung
│   ├── PlaylistRepository.kt # Vereint Stalker- und M3U-Quellen
│   ├── stalker/StalkerClient.kt  # MAG/Stalker-Portal-Protokoll
│   └── m3u/M3uParser.kt      # M3U-Playlist-Parser
└── ui/
    ├── MainActivity.kt / MainFragment.kt  # Leanback-Browse
    ├── CardPresenter.kt
    ├── settings/SettingsActivity.kt       # Anbieter + MAC-Verwaltung
    └── playback/PlaybackActivity.kt       # ExoPlayer-Wiedergabe
```

## Hinweis

Dieser Player stellt nur die Software bereit. Inhalte/Zugangsdaten (Portal-URL,
freigeschaltete MAC bzw. Playlist) stammen von deinem Anbieter. Stelle sicher,
dass du die Inhalte rechtmäßig nutzt.
