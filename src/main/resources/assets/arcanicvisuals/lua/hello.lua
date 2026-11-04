-- Пример скрипта Arcanic Lua.
-- Скрипты лежат в папке config/arcanicvisuals/scripts. Включай их в меню: Tools -> Lua.
-- Список всех функций есть на вкладке «API» той же страницы.

local shown = storage.get("shown") or 0
storage.set("shown", shown + 1)
print("Привет! Этот скрипт запускали " .. (shown + 1) .. " раз")

local fps = 0

events.on("tick", function()
  fps = client.fps()
end)

events.on("render", function()
  if not world.loaded() then return end
  local x, y = 16, draw.height() / 2 - 40
  local w, h = 170, 62
  draw.round(x, y, w, h, 10, color.rgb(12, 12, 18, 200))
  draw.outline(x, y, w, h, 10, color.alpha(color.accent(), 120))
  draw.text("Arcanic Lua", x + 12, y + 10, color.accent(), 13, "semibold")
  draw.text(fps .. " FPS", x + 12, y + 30, color.text(), 12)
  draw.text(math.floor(player.speed() * 10 + 0.5) / 10 .. " бл/с", x + 80, y + 30, color.text(), 12)
  local hp = player.health() / math.max(1, player.maxHealth())
  draw.round(x + 12, y + h - 12, w - 24, 4, 2, color.rgb(255, 255, 255, 40))
  draw.round(x + 12, y + h - 12, math.floor((w - 24) * hp), 4, 2, color.mix(color.hex("#F43F5E"), color.accent(), hp))
end)

events.on("chat", function(text)
  if text:find(player.name(), 1, true) then
    client.notify("Тебя упомянули в чате")
  end
end)
