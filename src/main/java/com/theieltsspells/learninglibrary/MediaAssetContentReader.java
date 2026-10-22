package com.theieltsspells.learninglibrary;

import java.util.UUID;

/** Public module API for trusted consumers that need the bytes of a managed image asset. */
public interface MediaAssetContentReader {
    ImageContent readImage(UUID fileId, long maxBytes);

    record ImageContent(String mimeType, byte[] data) {
        public ImageContent {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
