package dev.citeweave.video.local;

import static dev.citeweave.video.local.LocalVideoProbe.Failure.VIDEO_STORAGE_UNAVAILABLE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 在服务端专用目录发布不可变媒体对象，用户文件名只作为元数据。 */
@Component
class LocalVideoStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalVideoStorage.class);
    private final Path root;

    LocalVideoStorage(@Value("${citeweave.video.storage-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    Path stagingDirectory() {
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
            return root;
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    /** 先同步暂存数据，再用同卷硬链接发布。重复内容只能指向完整且相同的对象。 */
    String publish(Path staged) {
        try {
            String digest = sha256(staged);
            Path target = object(digest);
            try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.createLink(target, staged);
            } catch (FileAlreadyExistsException duplicate) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.mismatch(staged, target) != -1) {
                    throw unavailable();
                }
            }
            return digest;
        } catch (IOException | UnsupportedOperationException failure) {
            LOGGER.warn("Local video object publication failed ({})", failure.getClass().getSimpleName());
            throw unavailable();
        }
    }

    void requireObject(String digest, long size) {
        verifiedObject(digest, size);
    }

    /** 仅供后台任务访问经数据库确认的存储对象。 */
    Path verifiedObject(String digest, long size) {
        try {
            Path target = object(digest);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != size) throw unavailable();
            return target;
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private Path object(String digest) {
        return root.resolve(digest + ".media");
    }

    private static String sha256(Path staged) throws IOException {
        try (var stream = new DigestInputStream(Files.newInputStream(staged), MessageDigest.getInstance("SHA-256"))) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(stream.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static LocalVideoProbe.ProbeException unavailable() {
        return new LocalVideoProbe.ProbeException(VIDEO_STORAGE_UNAVAILABLE);
    }
}
