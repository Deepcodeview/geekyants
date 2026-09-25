package com.geekyants.assessment.webhook;

import com.geekyants.assessment.github.GithubRepo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "push_events")
public class PushEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id")
    private GithubRepo repository;

    @Column(name = "delivery_id", nullable = false)
    private String deliveryId;

    @Column(name = "ref_name", nullable = false)
    private String refName;

    @Column(name = "before_sha")
    private String beforeSha;

    @Column(name = "after_sha", nullable = false)
    private String afterSha;

    /** Authenticated GitHub account that pushed. Commit author fields are not trusted. */
    @Column(name = "pusher_login")
    private String pusherLogin;

    @Column(nullable = false)
    private boolean forced;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    @Column(name = "head_commit_message")
    private String headCommitMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PushEvent() {
    }

    public PushEvent(GithubRepo repository, String deliveryId, String refName, String beforeSha, String afterSha,
                     String pusherLogin, boolean forced, int commitCount, String headCommitMessage) {
        this.repository = repository;
        this.deliveryId = deliveryId;
        this.refName = refName;
        this.beforeSha = beforeSha;
        this.afterSha = afterSha;
        this.pusherLogin = pusherLogin;
        this.forced = forced;
        this.commitCount = commitCount;
        this.headCommitMessage = headCommitMessage == null ? null
                : headCommitMessage.substring(0, Math.min(headCommitMessage.length(), 2000));
        this.receivedAt = Instant.now();
    }

    public String getRefName() {
        return refName;
    }

    public String getAfterSha() {
        return afterSha;
    }

    public String getPusherLogin() {
        return pusherLogin;
    }

    public boolean isForced() {
        return forced;
    }

    public int getCommitCount() {
        return commitCount;
    }

    public String getHeadCommitMessage() {
        return headCommitMessage;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
