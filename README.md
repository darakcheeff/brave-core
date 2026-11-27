![Brave Browser](./docs/images/brave.svg)

# Brave Android Custom (`com.tech.brave`)

Кастомная сборка **Brave для Android (`arm64`)** на базе `brave-core`, очищенная от Google Mobile Services (GMS), телеметрии, навязчивого онбординга и лишних модулей (Wallet, Rewards, Ads, VPN, Brave Origin).

> **Релизы APK:** готовые релизные сборки публикуются в отдельной ветке [`releases`](https://github.com/darakcheeff/brave-core/tree/releases) и разделе [Releases (`v1.0.0`)](https://github.com/darakcheeff/brave-core/releases/tag/v1.0.0), отдельно от технических чекпоинтов кэша сборки.

---

## Основные возможности

### 1. Работа без Google Mobile Services (GMS-free)
- Удалена зависимость от Google Play Services / SafetyNet (`SafetyNet` не вызывается, отсутствуют системные уведомления GMS).
- Полностью совместимо с устройствами **Huawei (без GMS)** и чистыми AOSP-прошивками.

### 2. Встроенный Tor (`.onion`)
- Встроенный бинарник `libtor.so` (`extractNativeLibs="true"`).
- Отдельный пункт меню **New Tor Tab** в меню браузера.
- Стартовая страница Tor в стиле десктопного Brave с живым отображением прогресса подключения (`0% → 100%`), строкой поиска Brave и информационной карточкой.
- Трафик обычных вкладок идёт напрямую, трафик Tor-вкладок — через сеть Tor.

### 3. Brave Leo AI без баннеров и онбординга
- Модель `Automatic` работает без предварительного принятия соглашений.
- Удалены обучающие диалоги, предупреждения о хранении истории и премиум-баннеры.

### 4. Поддержка расширений Chrome Web Store
- Работает страница `brave://extensions/`, автообновление расширений отключено по умолчанию.
- Для `chromewebstore.google.com` и `chrome.google.com` автоматически включается десктопный User-Agent (кнопка **«Установить»** доступна сразу без ручного переключения режима).

### 5. Встроенный Stealth MCP Server
- Локальный HTTP/JSON-RPC 2.0 сервер на `http://127.0.0.1:8765` с авторизацией по `Authorization: Bearer <token>` (без использования CDP/DevTools).
- Эндпоинты: `GET /health`, `GET /status`, `POST /mcp`.
- Методы: `initialize`, `notifications/initialized`, `tools/list`, `tools/call`.
- Поддерживаемые инструменты:
  - `browser_navigate` (`url`, `new_tab`) — переход по URL в активной или новой вкладке;
  - `browser_get_dom` (`mode`: `markdown | text | dom`, `selector`) — извлечение содержимого страницы;
  - `browser_click` (`selector`, `x`, `y`) — клик по CSS-селектору или координатам;
  - `browser_type` (`text`, `selector`) — ввод текста с синтетическими событиями;
  - `browser_screenshot` (`quality`) — скриншот страницы в base64 JPEG;
  - `browser_tab_management` (`action`: `list | switch | new | close`, `tab_id`, `url`) — управление вкладками;
  - `browser_evaluate_script` (`script`) — выполнение JavaScript в контексте страницы.

### 6. Чистые настройки по умолчанию
- Поисковая система по умолчанию: **Google** (для обычных и приватных вкладок), **Brave Search** (для вкладок Tor).
- Удалён пункт **Brave Origin**.
- Отключены по умолчанию: нижняя панель инструментов, отчёты аналитики/конфиденциальности, автосохранение паролей, адресов и банковских карт.

---

## Подключение к MCP через ADB

```bash
adb forward tcp:8765 tcp:8765
curl -X POST http://127.0.0.1:8765/mcp \
  -H "Authorization: Bearer <ТОКЕН_ИЗ_НАСТРОЕК_MCP>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

---

## Сборка (CI)

Сборка `arm64` APK выполняется через GitHub Actions ([`.github/workflows/build-android.yml`](.github/workflows/build-android.yml)) на ветке `custom-android`.
