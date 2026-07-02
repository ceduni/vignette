package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;
import org.titiplex.service.storage.FileStorageService;
import org.titiplex.service.storage.MediaContent;
import org.titiplex.service.storage.StoredFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ThumbnailService {

    private final ThumbnailRepository repo;
    private final AudioRepository audioRepo;
    private final FileStorageService storage;

    public ThumbnailService(ThumbnailRepository repo, AudioRepository audioRepo, FileStorageService storage) {
        this.repo = repo;
        this.audioRepo = audioRepo;
        this.storage = storage;
    }

    @Transactional
    public Thumbnail save(String title, MultipartFile image, Scenario scenario, User user) throws Exception {
        StoredFile stored = storage.storeThumbnail(image, scenario.getId());

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setTitle((title == null || title.isBlank()) ? "Image " + (repo.maxIdx(scenario.getId()) + 1) : title.trim());
        thumbnail.setScenarioId(scenario.getId());
        thumbnail.setScenario(scenario);
        thumbnail.setAuthorId(user.getId());
        thumbnail.setAuthor(user);
        thumbnail.setIdx(repo.maxIdx(scenario.getId()) + 1);
        thumbnail.setContentType(stored.contentType());
        thumbnail.setImageSha256(stored.sha256());
        thumbnail.setStoragePath(stored.relativePath());
        thumbnail.setSizeBytes(stored.sizeBytes());
        thumbnail.setOriginalFilename(stored.originalFilename());

        thumbnail.setGridColumnSpan(1);
        thumbnail.setGridRowSpan(1);

        try (InputStream inputStream = image.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage != null) {
                thumbnail.setImageWidth(bufferedImage.getWidth());
                thumbnail.setImageHeight(bufferedImage.getHeight());
            }
        }

        return repo.save(thumbnail);
    }

    public List<Thumbnail> listByScenarioId(Long scenarioId) {
        return repo.findByScenarioIdOrderByIdxAsc(scenarioId);
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
        for (Audio audio : audioRepo.findByThumbnailIdOrderByIdxAsc(id)) {
            storage.deleteQuietly(audio.getStoragePath());
            audioRepo.delete(audio);
        }
        storage.deleteQuietly(thumbnail.getStoragePath());
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
}