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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.v419.Bedrock_v419;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.network.BedrockProtocol;
import org.geysermc.connector.registry.type.ItemMapping;
import org.geysermc.connector.registry.type.ItemMappings;
import org.geysermc.connector.registry.type.PaletteItem;
import org.geysermc.connector.utils.FileUtils;
import org.geysermc.connector.utils.LanguageUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ItemsRegistryLoader implements RegistryLoader<Object, Map<Integer, ItemMappings>> {
    private static final Object2IntMap<String> PALETTE_VERSIONS = new Object2IntOpenHashMap<String>() {
        {
            put("1_16_100", Bedrock_v419.V419_CODEC.getProtocolVersion());
        }
    };

    private static final ImmutableList<String> STORED_ITEMS = ImmutableList.<String>builder()
            .add("minecraft:barrier")
            .add("minecraft:bamboo")
            .add("minecraft:egg")
            .add("minecraft:gold_ingot")
            .add("minecraft:shield")
            .add("minecraft:milk_bucket")
            .add("minecraft:wheat")
            .add("minecraft:writable_book")
            .build();

    @Override
    public Map<Integer, ItemMappings> load(Object input) {
        Int2ObjectMap<ItemMappings> registeredMappings = new Int2ObjectOpenHashMap<>();

        InputStream stream = FileUtils.getResource("mappings/items.json");
        JsonNode items;
        try {
            items = GeyserConnector.JSON_MAPPER.readTree(stream);
        } catch (Exception e) {
            throw new AssertionError(LanguageUtils.getLocaleStringLog("geyser.toolbox.fail.runtime_java"), e);
        }

        /* Load item palette */
        for (Object2IntMap.Entry<String> palette : PALETTE_VERSIONS.object2IntEntrySet()) {
            stream = FileUtils.getResource(String.format("bedrock/runtime_item_states.%s.json", palette.getKey()));

            TypeReference<List<PaletteItem>> paletteEntriesType = new TypeReference<List<PaletteItem>>() { };

            ItemMappings.ItemMappingsBuilder mappings = ItemMappings.builder();
            // Used to get the Bedrock namespaced ID (in instances where there are small differences)
            Object2IntMap<String> bedrockIdToIdentifier = new Object2IntOpenHashMap<>();

            List<PaletteItem> paletteEntries;
            try {
                paletteEntries = GeyserConnector.JSON_MAPPER.readValue(stream, paletteEntriesType);
            } catch (Exception e) {
                throw new AssertionError(LanguageUtils.getLocaleStringLog("geyser.toolbox.fail.runtime_bedrock"), e);
            }

            List<StartGamePacket.ItemEntry> entries = new ObjectArrayList<>();
            for (PaletteItem entry : paletteEntries) {
                entries.add(new StartGamePacket.ItemEntry(entry.getName(), (short) entry.getId()));
                bedrockIdToIdentifier.put(entry.getName(), entry.getId());
            }

            /* Load creative items */
            stream = FileUtils.getResource(String.format("bedrock/creative_items.%s.json", palette.getKey()));

            JsonNode creativeItemEntries;
            try {
                creativeItemEntries = GeyserConnector.JSON_MAPPER.readTree(stream).get("items");
            } catch (Exception e) {
                throw new AssertionError(LanguageUtils.getLocaleStringLog("geyser.toolbox.fail.creative"), e);
            }

            List<String> itemNames = new ObjectArrayList<>();
            Int2ObjectMap<ItemMapping> itemMappings = new Int2ObjectOpenHashMap<>();

            int netId = 1;
            List<ItemData> creativeItems = new ObjectArrayList<>();
            for (JsonNode itemNode : creativeItemEntries) {
                ItemData item = getBedrockItemFromJson(itemNode);
                creativeItems.add(ItemData.fromNet(netId++, item.getId(), item.getDamage(), item.getCount(), item.getTag()));
            }

            Map<String, ItemMapping> storedItems = new Object2ObjectOpenHashMap<>();

            IntList boatIds = new IntArrayList();
            IntList bucketIds = new IntArrayList();

            int itemIndex = 0;
            Iterator<Map.Entry<String, JsonNode>> iterator = items.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String bedrockIdentifier = entry.getValue().get("bedrock_identifier").asText();
                int bedrockId = bedrockIdToIdentifier.getInt(bedrockIdentifier);
                if (bedrockId == -1) {
                    throw new RuntimeException("Missing Bedrock ID in mappings!: " + bedrockId);
                }
                JsonNode stackSizeNode = entry.getValue().get("stack_size");
                int stackSize = stackSizeNode == null ? 64 : stackSizeNode.intValue();
                ItemMapping mapping = ItemMapping.builder()
                        .javaIdentifier(entry.getKey())
                        .javaId(itemIndex)
                        .bedrockIdentifier(bedrockIdentifier)
                        .bedrockId(bedrockId)
                        .bedrockData(entry.getValue().get("bedrock_data").intValue())
                        .block(entry.getValue().get("is_block").booleanValue())
                        .stackSize(stackSize)
                        .toolTier(entry.getValue().has("tool_tier") ? entry.getValue().get("tool_tier").textValue() : null)
                        .toolType(entry.getValue().has("tool_type") ? entry.getValue().get("tool_type").textValue() : null)
                        .build();

                itemMappings.put(itemIndex, mapping);
                if (STORED_ITEMS.contains(entry.getKey())) {
                    storedItems.put(entry.getKey(), mapping);
                }

                if (entry.getKey().contains("boat")) {
                    boatIds.add(bedrockId);
                } else if (entry.getKey().contains("bucket") && !entry.getKey().contains("milk")) {
                    bucketIds.add(bedrockId);
                }

                itemNames.add(entry.getKey());
                itemIndex++;
            }

            // Add the loadstone compass since it doesn't exist on java but we need it for item conversion
            itemMappings.put(itemIndex, ItemMapping.builder()
                    .javaIdentifier("minecraft:lodestone_compass")
                    .javaId(itemIndex)
                    .bedrockIdentifier("minecraft:lodestone_compass")
                    .bedrockId(bedrockIdToIdentifier.getInt("minecraft:lodestone_compass"))
                    .bedrockData(0)
                    .block(false)
                    .stackSize(1)
                    .toolType(null)
                    .toolTier(null)
                    .build());

            registeredMappings.put(palette.getIntValue(), mappings
                    .creativeItems(creativeItems.toArray(new ItemData[0]))
                    .itemEntries(entries)
                    .items(itemMappings)
                    .boatIds(boatIds)
                    .bucketIds(bucketIds)
                    .storedItems(storedItems)
                    .itemNames(itemNames)
                    .build());
        }
        return registeredMappings;
    }

    /**
     * Gets a Bedrock {@link ItemData} from a {@link JsonNode}
     * @param itemNode the JSON node that contains ProxyPass-compatible Bedrock item data
     * @return the bedrock item from the given json
     */
    private static ItemData getBedrockItemFromJson(JsonNode itemNode) {
        int count = 1;
        short damage = 0;
        NbtMap tag = null;
        if (itemNode.has("damage")) {
            damage = itemNode.get("damage").numberValue().shortValue();
        }
        if (itemNode.has("count")) {
            count = itemNode.get("count").asInt();
        }
        if (itemNode.has("nbt_b64")) {
            byte[] bytes = Base64.getDecoder().decode(itemNode.get("nbt_b64").asText());
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                tag = (NbtMap) NbtUtils.createReaderLE(bais).readTag();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ItemData.of(itemNode.get("id").asInt(), damage, count, tag);
    }
}
