package dev.gitnode.os.branch.api;

import dev.gitnode.os.branch.services.BranchProtectionService;
import java.nio.file.Path;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProtectedBranchReceiveHook implements PreReceiveHook {
  private final BranchProtectionService protection;

  @Value("${gitnode.git.repo-root}")
  private String repoRoot;

  @Override
  @SuppressWarnings("checkstyle:IllegalCatch") // fail closed: any check failure rejects the push
  public void onPreReceive(final ReceivePack pack, final Collection<ReceiveCommand> commands) {
    final var root = Path.of(this.repoRoot).toAbsolutePath().normalize();
    final var directory = pack.getRepository().getDirectory().toPath().toAbsolutePath().normalize();
    if (!directory.startsWith(root)) {
      commands.forEach(
          command ->
              command.setResult(
                  ReceiveCommand.Result.REJECTED_OTHER_REASON, "Cannot verify branch protection"));
      return;
    }
    final var relative = root.relativize(directory);
    if (relative.getNameCount() != 2 || !relative.getName(1).toString().endsWith(".git")) {
      commands.forEach(
          command ->
              command.setResult(
                  ReceiveCommand.Result.REJECTED_OTHER_REASON, "Cannot verify branch protection"));
      return;
    }
    final var owner = relative.getName(0).toString();
    final var directoryName = relative.getName(1).toString();
    final var repo = directoryName.substring(0, directoryName.length() - 4);
    for (final var command : commands) {
      if (!command.getRefName().startsWith(Constants.R_HEADS)) continue;
      try {
        this.protection.assertDirectWriteAllowed(
            owner, repo, command.getRefName().substring(Constants.R_HEADS.length()));
      } catch (RuntimeException rejected) {
        command.setResult(
            ReceiveCommand.Result.REJECTED_OTHER_REASON,
            "Protected branch or protection check unavailable");
      }
    }
  }
}
