package com.theieltsspells.testing.application;

import java.util.UUID;

record SpeakingTestPublishedEvent(UUID testVersionId, String builderContent) {}
