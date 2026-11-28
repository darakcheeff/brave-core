# Релизы Brave Android Custom (`com.tech.brave`)

Отдельная ветка для стабильных релизов APK (отдельно от технических чекпоинтов кэша сборки).

## Актуальный релиз: `v1.1.1`

- **Файл:** [`Brave-arm64-Release-v1.1.1.apk`](https://github.com/darakcheeff/brave-core/releases/tag/v1.1.1) (`arm64-v8a`, `com.tech.brave`)
- **База:** `brave-core` (`v1.100.59` / Chromium `158.0.8127.92`, ветка `custom-android` @ `568dc0f55`, сборка CI `46082905825`)
- **Основные возможности:**
  - Обновлённая база Brave `v1.100.59` и Chromium `158.0.8127.92`.
  - Работа без GMS / SafetyNet (совместимо с Huawei и AOSP без Google Play Services).
  - Встроенный Tor (`New Tor Tab`, живой прогресс подключения на стартовой странице, изоляция трафика).
  - Brave Leo AI (`Automatic`) без баннеров, согласий и зависаний.
  - Поддержка расширений (`brave://extensions/`) и автоматический Desktop User-Agent для Chrome Web Store.
  - Встроенный Stealth MCP Server (`http://127.0.0.1:8765/mcp`) с Bearer-токеном и 7 инструментами (нативно внутри браузера, без CDP/WebDriver).
  - Поиск по умолчанию: Google (обычные/приватные вкладки), Brave Search (Tor); удалён Brave Origin; отключены нижняя панель, отчёты аналитики и автосохранение паролей/карт/адресов.

## Предыдущие релизы

- [`v1.0.0`](https://github.com/darakcheeff/brave-core/releases/tag/v1.0.0) — первый релиз (`Brave-arm64-Release-v1.0.0.apk`, сборка CI `45834944163`).
