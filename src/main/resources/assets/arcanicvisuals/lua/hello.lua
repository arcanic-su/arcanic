-- Пример скрипта Arcanic Lua: свой HUD-элемент и свой модуль в меню.
-- Всё API описано в API.md рядом со скриптами (Tools -> Lua -> Документация).

-- HUD-элемент: включи его в HUD-разделе меню и подвинь в HUD-редакторе
local card
card = hud.element({
  id = "hello_card",
  name = "Hello Card",
  description = "Пример из hello.lua: FPS, скорость и здоровье",
  size = {170, 62},
  render = function(editing, w, h)
    local tint = card:get("tint")
    draw.round(0, 0, w, h, 10, color.rgb(12, 12, 18, 200))
    draw.outline(0, 0, w, h, 10, color.alpha(tint, 120))
    draw.text("Arcanic Lua", 12, 10, tint, 13, "semibold")
    draw.text(client.fps() .. " FPS", 12, 30, color.text(), 12)
    if card:get("speed") then
      draw.text(math.floor(player.speed() * 10 + 0.5) / 10 .. " бл/с", 80, 30, color.text(), 12)
    end
    local hp = player.health() / math.max(1, player.maxHealth())
    draw.round(12, h - 12, w - 24, 4, 2, color.rgb(255, 255, 255, 40))
    draw.round(12, h - 12, math.floor((w - 24) * hp), 4, 2, color.mix(color.hex("#F43F5E"), tint, hp))
  end,
})
card:bool("speed", "Показывать скорость", true)
card:color("tint", "Цвет", color.accent())

-- модуль: уведомление, когда твой ник пишут в чате
local mentions = module.create({
  id = "hello_mentions",
  name = "Упоминания",
  description = "Пример из hello.lua: уведомление и звук, когда тебя упоминают в чате",
  category = "utility",
})
mentions:bool("sound", "Звук", true)

events.on("chat", function(text)
  if mentions:enabled() and text:find(player.name(), 1, true) then
    client.notify("Тебя упомянули в чате")
    if mentions:get("sound") then sound.play("minecraft:entity.experience_orb.pickup") end
  end
end)
