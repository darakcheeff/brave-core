# Релизы Brave Android Custom (`com.tech.brave`)

Отдельная ветка для стабильных релизов APK (отдельно от технических чекпоинтов кэша сборки в `android-arm64-checkpoint`).

## Актуальный релиз: `v1.0.0`

- **Файл:** [`Brave-arm64-Release-v1.0.0.apk`](https://github.com/darakcheeff/brave-core/releases/tag/v1.0.0) (`arm64-v8a`, `com.tech.brave`)
- **База:** `brave-core` (`custom-android` @ `830899733`, сборка CI `45834944163`)
- **Основные возможности:**
  - Работа без GMS / SafetyNet (совместимо с Huawei и AOSP без Google Play Services).
  - Встроенный Tor (`New Tor Tab`, живой прогресс подключения на стартовой странице, изоляция трафика).
  - Brave Leo AI (`Automatic`) без баннеров, согласий и зависаний.
  - Поддержка расширений (`brave://extensions/`) и автоматический Desktop User-Agent для Chrome Web Store.
  - Встроенный Stealth MCP Server (`http://127.0.0.1:8765/mcp`) с Bearer-токеном и 7 инструментами.
  - Поиск по умолчанию: Google (обычные/приватные вкладки), Brave Search (Tor); удалён Brave Origin; отключены нижняя панель, отчёты аналитики и автосохранение паролей/карт/адресов.
