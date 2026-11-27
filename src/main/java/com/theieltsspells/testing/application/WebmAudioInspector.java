package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
class WebmAudioInspector {
    private static final long EBML = 0x1A45DFA3L;
    private static final long DOC_TYPE = 0x4282L;
    private static final long SEGMENT = 0x18538067L;
    private static final long INFO = 0x1549A966L;
    private static final long TIMESTAMP_SCALE = 0x2AD7B1L;
    private static final long DURATION = 0x4489L;
    private static final long TRACKS = 0x1654AE6BL;
    private static final long TRACK_ENTRY = 0xAEL;
    private static final long TRACK_NUMBER = 0xD7L;
    private static final long TRACK_TYPE = 0x83L;
    private static final long CODEC_ID = 0x86L;
    private static final long CLUSTER = 0x1F43B675L;
    private static final long TIMESTAMP = 0xE7L;
    private static final long SIMPLE_BLOCK = 0xA3L;
    private static final long BLOCK_GROUP = 0xA0L;
    private static final long BLOCK = 0xA1L;
    private static final long BLOCK_DURATION = 0x9BL;
    private static final long OPUS_PACKET_MAX_NANOS = 120_000_000L;

    Inspection inspect(MultipartFile file, long maxBytes, Duration maxDuration) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("Vui lòng chọn bản ghi âm");
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("Bản ghi âm vượt quá dung lượng tối đa " + maxBytes / 1_048_576 + " MB");
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType()
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!"audio/webm".equals(mimeType)) {
            throw new BusinessRuleException("Bản ghi âm phải có định dạng WebM/Opus");
        }
        try {
            byte[] bytes = file.getBytes();
            long durationMs = Math.max(1, inspect(bytes).toMillis());
            if (durationMs > maxDuration.toMillis()) {
                throw new BusinessRuleException("Bản ghi âm vượt quá thời lượng tối đa " + maxDuration.toSeconds() + " giây");
            }
            return new Inspection("audio/webm", bytes.length, durationMs);
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể đọc bản ghi âm");
        }
    }

    static Duration inspect(byte[] bytes) {
        try {
            Parser parser = new Parser(bytes);
            return parser.duration();
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Tệp tải lên không phải WebM/Opus hợp lệ");
        }
    }

    record Inspection(String mimeType, long sizeBytes, long durationMs) {}

    private static final class Parser {
        private final byte[] data;
        private long timestampScale = 1_000_000L;
        private double declaredDurationTicks;
        private long audioTrack = -1;
        private boolean opus;
        private boolean videoTrack;
        private long maxBlockStartTicks = -1;
        private long maxBlockEndTicks = -1;

        private Parser(byte[] data) {
            this.data = data;
        }

        private Duration duration() {
            Element header = element(0, data.length);
            if (header.id != EBML || !"webm".equals(textChild(header, DOC_TYPE))) invalid();
            Element segment = nextElement(header.end, data.length, SEGMENT);
            parseSegment(segment);
            if (!opus || videoTrack || audioTrack < 0 || maxBlockStartTicks < 0) invalid();
            double declaredNanos = declaredDurationTicks * timestampScale;
            long blockNanos = Math.max(maxBlockEndTicks * timestampScale,
                    maxBlockStartTicks * timestampScale + OPUS_PACKET_MAX_NANOS);
            long nanos = Math.max((long) declaredNanos, blockNanos);
            if (nanos <= 0) invalid();
            return Duration.ofNanos(nanos);
        }

        private void parseSegment(Element segment) {
            int offset = segment.start;
            while (offset < segment.end) {
                Element child = element(offset, segment.end);
                if (child.id == INFO) parseInfo(child);
                else if (child.id == TRACKS) parseTracks(child);
                else if (child.id == CLUSTER) parseCluster(child);
                offset = advance(child, segment.end);
            }
        }

        private void parseInfo(Element info) {
            int offset = info.start;
            while (offset < info.end) {
                Element child = element(offset, info.end);
                if (child.id == TIMESTAMP_SCALE) timestampScale = unsigned(child);
                else if (child.id == DURATION) declaredDurationTicks = floating(child);
                offset = advance(child, info.end);
            }
            if (timestampScale <= 0 || !Double.isFinite(declaredDurationTicks)) invalid();
        }

        private void parseTracks(Element tracks) {
            int offset = tracks.start;
            while (offset < tracks.end) {
                Element child = element(offset, tracks.end);
                if (child.id == TRACK_ENTRY) parseTrack(child);
                offset = advance(child, tracks.end);
            }
        }

        private void parseTrack(Element track) {
            long number = -1;
            long type = -1;
            String codec = "";
            int offset = track.start;
            while (offset < track.end) {
                Element child = element(offset, track.end);
                if (child.id == TRACK_NUMBER) number = unsigned(child);
                else if (child.id == TRACK_TYPE) type = unsigned(child);
                else if (child.id == CODEC_ID) codec = text(child);
                offset = advance(child, track.end);
            }
            if (type == 1) videoTrack = true;
            if (type == 2 && "A_OPUS".equals(codec)) {
                audioTrack = number;
                opus = true;
            }
        }

        private void parseCluster(Element cluster) {
            long clusterTimestamp = 0;
            int offset = cluster.start;
            while (offset < cluster.end) {
                Element child = element(offset, cluster.end);
                if (child.id == CLUSTER) {
                    clusterTimestamp = 0;
                    offset = child.start;
                    continue;
                }
                if (child.id == TIMESTAMP) clusterTimestamp = unsigned(child);
                else if (child.id == SIMPLE_BLOCK) recordBlock(child, clusterTimestamp, -1);
                else if (child.id == BLOCK_GROUP) parseBlockGroup(child, clusterTimestamp);
                offset = advance(child, cluster.end);
            }
        }

        private void parseBlockGroup(Element group, long clusterTimestamp) {
            Element block = null;
            long blockDuration = -1;
            int offset = group.start;
            while (offset < group.end) {
                Element child = element(offset, group.end);
                if (child.id == BLOCK) block = child;
                else if (child.id == BLOCK_DURATION) blockDuration = unsigned(child);
                offset = advance(child, group.end);
            }
            if (block != null) recordBlock(block, clusterTimestamp, blockDuration);
        }

        private void recordBlock(Element block, long clusterTimestamp, long blockDuration) {
            Vint track = vint(block.start, block.end, false);
            if (track.value != audioTrack || track.next + 2 > block.end) return;
            int relative = (short) (((data[track.next] & 0xff) << 8) | (data[track.next + 1] & 0xff));
            long start = clusterTimestamp + relative;
            if (start < 0) return;
            maxBlockStartTicks = Math.max(maxBlockStartTicks, start);
            if (blockDuration >= 0) maxBlockEndTicks = Math.max(maxBlockEndTicks, start + blockDuration);
        }

        private Element nextElement(int offset, int limit, long expectedId) {
            while (offset < limit) {
                Element value = element(offset, limit);
                if (value.id == expectedId) return value;
                offset = advance(value, limit);
            }
            invalid();
            return null;
        }

        private String textChild(Element parent, long id) {
            int offset = parent.start;
            while (offset < parent.end) {
                Element child = element(offset, parent.end);
                if (child.id == id) return text(child);
                offset = advance(child, parent.end);
            }
            return "";
        }

        private Element element(int offset, int limit) {
            Vint id = vint(offset, limit, true);
            Vint size = vint(id.next, limit, false);
            int end = size.unknown ? limit : Math.toIntExact(Math.min((long) limit, size.next + size.value));
            if (!size.unknown && end - size.next != size.value) invalid();
            return new Element(id.value, size.next, end, size.unknown);
        }

        private Vint vint(int offset, int limit, boolean keepMarker) {
            if (offset >= limit) invalid();
            int first = data[offset] & 0xff;
            int mask = 0x80;
            int length = 1;
            while (length <= 8 && (first & mask) == 0) {
                mask >>>= 1;
                length++;
            }
            if (length > 8 || offset + length > limit) invalid();
            long value = keepMarker ? first : first & (mask - 1);
            for (int i = 1; i < length; i++) value = (value << 8) | (data[offset + i] & 0xffL);
            long unknownValue = (1L << (7 * length)) - 1;
            return new Vint(value, offset + length, !keepMarker && value == unknownValue);
        }

        private int advance(Element element, int limit) {
            if (element.unknown) return limit;
            if (element.end <= element.start) invalid();
            return element.end;
        }

        private long unsigned(Element element) {
            int length = element.end - element.start;
            if (length < 1 || length > 8) invalid();
            long value = 0;
            for (int i = element.start; i < element.end; i++) value = (value << 8) | (data[i] & 0xffL);
            return value;
        }

        private double floating(Element element) {
            int length = element.end - element.start;
            long bits = unsigned(element);
            if (length == 4) return Float.intBitsToFloat((int) bits);
            if (length == 8) return Double.longBitsToDouble(bits);
            invalid();
            return 0;
        }

        private String text(Element element) {
            return new String(data, element.start, element.end - element.start, StandardCharsets.US_ASCII);
        }

        private void invalid() {
            throw new IllegalArgumentException("Invalid WebM");
        }

        private record Element(long id, int start, int end, boolean unknown) {}
        private record Vint(long value, int next, boolean unknown) {}
    }
}
