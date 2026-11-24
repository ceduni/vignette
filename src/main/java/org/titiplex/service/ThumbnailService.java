package org.titiplex.service;

import org.titiplex.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;
import org.titiplex.service.storage.FileStorageService;
import org.titiplex.service.storage.MediaContent;
import org.titiplex.service.storage.StoredFile;


import org.titiplex.util.SvgSanitizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class ThumbnailService {

    private final ThumbnailRepository repo;
    private final AudioRepository audioRepo;
    private final FileStorageService storage;
    private final ScenarioRepository scenarioRepo;

    public ThumbnailService(ThumbnailRepository repo,
                            AudioRepository audioRepo,
                            FileStorageService storage,
                            ScenarioRepository scenarioRepo) {
        this.repo = repo;
        this.audioRepo = audioRepo;
        this.storage = storage;
        this.scenarioRepo = scenarioRepo;
    }

    @Transactional
    public Thumbnail save(String title, MultipartFile image, Scenario scenario, User user) throws Exception {
        return save(title, image, scenario, user, null);
    }

    /** Saves a scene image; {@code vignetteSceneJson} is the Vignette Maker drawing, or null for a plain upload. */
    @Transactional
    public Thumbnail save(String title, MultipartFile image, Scenario scenario, User user,
                          String vignetteSceneJson) throws Exception {
        // Lock the scenario so that concurrent uploads compute max(idx) + 1 one after the other.
        scenarioRepo.lockById(scenario.getId());
        int nextIdx = repo.maxIdx(scenario.getId()) + 1;
        String effectiveTitle = Text.requireMaxLength(
                Text.trimOrDefault(title, "Image " + nextIdx), 255, "Scene title");
        StoredFile stored = storage.storeThumbnail(image, scenario.getId());
        // Storage is content-addressed (path derived from the SHA-256), so a duplicate upload
        // lands on the file the existing scene already uses: nothing to clean up on refusal.
        // The scenario lock above also makes this check safe against concurrent uploads.
        if (repo.existsByScenarioIdAndImageSha256(scenario.getId(), stored.sha256())) {
            // Same bytes but a different extension produce a new path: drop that orphan file.
            if (!repo.existsByStoragePathAndIdNot(stored.relativePath(), -1L)) {
                storage.deleteQuietly(stored.relativePath());
            }
            throw new IllegalStateException("This image is already used in this scenario");
        }

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setTitle(effectiveTitle);
        thumbnail.setScenarioId(scenario.getId());
        thumbnail.setScenario(scenario);
        thumbnail.setAuthorId(user.getId());
        thumbnail.setAuthor(user);
        thumbnail.setIdx(nextIdx);
        thumbnail.setContentType(stored.contentType());
        thumbnail.setImageSha256(stored.sha256());
        thumbnail.setStoragePath(stored.relativePath());
        thumbnail.setSizeBytes(stored.sizeBytes());
        thumbnail.setOriginalFilename(stored.originalFilename());

        thumbnail.setGridColumnSpan(1);
        thumbnail.setGridRowSpan(1);
        thumbnail.setVignetteSceneJson(vignetteSceneJson);
        readDimensions(image, thumbnail);

        return repo.save(thumbnail);
    }

    /**
     * Replaces the image and the drawing of a Vignette Maker scene, keeping its place, title,
     * layout and audios. The previous file is deleted after commit unless another scene uses it.
     */
    @Transactional
    public Thumbnail replaceVignette(Long id, MultipartFile image, String vignetteSceneJson) throws Exception {
        repo.lockById(id);
        Thumbnail thumbnail = getThumbnailById(id);
        String previousPath = thumbnail.getStoragePath();

        StoredFile stored = storage.storeThumbnail(image, thumbnail.getScenarioId());
        if (!stored.sha256().equals(thumbnail.getImageSha256())
                && repo.existsByScenarioIdAndImageSha256(thumbnail.getScenarioId(), stored.sha256())) {
            if (!repo.existsByStoragePathAndIdNot(stored.relativePath(), -1L)) {
                storage.deleteQuietly(stored.relativePath());
            }
            throw new IllegalStateException("This image is already used in this scenario");
        }

        thumbnail.setContentType(stored.contentType());
        thumbnail.setImageSha256(stored.sha256());
        thumbnail.setStoragePath(stored.relativePath());
        thumbnail.setSizeBytes(stored.sizeBytes());
        thumbnail.setOriginalFilename(stored.originalFilename());
        thumbnail.setVignetteSceneJson(vignetteSceneJson);
        thumbnail.setImageWidth(null);
        thumbnail.setImageHeight(null);
        readDimensions(image, thumbnail);
        Thumbnail saved = repo.save(thumbnail);

        if (!previousPath.equals(stored.relativePath())
                && !repo.existsByStoragePathAndIdNot(previousPath, id)) {
            storage.deleteAfterCommit(previousPath);
        }
        return saved;
    }

    private static void readDimensions(MultipartFile image, Thumbnail thumbnail) throws Exception {
        if ("image/svg+xml".equals(thumbnail.getContentType())) {
            SvgSanitizer.Size size = SvgSanitizer.size(image.getBytes());
            if (size != null) {
                thumbnail.setImageWidth(size.width());
                thumbnail.setImageHeight(size.height());
            }
            return;
        }
        try (InputStream inputStream = image.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage != null) {
                thumbnail.setImageWidth(bufferedImage.getWidth());
                thumbnail.setImageHeight(bufferedImage.getHeight());
            }
        }
    }

    public List<Thumbnail> listByScenarioId(Long scenarioId) {
        return repo.findByScenarioIdOrderByIdxAsc(scenarioId);
    }

    @Transactional
    public List<Thumbnail> reorder(Long scenarioId, List<Long> ids) {
        List<Thumbnail> rows = listByScenarioId(scenarioId);
        Map<Long, Thumbnail> byId = rows.stream().collect(Collectors.toMap(Thumbnail::getId, t -> t));
        // Same count and same set of ids: no scene missing, unknown, or listed twice.
        boolean everySceneExactlyOnce = ids != null
                && ids.size() == rows.size()
                && new HashSet<>(ids).equals(byId.keySet());
        if (!everySceneExactlyOnce) {
            throw new IllegalArgumentException("Provide every scene in this vignette exactly once");
        }
        List<Thumbnail> ordered = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Thumbnail row = byId.get(ids.get(i));
            row.setIdx(i + 1);
            ordered.add(row);
        }
        return repo.saveAll(ordered);
    }

    /** Locks the thumbnail row until the end of the current transaction. */
    public void lockThumbnail(Long id) {
        repo.lockById(id);
    }

    public Thumbnail getThumbnailById(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Thumbnail not found"));
    }

    public MediaContent loadContent(Long id) {
        Thumbnail t = getThumbnailById(id);
        return storage.load(t.getStoragePath(), t.getContentType(), t.getImageSha256());
    }

    @Transactional
    public void delete(Long id) {
        Thumbnail thumbnail = getThumbnailById(id);
        boolean imageIsShared = repo.existsByStoragePathAndIdNot(thumbnail.getStoragePath(), id);
        for (Audio audio : audioRepo.findByThumbnailIdOrderByIdxAsc(id)) {
            if (!audioRepo.existsByStoragePathAndIdNot(audio.getStoragePath(), audio.getId())) {
                storage.deleteAfterCommit(audio.getStoragePath());
            }
            audioRepo.delete(audio);
        }
        if (!imageIsShared) {
            storage.deleteAfterCommit(thumbnail.getStoragePath());
        }
        repo.delete(thumbnail);
    }

    @Transactional
    public Thumbnail updateLayout(Long id, UpdateThumbnailLayoutRequest request) {
        Thumbnail thumbnail = getThumbnailById(id);

        if (request.gridColumn() != null && request.gridColumn() < 1) {
            throw new IllegalArgumentException("gridColumn must be >= 1");
        }
        if (request.gridRow() != null && request.gridRow() < 1) {
            throw new IllegalArgumentException("gridRow must be >= 1");
        }
        if (request.gridColumnSpan() != null && request.gridColumnSpan() < 1) {
            throw new IllegalArgumentException("gridColumnSpan must be >= 1");
        }
        if (request.gridRowSpan() != null && request.gridRowSpan() < 1) {
            throw new IllegalArgumentException("gridRowSpan must be >= 1");
        }

        thumbnail.setGridColumn(request.gridColumn());
        thumbnail.setGridRow(request.gridRow());
        thumbnail.setGridColumnSpan(request.gridColumnSpan() == null ? 1 : request.gridColumnSpan());
        thumbnail.setGridRowSpan(request.gridRowSpan() == null ? 1 : request.gridRowSpan());

        return repo.save(thumbnail);
    }

    @Transactional
    public Thumbnail updateTitle(Long id, String title) {
        Thumbnail thumbnail = getThumbnailById(id);
        thumbnail.setTitle(Text.requireMaxLength(
                Text.trimOrDefault(title, "Image " + thumbnail.getIdx()), 255, "Scene title"));
        return repo.save(thumbnail);
    }
}
