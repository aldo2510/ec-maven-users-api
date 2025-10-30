package com.cloudnative.academy.repository;

import com.cloudnative.academy.domain.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

public class UserRepositoryTest {

    private UserRepository userRepository = new UserRepository();

    @Test
    public void testSaveUser() {
        User user = new User(1L, "John Doe", "john.doe@example.com");
        User savedUser = userRepository.save(user);
        assertNotNull(savedUser);
        assertEquals("John Doe", savedUser.getName());
    }

    @Test
    public void testFindByName() {
        User user = new User(1L, "John Doe", "john.doe@example.com");
        userRepository.save(user);
        Optional<User> foundUser = userRepository.findByName("John Doe");
        assertTrue(foundUser.isPresent());
        assertEquals("John Doe", foundUser.get().getName());
    }

    @Test
    public void testDeleteByName() {
        User user = new User(1L, "John Doe", "john.doe@example.com");
        userRepository.save(user);
        userRepository.deleteByName("John Doe");
        Optional<User> foundUser = userRepository.findByName("John Doe");
        assertFalse(foundUser.isPresent());
    }

    @Test
    public void testFindAllUsers() {
        userRepository.save(new User(1L, "John Doe", "john.doe@example.com"));
        userRepository.save(new User(2L, "Jane Smith", "jane.smith@example.com"));
        List<User> users = userRepository.findAll();
        assertEquals(2, users.size());
    }
}
