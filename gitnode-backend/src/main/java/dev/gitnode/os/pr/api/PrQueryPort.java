package dev.gitnode.os.pr.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface PrQueryPort {

  Optional<PrData> findById(UUID prId);

  Optional<PrData> findByRepoIdAndNumber(UUID repoId, int number);

  List<PrData> findOpenByRepoId(UUID repoId);
}
