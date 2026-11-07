/*
 * This file is part of Arcanic Visuals (https://github.com/arcanic-su/arcanic)
 *
 * Copyright (c) 2026 Arcanic
 *
 * Arcanic Visuals is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Arcanic Visuals is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Arcanic Visuals. If not, see <https://www.gnu.org/licenses/>.
 */
package net.arcanic.visuals.render;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/** Compiles a pipeline from the shader files packed in our jar instead of the game's shader manager. */
public final class EmbeddedShaders {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*#moj_import\\s*<([^:>]+):([^>]+)>\\s*$", Pattern.MULTILINE);

    private EmbeddedShaders() {}

    public static CompiledRenderPipeline compile(RenderPipeline pipeline) {
        return RenderSystem.getDevice().precompilePipeline(pipeline, EmbeddedShaders::source);
    }

    private static String source(Identifier id, ShaderType type) {
        String ext = type == ShaderType.VERTEX ? ".vsh" : ".fsh";
        String text = read("/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + ext);
        return text == null ? null : inline(text, new HashSet<>());
    }

    private static String inline(String text, Set<String> seen) {
        Matcher m = IMPORT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1) + ":" + m.group(2), body = "";
            if (seen.add(key)) {
                String inc = read("/assets/" + m.group(1) + "/shaders/include/" + m.group(2));
                if (inc != null) body = inline(inc.replaceAll("(?m)^\\s*#version.*$", ""), seen);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(body));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String read(String path) {
        try (InputStream in = EmbeddedShaders.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
