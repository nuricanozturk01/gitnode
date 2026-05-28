package com.nuricanozturk.originhub.issue.api;

import java.util.Optional;
import java.util.UUID;

public interface IssueQueryService {

  Optional<IssueData> findById(UUID id);
}
