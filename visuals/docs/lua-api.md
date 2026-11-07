# Arcanic Lua API

Скрипты лежат в `config/arcanicvisuals/scripts/*.lua` и включаются в меню: **Tools → Lua**.
Картинки для `draw.image` кладутся в `scripts/images`, данные `storage` и модулей сохраняются в `scripts/data`,
всё, что пишет `print`, и ошибки попадают в `scripts/log.txt`.

Каждый скрипт работает в своей песочнице: из Lua есть `string`, `table`, `math`, `bit32`, без `io`, `os`, `load`,
`require` и доступа к Java. Скрипт, который завис (бесконечный цикл, бесконечная рекурсия), останавливается с ошибкой,
игра продолжает работать.

Скрипты не управляют игроком: не двигают, не поворачивают камеру, не бьют и не кликают в инвентаре. Других игроков и
мобов видно только тех, кого видишь сам (не сквозь стены).

Цвета — числа ARGB, как `0xFF8B5CF6`; удобнее брать их из `color.*`.
Координаты на экране — в пикселях окна; `draw.scale()` = высота окна / 1080, на него удобно умножать размеры.

---

## События

```lua
events.on("tick", function() end)              -- 20 раз в секунду
events.on("render", function() end)            -- каждый кадр, здесь рисуют на экране
events.on("chat", function(text) end)          -- пришло сообщение; return false — скрыть его
events.on("send", function(text) end)          -- ты отправляешь сообщение или /команду; return false — отменить
events.on("key", function(name) end)           -- нажата клавиша или кнопка мыши в игре ("r", "f6", "mouse4")
events.on("join", function(server) end)        -- зашёл на сервер
events.on("leave", function() end)             -- вышел с сервера
events.on("screen", function(name) end)        -- открылось или закрылось меню ("" — меню нет)
events.on("damage", function(amount, hp) end)  -- тебя ранили
events.on("death", function() end)
events.on("attack", function(entity) end)      -- ты ударил сущность
events.on("unload", function() end)            -- скрипт выключают
events.off(name, fn)
events.emit("my_event", ...)                   -- своё событие для всех скриптов (events.on("my_event", ...))
```

## Свои модули

Модуль появляется в меню рядом с нашими: с переключателем, биндом, настройками и поиском.
Его состояние и настройки сохраняются.

```lua
local fly = module.create({
  id = "speed_lines",              -- a-z, 0-9, _ ; в меню будет lua_speed_lines
  name = "Speed Lines",
  description = "Линии скорости по краям экрана",
  category = "visuals",            -- visuals, combat, players, effects, hands, world, utility, hud
  onEnable = function() end,
  onDisable = function() end,
})
fly:bool("glow", "Свечение", true)
fly:slider("amount", "Количество", 40, 10, 100, 1, "")   -- key, name, default, min, max, step, unit
fly:mode("style", "Стиль", "Тонкие", {"Тонкие", "Толстые"})
fly:color("tint", "Цвет", color.accent())
fly:text("label", "Подпись", "Arcanic")
fly:key("boost", "Клавиша", "lshift")
fly:button("reset", "Сброс", "Сбросить", function() end)

fly:enabled()            fly:setEnabled(true)
fly:get("amount")        fly:set("amount", 60)
```

## Свои HUD-элементы

Элемент двигается и масштабируется в HUD-редакторе, как наши. Рисуй в его координатах (0, 0 — левый верхний угол,
размер как в 1080p), масштаб применится сам.

```lua
local card = hud.element({
  id = "clock",
  name = "Часы",
  size = {140, 40},
  x = 0.02, y = 0.3,               -- место по умолчанию, доли экрана
  render = function(editing, w, h)
    draw.round(0, 0, w, h, 10, color.rgb(10, 10, 14, 200))
    draw.text(client.clock("HH:mm:ss"), w / 2, 12, color.text(), 16, "bold", "center")
  end,
})
card:size(160, 40)
card:bool("seconds", "Секунды", true)    -- у элемента такие же настройки, как у модуля
hud.elements()                           -- id всех HUD-элементов
hud.position("watermark")                -- x, y долями экрана
```

## Рисование

Работает в `render` и внутри HUD-элементов.

```lua
draw.width(), draw.height(), draw.scale()
draw.rect(x, y, w, h, color)
draw.round(x, y, w, h, radius, color)
draw.outline(x, y, w, h, radius, color)
draw.circle(cx, cy, r, color)            draw.ring(cx, cy, r, color)
draw.line(x1, y1, x2, y2, thickness, color)
draw.gradient(x, y, w, h, color1, color2, vertical)
draw.shadow(x, y, w, h, spread, color)
draw.text(text, x, y, color, size, weight, align, shadow)   -- weight: medium|semibold|bold; align: left|center|right
draw.textWidth(text, size, weight)
draw.icon(name, x, y, size, color)       -- иконки меню: "w.heart", "w.sword", "w.star"...
draw.item("minecraft:totem_of_undying", x, y, size)
draw.head("PlayerName", x, y, size)
draw.image("logo.png", x, y, w, h, color)   -- из scripts/images
draw.clip(x, y, w, h) ... draw.unclip()
draw.push() draw.translate(x, y) draw.zoom(k) draw.rotate(deg) ... draw.pop()
draw.alpha(0.5)                          -- прозрачность всего, что рисуется дальше в этом кадре
```

## Цвета

```lua
color.rgb(r, g, b, a)   color.hsv(h, s, v, a)   color.hex("#8B5CF6")
color.unpack(c)  --> r, g, b, a
color.accent()  color.accent2()  color.text()  color.muted()  color.background()   -- из темы
color.mix(c1, c2, t)   color.alpha(c, a)   color.rainbow(speed, offset, saturation)
```

## Игрок

```lua
player.name() player.uuid() player.health() player.maxHealth() player.absorption()
player.food() player.saturation() player.armor() player.level() player.xp()
player.pos() --> x, y, z      player.velocity() --> vx, vy, vz (блоков/с)    player.speed()
player.yaw() player.pitch() player.onGround() player.sneaking() player.sprinting() player.swimming()
player.inWater() player.flying() player.burning() player.hurtTime() player.fallDistance()
player.attackCharge()   -- 0..1
player.gamemode() player.dimension() player.biome()
player.held() player.offhand()   -- id предмета
player.armorItems()  --> {{id, name, count, damage, maxDamage}, ...}
player.effects()     --> {{id, name, level, seconds}, ...}
player.lookingAt()   --> блок {type="block", id, x, y, z, face} или сущность, или nil
```

## Мир

```lua
world.loaded() world.time() world.day() world.weather()
world.block(x, y, z)     -- id блока, только рядом с тобой (до 8 блоков)
world.players()          -- игроки в поле зрения
world.entities(radius)   -- сущности в поле зрения, радиус до 64
world.online()           --> {{name, ping}, ...} из таба
world.toScreen(x, y, z)  --> sx, sy, depth или nil (точка за спиной)
```

Сущность: `{type="entity", id, uuid, kind, name, x, y, z, height, distance, isPlayer, friend, sneaking,
health, maxHealth, armor, held, hurtTime}`.

## Инвентарь (только чтение)

```lua
inventory.selected()   inventory.get(slot)   inventory.items()   inventory.count("minecraft:ender_pearl")
```

## Клиент, чат, клавиши, звук

```lua
client.fps() client.ping() client.ms() client.clock("HH:mm") client.server() client.screen()
client.version() client.minecraft() client.english()
client.notify(text, positive)      client.title(title, subtitle, fadeIn, stay, fadeOut)
client.actionbar(text)             client.openMenu()   client.closeScreen()

chat.send("привет")  chat.send("/home")   -- не чаще раза в 1,2 с
chat.print(text, "gold")                  -- видишь только ты; цвета Minecraft: gold, aqua, red...
chat.clear()

keys.down("lshift")  keys.bind("g", function() end)  keys.name(code)
mouse.x() mouse.y() mouse.down(1)

sound.play("minecraft:entity.experience_orb.pickup", volume, pitch)
sound.ui("click")
```

## Модули Arcanic

```lua
modules.list()                      -- id всех модулей
modules.info("hitboxes")            --> {id, name, description, category, enabled, bind, script}
modules.enabled(id)  modules.set(id, true)  modules.toggle(id)
modules.settings(id)                --> {{key, name, type, value, min, max, step, options}, ...}
modules.get(id, key)  modules.setValue(id, key, value)  modules.press(id, buttonKey)
```

## Тема и конфиги

```lua
theme.custom() theme.setCustom(true)
theme.get("accent") theme.set("accent", color.hex("#22D3EE"))
-- цвета: panel, sidebar, card, field, text, muted, accent, accent2
theme.presets() theme.preset("Неон") theme.code() theme.load(code)

config.list() config.active() config.load(name)
```

## Таймеры, данные, JSON, HTTP

```lua
local id = timer.after(1000, fn)   timer.every(500, fn)   timer.cancel(id)   timer.now()

storage.get(key, default)  storage.set(key, value)  storage.keys()  storage.clear()
-- хранит строки, числа, true/false и таблицы; до 1 МБ на скрипт

json.encode(table)   json.decode(text)

http.get(url, function(status, body) end, headers)
http.post(url, body, function(status, body) end, headers)
-- только https, без локальных адресов, до 4 запросов сразу и 60 в минуту, ответ до 1 МБ
```

## Прочее

`print(...)` пишет в `scripts/log.txt`. `SCRIPT` — имя файла скрипта.
