/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2025 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.data.item;

import com.google.common.cache.CacheBuilder;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.data.item.ItemHasher;
import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import com.viaversion.viaversion.api.minecraft.item.HashedItem;
import com.viaversion.viaversion.api.minecraft.item.HashedStructuredItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.rewriter.item.ItemDataComponentConverter;
import com.viaversion.viaversion.rewriter.item.ItemDataComponentConverter.RegistryAccess;
import com.viaversion.viaversion.util.SerializerVersion;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.lenni0451.mcstructs.converter.impl.v1_21_5.HashConverter_v1_21_5;
import net.lenni0451.mcstructs.itemcomponents.ItemComponentRegistry;
import net.lenni0451.mcstructs.itemcomponents.impl.v1_21_5.ItemComponents_v1_21_5;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ItemHasherBase implements ItemHasher {

    public static final int UNKNOWN_HASH = Integer.MIN_VALUE;
    private final Map<Integer, HashedItem> hashes = CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(1024).<Integer, HashedItem>build().asMap();
    private final List<String> enchantments = new ArrayList<>();
    private final ItemDataComponentConverter itemComponentConverter;
    protected final UserConnection connection;
    private boolean processingClientboundInventoryPacket;

    public ItemHasherBase(final UserConnection connection, final SerializerVersion serializerVersion, final SerializerVersion mappedSerializerVersion, final MappingData mappingData) {
        final RegistryAccess registryAccess = RegistryAccess.of(
            this.enchantments,
            ItemComponentRegistry.V1_21_5.getRegistries(), // Unused outside of file deserialization
            mappingData
        );
        this.itemComponentConverter = new ItemDataComponentConverter(serializerVersion, mappedSerializerVersion, registryAccess);
        this.connection = connection;
    }

    @Override
    public void setEnchantments(final List<String> enchantments) {
        this.enchantments.clear();
        this.enchantments.addAll(enchantments);
    }

    /**
     * Returns the hashed item for the given item. Not all data may be successfully hashed.
     *
     * @param item the item to hash
     * @param mapped whether the item is mapped to the client version
     * @return the hashed item
     */
    public HashedItem toHashedItem(final Item item, final boolean mapped) {
        return toHashedItem(this.itemComponentConverter, item, mapped);
    }

    public static HashedItem toHashedItem(final ItemDataComponentConverter converter, final Item item, final boolean mapped) {
        final HashedItem hashedItem = new HashedStructuredItem(item.identifier(), item.amount());
        for (final StructuredData<?> data : item.dataContainer().data().values()) {
            if (data.isEmpty()) {
                hashedItem.removedDataIds().add(data.id());
                continue;
            }

            final ItemDataComponentConverter.Result<?> result = converter.viaToMcStructs(data, mapped);
            final int hash = result != null ? hash(result) : UNKNOWN_HASH;
            hashedItem.dataHashesById().put(data.id(), hash);
        }
        return hashedItem;
    }

    /**
     * Tracks the data for the given data container, storing the original hashes via the now transformed data hashes.
     *
     * @param customData custom_data tag
     * @param originalHashedItem the original (pre-transformed) hashed item
     */
    public void trackOriginalHashedItem(final CompoundTag customData, final HashedItem originalHashedItem) {
        // Store them via the custom_data hash, which includes the original data hashes.
        // Not perfect (as opposed to storing it by the full hashed item), but good enough. In the rare occasion there is a collision, we ignore it without issues.
        final int customDataHash = hashTag(customData);
        this.hashes.put(customDataHash, originalHashedItem);
    }

    /**
     * Returns a copy of the original hashed item for a given custom_data hash.
     *
     * @param customDataHash custom_data hash
     * @param clientItem the hashed item from the client that the original should be retrieved for
     * @return the original hashed item, or null if not found
     */
    public @Nullable HashedItem originalHashedItem(final int customDataHash, final HashedItem clientItem) {
        HashedItem originalItem = this.hashes.get(customDataHash);
        if (originalItem == null) {
            return null;
        }

        originalItem = originalItem.copy();

        // Take hashes for data we couldn't calculate the hash of from the client item, more likely than not it's the same
        for (final Int2IntMap.Entry entry : originalItem.dataHashesById().int2IntEntrySet()) {
            final int typeId = entry.getIntKey();
            if (entry.getIntValue() == UNKNOWN_HASH && clientItem.dataHashesById().containsKey(typeId)) {
                final int clientProvidedHash = clientItem.dataHashesById().get(typeId);
                originalItem.dataHashesById().put(typeId, clientProvidedHash);
            }
        }
        return originalItem;
    }

    @Override
    public boolean isProcessingClientboundInventoryPacket() {
        return processingClientboundInventoryPacket;
    }

    @Override
    public void setProcessingClientboundInventoryPacket(final boolean processingClientboundInventoryPacket) {
        this.processingClientboundInventoryPacket = processingClientboundInventoryPacket;
    }

    private static <T> int hash(final ItemDataComponentConverter.Result<T> result) {
        return result.type().getCodec().serialize(HashConverter_v1_21_5.CRC32C, result.value()).getOrThrow().asInt();
    }

    private int hashTag(final CompoundTag tag) {
        return ItemComponents_v1_21_5.V1_21_5.CUSTOM_DATA.getCodec().serialize(HashConverter_v1_21_5.CRC32C, tag).getOrThrow().asInt();
    }
}
