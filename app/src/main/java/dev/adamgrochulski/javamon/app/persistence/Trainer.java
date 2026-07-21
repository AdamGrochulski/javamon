package dev.adamgrochulski.javamon.app.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trainers")
public class Trainer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String username;

    /** BCrypt. null tylko dla gościa - pilnuje tego CHECK w bazie. */
    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    @Column(nullable = false)
    private boolean guest;

    @Column(nullable = false)
    private int rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Soft delete - null oznacza konto aktywne. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Trainer() {
        // wymagany przez JPA
    }

    private Trainer(String username, String passwordHash, boolean guest) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.guest = guest;
        this.rating = 1000;
        this.createdAt = Instant.now();
    }

    public static Trainer registered(String username, String passwordHash) {
        return new Trainer(username, passwordHash, false);
    }

    public static Trainer guest(String username) {
        return new Trainer(username, null, true);
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isGuest() { return guest; }
    public int getRating() { return rating; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isActive() { return deletedAt == null; }

    public void applyRating(int newRating) { this.rating = newRating; }
    public void markDeleted() { this.deletedAt = Instant.now(); }
}
