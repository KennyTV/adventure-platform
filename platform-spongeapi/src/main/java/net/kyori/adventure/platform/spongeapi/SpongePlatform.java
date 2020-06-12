/*
 * This file is part of text-extras, licensed under the MIT License.
 *
 * Copyright (c) 2018 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.spongeapi;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.impl.AdventurePlatformImpl;
import net.kyori.adventure.platform.impl.Handler;
import net.kyori.adventure.platform.impl.HandlerCollection;
import net.kyori.adventure.platform.impl.Knobs;
import net.kyori.adventure.platform.viaversion.ViaVersionHandlers;
import net.kyori.adventure.util.NameMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.effect.Viewer;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.channel.MessageReceiver;
import us.myles.ViaVersion.api.platform.ViaPlatform;

import static java.util.Objects.requireNonNull;

public class SpongePlatform extends AdventurePlatformImpl {
  
  static { // init
    Knobs.logger(new Slf4jLogHandler());
  }

  /* package */ static <K, S extends CatalogType> S sponge(final @NonNull Class<S> spongeType, final @NonNull K value, final @NonNull NameMap<K> elements)  {
    return Sponge.getRegistry().getType(spongeType, elements.name(requireNonNull(value, "value")))
      .orElseThrow(() -> new IllegalArgumentException("Value " + value + " could not be found in Sponge type " + spongeType));
  }

  /* package */ static <K, S extends CatalogType> K adventure(final @NonNull S sponge, final @NonNull NameMap<K> values) {
    return values.value(requireNonNull(sponge, "sponge").getId())
      .orElseThrow(() -> new IllegalArgumentException("Sponge CatalogType value " + sponge + " could not be converted to its Adventure equivalent"));
  }

  /* package */ static <S extends CatalogType> S sponge(final @NonNull Class<S> spongeType, final @NonNull Key identifier) {
    return Sponge.getRegistry().getType(spongeType, requireNonNull(identifier, "Identifier must be non-null").asString())
      .orElseThrow(() -> new IllegalArgumentException("Value for Key " + identifier + " could not be found in Sponge type " + spongeType));
  }
  
  private final HandlerCollection<MessageReceiver, Handler.Chat<MessageReceiver, ?>> chat;
  private final HandlerCollection<MessageReceiver, Handler.ActionBar<MessageReceiver, ?>> actionBar;
  private final HandlerCollection<Viewer, Handler.Titles<Viewer>> title;
  private final HandlerCollection<Player, Handler.BossBars<Player>> bossBar;
  private final HandlerCollection<Viewer, Handler.PlaySound<Viewer>> sound;

  public SpongePlatform() { 
    final SpongeViaProvider via = new SpongeViaProvider();
    this.chat = new HandlerCollection<>(new ViaVersionHandlers.Chat<>(via), new SpongeHandlers.Chat());
    this.actionBar = new HandlerCollection<>(new ViaVersionHandlers.ActionBar<>(via), new SpongeHandlers.ActionBar());
    this.title = new HandlerCollection<>(new ViaVersionHandlers.Titles<>(via), new SpongeHandlers.Titles());
    this.bossBar = new HandlerCollection<>(new ViaVersionHandlers.BossBars<>(via), new SpongeBossBarListener());
    this.sound = new HandlerCollection<>(new SpongeHandlers.PlaySound()); // don't include via since we don't target versions below 1.9
    
    final PluginContainer instance = () -> "adventure";
    Sponge.getEventManager().registerListeners(instance, this);
    
    add(new SpongeSenderAudience<>(Sponge.getServer().getConsole(), this.chat, this.actionBar, null, null, null));
  }
  
  @Listener
  public void join(final ClientConnectionEvent.@NonNull Join event) {
    this.add(new SpongePlayerAudience(event.getTargetEntity(), this.chat, this.actionBar, this.title, this.bossBar, this.sound));
  }
  
  @Listener
  public void quit(final ClientConnectionEvent.@NonNull Disconnect event) {
   this.remove(event.getTargetEntity().getUniqueId());
  }
  
  /* package */ static class SpongeViaProvider implements ViaVersionHandlers.ViaAPIProvider<Object> { // too many interfaces :(
    
    private volatile ViaPlatform<?> platform = null;

    @Override
    public boolean isAvailable() {
      return Sponge.getPluginManager().isLoaded("viaversion");
    }

    @Override
    public ViaPlatform<?> platform() {
      if(!isAvailable()) {
        return null;
      }
      ViaPlatform<?> platform = this.platform;
      if(platform == null) {
        final PluginContainer container = Sponge.getPluginManager().getPlugin("viaversion").orElse(null);
        if(container == null) return null;
        this.platform = platform = (ViaPlatform<?>) container.getInstance().orElse(null);
      }
      return platform;
    }

    @Override
    public @Nullable UUID id(final Object viewer) {
      if(!(viewer instanceof Player)) return null;
      
      return ((Player) viewer).getUniqueId();
    }
  }
}
