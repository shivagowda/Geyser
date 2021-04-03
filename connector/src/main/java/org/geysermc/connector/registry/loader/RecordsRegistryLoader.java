/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.registry.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.connector.GeyserConnector;

import java.util.Iterator;
import java.util.Map;

public class RecordsRegistryLoader extends EffectRegistryLoader<Map<Integer, SoundEvent>> {

    @Override
    public Map<Integer, SoundEvent> load(String input) {
        this.loadFile(input);

        Iterator<Map.Entry<String, JsonNode>> effectsIterator = this.get(input).fields();
        Int2ObjectMap<SoundEvent> records = new Int2ObjectOpenHashMap<>();
        while (effectsIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = effectsIterator.next();
            JsonNode node = entry.getValue();
            try {
                String type = node.get("type").asText();
                if ("record".equals(type)) {
                    JsonNode recordsNode = entry.getValue().get("records");
                    Iterator<Map.Entry<String, JsonNode>> recordsIterator = recordsNode.fields();
                    while (recordsIterator.hasNext()) {
                        Map.Entry<String, JsonNode> recordEntry = recordsIterator.next();
                        records.put(Integer.parseInt(recordEntry.getKey()), SoundEvent.valueOf(recordEntry.getValue().asText()));
                    }
                }
            } catch (Exception e) {
                GeyserConnector.getInstance().getLogger().warning("Failed to map sound effect " + entry.getKey() + " : " + e.toString());
            }
        }
        return records;
    }
}
