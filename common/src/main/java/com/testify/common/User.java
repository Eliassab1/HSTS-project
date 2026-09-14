package com.testify.common;

import java.io.Serializable;

/**
 * Represents a user in the TESTIFY system.
 *
 * A user contains login details, a display name
 * and a role used for determining which screen
 * should be displayed after login.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier of the user.
     */
    private int userId;

    /**
     * Username used for login.
     */
    private String username;

    /**
     * Password used for login.
     */
    private String password;

    /**
     * Full name of the user.
     */
    private String fullName;

    /**
     * Role of the user.
     *
     * Examples:
     * TEACHER
     * STUDENT
     * PRINCIPAL
     */
    private String role;

    /**
     * URL of the user's profile picture. Null when no picture is set.
     */
    private String avatarUrl;

    /**
     * National ID (ת"ז) a student enters to start an exam. Null for staff
     * and for students who haven't set one yet.
     */
    private String nationalId;

    /**
     * Grade level (שכבה) of a student, 9-12.
     *
     * Deliberately an {@code Integer}, not an {@code int}: teachers and
     * principals have no grade level and the {@code users.grade_level}
     * column is NULL for them, which must round-trip as null rather than
     * collapsing to 0.
     */
    private Integer gradeLevel;

    /**
     * Empty constructor.
     */
    public User() {
    }

    /**
     * Creates a user with all details.
     *
     * @param userId unique user identifier
     * @param username login username
     * @param password login password
     * @param fullName full name
     * @param role user role
     */
    public User(
            int userId,
            String username,
            String password,
            String fullName,
            String role
    ) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    /**
     * Returns a readable representation of the user.
     *
     * The password is intentionally not included.
     *
     * @return user details
     */
    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}