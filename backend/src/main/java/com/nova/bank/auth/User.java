package com.nova.bank.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Persist identity and serialized login transition state; never serialize this entity. */
@Entity @Table(name="users")
public class User {
 @Id private UUID id;
 @Column(nullable=false,length=50,unique=true) private String username;
 @Column(name="password_hash",nullable=false,length=255) private String passwordHash;
 @Column(name="full_name",nullable=false,length=100) private String fullName;
 @Column(nullable=false,length=30) private String role;
 @Column(name="failed_attempts",nullable=false) private int failedAttempts;
 @Column(nullable=false) private boolean locked;
 @Column(name="last_login") private Instant lastLogin;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 /** Allow Hibernate to hydrate persisted identity. */
 protected User() {}
 /** Create a fixture identity with a BCrypt hash. */
 public User(UUID id,String username,String passwordHash,String fullName,String role) { this.id=id; this.username=username; this.passwordHash=passwordHash; this.fullName=fullName; this.role=role; this.lastLogin=Instant.parse("2026-10-14T08:10:00Z"); this.createdAt=Instant.now(); this.updatedAt=createdAt; }
 /** Identify the authenticated owner. */ public UUID getId(){return id;}
 /** Return the public login identifier. */ public String getUsername(){return username;}
 /** Return the private verification hash inside the service boundary. */ public String getPasswordHash(){return passwordHash;}
 /** Return the display name. */ public String getFullName(){return fullName;}
 /** Return the authoritative permission role. */ public String getRole(){return role;}
 /** Return committed failure count. */ public int getFailedAttempts(){return failedAttempts;}
 /** Report whether reset is required. */ public boolean isLocked(){return locked;}
 /** Return the previous successful login instant. */ public Instant getLastLogin(){return lastLogin;}
 /** Apply an unsuccessful attempt while holding the row lock. */ public void fail(){ failedAttempts=Math.min(3,failedAttempts+1); locked=failedAttempts==3; updatedAt=Instant.now(); }
 /** Reset consecutive failures and stamp the successful login. */ public void succeed(){failedAttempts=0; lastLogin=Instant.now(); updatedAt=lastLogin;}
 /** Compare persisted identities without touching secrets. */ @Override public boolean equals(Object o){return o instanceof User u && id!=null && id.equals(u.id);}
 /** Hash by persisted identity. */ @Override public int hashCode(){return id==null?0:id.hashCode();}
}
