package dev.gitnode.os.shared.registries.repsy.dtos;


import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class RepsyRestResponse<T> {
  private final @NonNull String msgId;
  @Getter
  private final @NonNull ResponseType type;
  @Getter private @Nullable T data;
  @Getter private String errorCode;
  @Getter private String text;

  public RepsyRestResponse(final @NonNull String msgId, final @NonNull ResponseType type) {
    this.msgId = msgId;
    this.type = type;
  }

  public void setData(final @Nullable T data) {
    this.data = data;
  }

  public void setErrorCode(final @NonNull String errorCode) {
    this.errorCode = errorCode;
  }

  public void setText(final @NonNull String text) {
    this.text = text;
  }

  public @NonNull String getMsgId() {
    return this.msgId;
  }
}
