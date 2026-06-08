package dev.gitnode.os.shared.log;

// https://github.com/maricn/logback-slack-appender

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

@Getter
public class SlackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private static final @NonNull String API_URL = "https://slack.com/api/chat.postMessage";
  private static final int DEFAULT_TIMEOUT = 30_000;

  private static final @NonNull Layout<ILoggingEvent> DEFAULT_LAYOUT =
      new LayoutBase<>() {
        @Override
        public @NonNull String doLayout(final @NonNull ILoggingEvent event) {
          return "-- ["
              + event.getLevel()
              + "]"
              + event.getLoggerName()
              + " - "
              + event.getFormattedMessage().replace("\n", "\n\t");
        }
      };

  @Setter private String channel;
  @Setter private @NonNull Boolean colorCoding = false;
  private String iconEmoji;
  @Setter private String iconUrl;
  @Setter private @NonNull Layout<ILoggingEvent> layout = DEFAULT_LAYOUT;
  @Setter private int timeout = DEFAULT_TIMEOUT;
  @Setter private String token;
  @Setter private String username;
  @Setter private String webhookUri;

  public void setIconEmoji(final @NonNull String iconEmojiArg) {
    this.iconEmoji = iconEmojiArg;

    if (!Strings.isNullOrEmpty(this.iconEmoji)
        && this.iconEmoji.startsWith(":")
        && !this.iconEmoji.endsWith(":")) {
      this.iconEmoji += ":";
    }
  }

  @Override
  protected void append(final @NonNull ILoggingEvent evt) {
    try {
      if (this.webhookUri != null && !this.webhookUri.isEmpty()) {
        this.sendMessageWithWebhookUri(evt);
      } else if (this.token != null && !this.token.isEmpty()) {
        this.sendMessageWithToken(evt);
      }
    } catch (final Exception ex) {
      this.addError("Error posting log to Slack.com (" + this.channel + "): " + evt, ex);
    }
  }

  private void addParam(
      final @NonNull StringWriter requestParams,
      final @Nullable String param,
      final String paramKey) {
    if (param != null) {
      requestParams
          .append(paramKey)
          .append("=")
          .append(URLEncoder.encode(param, StandardCharsets.UTF_8))
          .append('&');
    }
  }

  private @NonNull String colorByEvent(final @NonNull ILoggingEvent evt) {
    if (Level.ERROR.equals(evt.getLevel())) {
      return "danger";
    } else if (Level.WARN.equals(evt.getLevel())) {
      return "warning";
    } else if (Level.INFO.equals(evt.getLevel())) {
      return "good";
    }

    return "";
  }

  private void postMessage(
      final @NonNull String uri, final String contentType, final byte @NonNull [] bytes)
      throws IOException {

    final HttpURLConnection conn = (HttpURLConnection) URI.create(uri).toURL().openConnection();
    conn.setConnectTimeout(this.timeout);
    conn.setReadTimeout(this.timeout);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST");
    conn.setFixedLengthStreamingMode(bytes.length);
    conn.setRequestProperty("Content-Type", contentType);

    final OutputStream os = conn.getOutputStream();
    os.write(bytes);

    os.flush();
    os.close();
  }

  private void sendMessageWithToken(final @NonNull ILoggingEvent evt) throws IOException {
    final StringWriter requestParams = new StringWriter();
    requestParams.append("token=").append(this.token).append("&");

    final String[] parts = this.layout.doLayout(evt).split("\n", 2);
    requestParams
        .append("text=")
        .append(URLEncoder.encode(parts[0], StandardCharsets.UTF_8))
        .append('&');

    // Send the lines below the first line as an attachment.
    if (parts.length > 1 && !parts[1].isEmpty()) {
      final Map<String, String> attachment = new HashMap<>();
      attachment.put("text", parts[1]);
      if (this.colorCoding) {
        attachment.put("color", this.colorByEvent(evt));
      }

      final List<Map<String, String>> attachments = List.of(attachment);
      final String json = new ObjectMapper().writeValueAsString(attachments);
      requestParams
          .append("attachments=")
          .append(URLEncoder.encode(json, StandardCharsets.UTF_8))
          .append('&');
    }

    this.addParam(requestParams, this.channel, "channel");
    this.addParam(requestParams, this.username, "username");
    this.addParam(requestParams, this.iconEmoji, "icon_emoji");
    this.addParam(requestParams, this.iconUrl, "icon_url");

    final byte[] bytes = requestParams.toString().getBytes(StandardCharsets.UTF_8);

    this.postMessage(API_URL, "application/x-www-form-urlencoded", bytes);
  }

  private void sendMessageWithWebhookUri(final @NonNull ILoggingEvent evt) throws IOException {
    final String[] parts = this.layout.doLayout(evt).split("\n", 2);

    final Map<String, Object> message = new HashMap<>();
    message.put("channel", this.channel);
    message.put("username", this.username);
    message.put("icon_emoji", this.iconEmoji);
    message.put("icon_url", this.iconUrl);
    message.put("text", parts[0]);

    // Send the lines below the first line as an attachment.
    if (parts.length > 1 && !parts[1].isEmpty()) {
      final Map<String, String> attachment = new HashMap<>();
      attachment.put("text", parts[1]);
      if (this.colorCoding) {
        attachment.put("color", this.colorByEvent(evt));
      }

      message.put("attachments", List.of(attachment));
    }

    final ObjectMapper objectMapper = new ObjectMapper();
    final byte[] bytes = objectMapper.writeValueAsBytes(message);

    this.postMessage(this.webhookUri, "application/json", bytes);
  }
}
