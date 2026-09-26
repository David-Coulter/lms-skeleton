package edu.lms.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * The 3-layer stack Phase 1 asks for: entity -> repository -> service -> controller.
 * Base path must match the gateway route Sanjay assigns (see application.yml).
 */
@RestController
@RequestMapping("/api/__RESOURCE__")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<Item> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> get(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Item> create(@Valid @RequestBody Item item) {
        Item saved = service.create(item);
        return ResponseEntity.created(URI.create("/api/__RESOURCE__/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Item> update(@PathVariable Long id, @Valid @RequestBody Item item) {
        return service.update(id, item)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Handy for the profile demo: shows which profile the running container picked up.
     */
    @GetMapping("/whoami")
    public String whoami(@org.springframework.beans.factory.annotation.Value("${lms.environment-label}") String label) {
        return label;
    }
}
