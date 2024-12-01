/*
 * Copyright (c) EngineHub and Contributors
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
package com.viaversion.eduard.listener;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.eduard.ViaEduardBot;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import com.viaversion.eduard.util.AthenaHelper;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

// Loosely based on https://github.com/EngineHub/EngineHub-Bot/blob/master/src/main/java/org/enginehub/discord/module/errorHelper/ErrorHelper.java (see above copyright header)
public class ErrorHelper extends ListenerAdapter {

    private static final int MAX_IMAGE_BYTES = 1024 * 1024 * 5;
    private static final int MAX_FILE_BYTES = 1024 * 1024 * 5;
    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("OCR Task %d").build());
    private final List<ErrorEntry> errorMessages = new ArrayList<>();
    private final String tessDataPath = Path.of("tessdata").toAbsolutePath().toString();
    private final boolean enableImageScanning;
    private final ViaEduardBot bot;
    private final AthenaHelper athena = new AthenaHelper();

    public ErrorHelper(final ViaEduardBot bot, final JsonObject object) {
        this.bot = bot;

        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            final JsonObject errorObject = entry.getValue().getAsJsonObject();
            final ErrorEntry errorEntry = new ErrorEntry(
                entry.getKey(),
                errorObject.getAsJsonArray("match-text").asList().stream().map(JsonElement::getAsString).collect(Collectors.toList()),
                errorObject.getAsJsonPrimitive("error-message").getAsString(),
                errorObject.getAsJsonPrimitive("confidence").getAsInt(),
                errorObject.getAsJsonArray("from").asList().stream().map(element -> ErrorContainer.valueOf(element.getAsString().toUpperCase(Locale.ROOT))).collect(Collectors.toList())
            );
            errorMessages.add(errorEntry);
        }

        enableImageScanning = object.getAsJsonPrimitive("enable-image-scanning").getAsBoolean();
    }

    @Override
    public void onMessageReceived(@Nonnull final MessageReceivedEvent event) {
        if (event.isWebhookMessage()) {
            return;
        }

        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) {
            return;
        }

        final Message message = event.getMessage();
        final boolean sendDebug = message.isFromGuild();
        final Set<ErrorEntry> triggered = new HashSet<>();
        if (!handle(message, message.getContentRaw(), ErrorContainer.TEXT, sendDebug, triggered)) {
            return;
        }

        for (final Message.Attachment attachment : message.getAttachments()) {
            final String fileName = attachment.getFileName();
            if (fileName.endsWith(".txt") || fileName.endsWith(".log")) {
                if (attachment.getSize() > MAX_FILE_BYTES) {
                    continue;
                }

                final String content;
                try (final InputStream in = new BufferedInputStream(attachment.getProxy().download().get())) {
                    content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (final IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    continue;
                }

                try {
                    athena.createOutput(event, null, athena.sendRequest(content, "raw"));
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                if (!handle(message, content, ErrorContainer.FILE, sendDebug, triggered)) {
                    return;
                }
            } else if (enableImageScanning && attachment.isImage()) {
                if (attachment.getSize() > MAX_IMAGE_BYTES) {
                    continue;
                }

                executorService.execute(() -> readImage(attachment, message, sendDebug, triggered));
            }
        }
    }

    private void readImage(final Message.Attachment attachment, final Message message, final boolean sendDebug, final Set<ErrorEntry> alreadyTriggered) {
        final BufferedImage image;
        try (final InputStream is = new BufferedInputStream(attachment.getProxy().download().get())) {
            image = ImageIO.read(is);
        } catch (final Exception e) {
            System.err.println("Error while reading image: " + attachment.getUrl());
            e.printStackTrace();
            return;
        }

        if (image == null) {
            System.err.println("Got null when reading image: " + attachment.getUrl());
            return;
        }

        final String text;
        try {
            final Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            text = tesseract.doOCR(image);
        } catch (final TesseractException e) {
            System.err.println("Error while doing OCR on image: " + attachment.getUrl());
            e.printStackTrace();
            return;
        } finally {
            image.flush();
        }

        if (message.getAuthor().getIdLong() == 259465250716254210L && message.getChannel() instanceof PrivateChannel) {
            message.getChannel().sendMessage("[OCR Debug] " + text).queue();
        }

        handle(message, text, ErrorContainer.IMAGE, sendDebug, alreadyTriggered);
    }

    private boolean handle(
        final Message message,
        final String messageContent,
        final ErrorContainer errorContainer,
        final boolean sendDebug,
        final Set<ErrorEntry> alreadyTriggered
    ) {
        final String cleanMessage = cleanString(messageContent);
        for (final ErrorEntry entry : errorMessages) {
            if (alreadyTriggered.contains(entry)) {
                // Don't trigger the same message twice
                continue;
            }

            final Result result = entry.test(cleanMessage, errorContainer);
            if (!result.triggered()) {
                continue;
            }

            if (bot.getNonSupportChannelIds().contains(message.getChannelIdLong())) {
                bot.sendSupportChannelRedirect(message.getChannel(), message.getAuthor());
                return false;
            }

            alreadyTriggered.add(entry);

            final String response = entry.response();
            System.out.println(response);
            message.getChannel().sendMessage(response + " " + message.getAuthor().getAsMention()).queue();

            if (sendDebug) {
                final String debugMessage = "Triggered " + entry.name() + " on " + message.getJumpUrl() + " (required confidence: " + entry.requiredConfidence() + ")"
                    + "\nPartial confidence: " + result.highestPartialRatio()
                    + "\nWeighted confidence: " + result.highestWeightedRatio();
                bot.getGuild().getChannelById(TextChannel.class, bot.getBotChannelId()).sendMessage(debugMessage).queue();
            }
        }
        return true;
    }

    private static String cleanString(final String string) {
        return string.toLowerCase(Locale.ROOT)
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .replace("\t", "")
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"");
    }

    private enum ErrorContainer {
        FILE,
        TEXT,
        IMAGE
    }

    private record ErrorEntry(String name, List<String> triggers, String response,
                              int requiredConfidence, EnumSet<ErrorContainer> containers) {
        private static final double MIN_LENGTH = 0.8D;

        ErrorEntry(final String name, final List<String> triggers, final String response, final int requiredConfidence, final Collection<ErrorContainer> containers) {
            this(
                name,
                triggers.stream().map(ErrorHelper::cleanString).collect(Collectors.toList()),
                response,
                requiredConfidence,
                containers.stream().collect(Collectors.toCollection(() -> EnumSet.noneOf(ErrorContainer.class)))
            );
        }

        Result test(final String error, final ErrorContainer errorContainer) {
            if (!containers.contains(errorContainer)) {
                return Result.NONE;
            }

            int highestPartialRatio = 0;
            int highestWeightedRatio = 0;
            for (final String cleanedTrigger : triggers) {
                if (error.length() < cleanedTrigger.length() * MIN_LENGTH) {
                    return Result.NONE;
                }

                final int partialRatio = FuzzySearch.partialRatio(cleanedTrigger, error);
                final int weightedRatio = FuzzySearch.weightedRatio(cleanedTrigger, error);
                if (partialRatio < requiredConfidence || weightedRatio < requiredConfidence) {
                    return Result.NONE;
                }

                highestPartialRatio = Math.max(highestPartialRatio, partialRatio);
                highestWeightedRatio = Math.max(highestWeightedRatio, weightedRatio);
            }
            return new Result(true, highestPartialRatio, highestWeightedRatio);
        }
    }

    private record Result(boolean triggered, int highestPartialRatio, int highestWeightedRatio) {
        static final Result NONE = new Result(false, 0, 0);
    }
}