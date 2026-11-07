# Arcanic

Визуальный клиент для Minecraft (Fabric 1.21.11) и его лаунчер.

| Папка | Что там | Сборка |
|---|---|---|
| [`visuals/`](visuals) | Arcanic Visuals - мод: модули, косметика, Lua, студии | `cd visuals` и `./gradlew build` (Java 25) |
| [`launcher/`](launcher) | Arcanic Launcher - лаунчер на C++ для Windows | `launcher\native\build.bat` (Visual Studio Build Tools с C++) |

Подробности - в README каждой папки. Обе части под GNU GPL v3; у лаунчера есть дополнительное условие об указании
авторов (`launcher/NOTICE`).

Сервер (аккаунты, Premium, синхронизация) не входит в репозиторий: Premium и админка проверяются только на нём.
