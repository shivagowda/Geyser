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

package org.geysermc.connector.registry.type;

import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Builder;
import lombok.Value;
import org.geysermc.connector.GeyserConnector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Builder
@Value
public class ItemMappings {
    private static final List<String> JAVA_ONLY_ITEMS = Arrays.asList("minecraft:spectral_arrow", "minecraft:debug_stick",
            "minecraft:knowledge_book", "minecraft:tipped_arrow", "minecraft:furnace_minecart");

    Map<String, ItemMapping> cachedJavaMappings = new WeakHashMap<>();
    Map<Integer, ItemMapping> cachedBedrockMappings = new WeakHashMap<>();

    Int2ObjectMap<ItemMapping> items;

    ItemData[] creativeItems;
    List<StartGamePacket.ItemEntry> itemEntries;

    Map<String, ItemMapping> storedItems;
    List<String> itemNames;

    IntList bucketIds;
    IntList boatIds;

    /**
     * Gets an {@link ItemMapping} from the given Minecraft: Java Edition
     * block state identifier.
     *
     * @param javaIdentifier the block state identifier
     * @return an item entry from the given java edition identifier
     */
    public ItemMapping getMapping(String javaIdentifier) {
        return this.cachedJavaMappings.computeIfAbsent(javaIdentifier, key -> {
            for (ItemMapping mapping : this.items.values()) {
                if (mapping.getJavaIdentifier().equals(key)) {
                    return mapping;
                }
            }
            return null;
        });
    }

    /**
     * Gets an {@link ItemMapping} from the given {@link ItemData}.
     *
     * @param data the item data
     * @return an item entry from the given item data
     */
    public ItemMapping getMapping(ItemData data) {
        return this.cachedBedrockMappings.computeIfAbsent(data.getId(), key -> {
            for (ItemMapping itemMapping : this.items.values()) {
                if (itemMapping.getBedrockId() == data.getId() && (itemMapping.getBedrockData() == data.getDamage() ||
                        // Make exceptions for potions and tipped arrows, whose damage values can vary
                        (itemMapping.getJavaIdentifier().endsWith("potion") || itemMapping.getJavaIdentifier().equals("minecraft:arrow")))) {
                    if (!JAVA_ONLY_ITEMS.contains(itemMapping.getJavaIdentifier())) {
                        // From a Bedrock item data, we aren't getting one of these items
                        return itemMapping;
                    }
                }
            }

            // This will hide the message when the player clicks with an empty hand
            if (data.getId() != 0 && data.getDamage() != 0) {
                GeyserConnector.getInstance().getLogger().debug("Missing mapping for bedrock item " + data.getId() + ":" + data.getDamage());
            }
            return ItemMapping.AIR;
        });
    }

    public ItemMapping getStored(String identifier) {
        return this.storedItems.get(identifier);
    }
}
