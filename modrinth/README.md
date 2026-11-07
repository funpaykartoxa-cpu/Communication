# Публикация на Modrinth

Комплект материалов для страницы проекта.

## Файлы

| Файл | Назначение |
|---|---|
| `icon.png` | Иконка проекта, 512×512. |
| `description.md` | Полное описание проекта для страницы Modrinth. |
| `project.json` | JSON для `POST /v2/project`; тело описания уже встроено. |
| `version.json` | JSON для `POST /v2/version`; changelog уже встроен. |
| `changelog.md` | Changelog в читаемом виде. |
| `gallery/` | Шесть изображений 1920×1080 и порядок загрузки. |

## Что заполнить перед публикацией

1. **Лицензия.** Выбрана **LGPL-3.0-only**: `license_id` в `project.json` — `LGPL-3.0`, в `fabric.mod.json` — `LGPL-3.0-only`. Полный текст лежит в корневом `LICENSE`.
2. **ID проекта.** После создания проекта замените пустой `project_id` в `version.json` на выданный Modrinth.
3. **Файлы версии.** Загрузите `communication-1.1.0.jar` и `communication-1.1.0-sources.jar` из `build/libs`.
4. **Иконка.** Загружается отдельным запросом `PATCH /v2/project/{id}/icon` (multipart), не через `project.json`.
5. **Галерея.** Изображения загружаются отдельными запросами; названия и порядок — в `gallery/README.md`.

## Проверки

- В публичных текстах нет упоминаний сторонних клиентов и старых названий.
- Описание и changelog — на русском языке.
- Категории: `social`, `utility`, `gui`, `multiplayer`.
- Лицензия: `LGPL-3.0` (Modrinth), `LGPL-3.0-only` (SPDX).
- Зависимость: Fabric API (`P7dR8mSH`), тип `required`.
- Поддерживаемые версии: Minecraft `1.21.4`, загрузчик `fabric`.
- `client_side: required`, `server_side: unsupported`.

## Пример запроса

```bash
curl -X POST https://api.modrinth.com/v2/project \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "Content-Type: application/json" \
  --data @modrinth/project.json
```

Публикация выполняется только после подтверждения владельца проекта.
