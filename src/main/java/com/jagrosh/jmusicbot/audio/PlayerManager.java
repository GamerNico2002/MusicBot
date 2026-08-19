/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.dunctebot.sourcemanagers.DuncteBotSources;
import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.AndroidMusic;
import dev.lavalink.youtube.clients.Ios;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class PlayerManager extends DefaultAudioPlayerManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerManager.class);
    private static final String TOKEN_FILE = "youtube_token.txt";

    private final Bot bot;
    
    public PlayerManager(Bot bot)
    {
        this.bot = bot;
    }

    private String readTokenFromFile()
    {
        try
        {
            Path path = Paths.get(TOKEN_FILE);
            if(Files.exists(path))
            {
                String token = Files.readString(path).trim();
                if(!token.isEmpty())
                    return token;
            }
        }
        catch(IOException e)
        {
            LOGGER.warn("Could not read YouTube token file: {}", e.getMessage());
        }
        return null;
    }

    private void saveTokenToFile(String token)
    {
        try
        {
            Files.writeString(Paths.get(TOKEN_FILE), token);
            LOGGER.info("YouTube OAuth refresh token saved to {}", TOKEN_FILE);
        }
        catch(IOException e)
        {
            LOGGER.warn("Could not save YouTube token to file: {}", e.getMessage());
        }
    }
    
    public void init()
    {
        TransformativeAudioSourceManager.createTransforms(bot.getConfig().getTransforms()).forEach(t -> registerSourceManager(t));

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(true, new Client[] {
            new Music(),
            new Web(),
            new WebEmbedded(),
            new AndroidVr(),
            new AndroidMusic(),
            new MWeb(),
            new TvHtml5Simply(),
            new Ios()
        });
        yt.setPlaylistPageCount(bot.getConfig().getMaxYTPlaylistPages());

        String oauth = bot.getConfig().getYoutubeOAuth();
        if(oauth != null && !oauth.isEmpty())
        {
            try
            {
                yt.useOauth2(oauth, true);
            }
            catch(Exception e)
            {
                LOGGER.warn("YouTube OAuth with provided token failed: {}", e.getMessage());
                LOGGER.warn("Continuing without OAuth — some videos may not work.");
            }
        }
        else
        {
            String poToken = bot.getConfig().getYoutubePoToken();
            if(poToken != null && !poToken.isEmpty())
            {
                Web.setPoTokenAndVisitorData(poToken, bot.getConfig().getYoutubeVisitorData());
            }
            else
            {
                String savedToken = readTokenFromFile();
                if(savedToken != null)
                {
                    try
                    {
                        LOGGER.info("Loaded YouTube OAuth refresh token from {}", TOKEN_FILE);
                        yt.useOauth2(savedToken, true);
                    }
                    catch(Exception e)
                    {
                        LOGGER.warn("YouTube OAuth with saved token failed: {}", e.getMessage());
                        LOGGER.warn("The token may be invalid or expired. Delete {} and restart to re-authenticate.", TOKEN_FILE);
                        LOGGER.warn("Continuing without OAuth — some videos may not work.");
                    }
                }
                else
                {
                    LOGGER.info("No YouTube token found. Starting interactive OAuth flow...");
                    LOGGER.info("Follow the instructions in the console to authenticate.");
                    try
                    {
                        yt.useOauth2(null, false);
                        String newToken = yt.getOauth2RefreshToken();
                        if(newToken != null)
                        {
                            saveTokenToFile(newToken);
                        }
                        else
                        {
                            LOGGER.warn("OAuth flow completed but refresh token could not be retrieved.");
                            LOGGER.warn("Check logging level for dev.lavalink.youtube.http.YoutubeOauth2Handler=INFO");
                        }
                    }
                    catch(Exception e)
                    {
                        LOGGER.warn("YouTube OAuth flow failed: {}", e.getMessage());
                        LOGGER.warn("Continuing without OAuth — some videos may not work.");
                    }
                }
            }
        }
        registerSourceManager(yt);

        registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        registerSourceManager(new BandcampAudioSourceManager());
        registerSourceManager(new VimeoAudioSourceManager());
        registerSourceManager(new TwitchStreamAudioSourceManager());
        registerSourceManager(new BeamAudioSourceManager());
        registerSourceManager(new GetyarnAudioSourceManager());
        registerSourceManager(new NicoAudioSourceManager());
        registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));

        AudioSourceManagers.registerLocalSource(this);

        DuncteBotSources.registerAll(this, "en-US");
    }
    
    public Bot getBot()
    {
        return bot;
    }
    
    public boolean hasHandler(Guild guild)
    {
        return guild.getAudioManager().getSendingHandler()!=null;
    }
    
    public AudioHandler setUpHandler(Guild guild)
    {
        AudioHandler handler;
        if(guild.getAudioManager().getSendingHandler()==null)
        {
            AudioPlayer player = createPlayer();
            player.setVolume(bot.getSettingsManager().getSettings(guild).getVolume());
            handler = new AudioHandler(this, guild, player);
            player.addListener(handler);
            guild.getAudioManager().setSendingHandler(handler);
        }
        else
            handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return handler;
    }
}
